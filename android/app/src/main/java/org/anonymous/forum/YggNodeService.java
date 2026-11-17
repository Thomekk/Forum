package org.anonymous.forum;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import net.yggchat.yggchat.Yggchat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Owns the Yggdrasil transport, app-level discovery and best-effort local replication. */
public final class YggNodeService extends Service {
    public static final String ACTION_STATUS = "org.anonymous.forum.NETWORK_STATUS";
    private static final String ACTION_START = "org.anonymous.forum.START_NODE";
    private static final String ACTION_SYNC = "org.anonymous.forum.SYNC_NODE";
    private static final String ACTION_PURGE = "org.anonymous.forum.PURGE_SHARDS";
    private static final String CHANNEL_ID = "forum-network";
    private static final int NOTIFICATION_ID = 417;
    private static final String TAG = "ForumNetwork";
    private static final long POLL_SECONDS = 60L;
    private static final long TREE_REFRESH_MS = 5L * 60L * 1000L;
    private static final long INITIAL_RETRY_SECONDS = 5L;
    private static final long MAX_RETRY_SECONDS = 15L * 60L;
    private static final int MAX_THREADS_PER_CYCLE = 64;
    private static final int MAX_MESSAGES_PER_THREAD = 80;

    private ForumCore forum;
    private ForumMesh mesh;
    private Yggchat.Session node;
    private YggdrasilAdminClient admin;
    private ScheduledExecutorService worker;
    private ExecutorService discoveryPool;
    private volatile String status = "status_starting";
    private volatile String myCode = "";
    private volatile String nodeKey = "";
    private volatile String nodeAddress = "";
    private volatile String configJson = "{}";
    private volatile JSONObject localPresence;
    private volatile boolean presenceConfigured;
    private JSONArray cachedTree;
    private long nextTreeRefreshAt;
    private long retryDelaySeconds = INITIAL_RETRY_SECONDS;
    private boolean cycleScheduled;

    public static void start(Context context) { dispatch(context, ACTION_START); }
    public static void sync(Context context) { dispatch(context, ACTION_SYNC); }
    public static void purgeShards(Context context) { dispatch(context, ACTION_PURGE); }

    private static void dispatch(Context context, String action) {
        Intent intent = new Intent(context, YggNodeService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    @Override public void onCreate() {
        super.onCreate();
        forum = new ForumCore(getApplicationContext());
        mesh = new ForumMesh(getApplicationContext());
        File yggDir = new File(getFilesDir(), "yggdrasil");
        if (!yggDir.isDirectory() && !yggDir.mkdirs()) Log.w(TAG, "Could not create the private Yggdrasil data directory yet");
        // The admin endpoint is an unauthenticated Yggdrasil API; keep it inside app-private files.
        admin = new YggdrasilAdminClient(new File(yggDir, "forum-admin.sock").getAbsolutePath());
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "forum-ygg-worker");
            thread.setDaemon(true);
            return thread;
        });
        discoveryPool = Executors.newFixedThreadPool(4, r -> {
            Thread thread = new Thread(r, "forum-discovery-query");
            thread.setDaemon(true);
            return thread;
        });
        createNotificationChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, notification(statusText()));
        if (intent == null || ACTION_START.equals(intent.getAction()) || ACTION_SYNC.equals(intent.getAction())) {
            ensurePolling();
        } else if (ACTION_PURGE.equals(intent.getAction())) {
            worker.execute(this::purgeInternal);
            ensurePolling();
        }
        broadcastStatus();
        return START_STICKY;
    }

    private synchronized void ensurePolling() { scheduleCycle(0L); }

    private synchronized void scheduleCycle(long delaySeconds) {
        if (worker == null || worker.isShutdown() || cycleScheduled) return;
        cycleScheduled = true;
        worker.schedule(this::runCycle, delaySeconds, TimeUnit.SECONDS);
    }

    private void runCycle() {
        boolean active = false;
        boolean waitingForConfig = false;
        try {
            if (node != null && !node.isStarted()) {
                try { node.stop(); } catch (Throwable ignored) { }
                node = null;
                presenceConfigured = false;
            }
            if (node == null) {
                active = startNodeInternal();
                waitingForConfig = "status_bootstrap_missing".equals(status)
                        || "status_bootstrap_invalid".equals(status);
            }
            if (node != null && node.isStarted()) {
                pollAndSend();
                active = node != null && node.isStarted();
            } else {
                broadcastStatus();
            }
        } catch (Throwable error) {
            Log.w(TAG, "Yggdrasil sync cycle failed", error);
            if (node != null) {
                try { node.stop(); } catch (Throwable ignored) { }
                node = null;
                presenceConfigured = false;
            }
            status = "status_temp_unavailable";
            broadcastStatus();
        } finally {
            synchronized (this) {
                cycleScheduled = false;
                if (active) {
                    retryDelaySeconds = INITIAL_RETRY_SECONDS;
                    scheduleCycle(POLL_SECONDS);
                } else if (waitingForConfig) {
                    scheduleCycle(POLL_SECONDS);
                } else {
                    long delay = retryDelaySeconds;
                    retryDelaySeconds = Math.min(MAX_RETRY_SECONDS, retryDelaySeconds * 2L);
                    scheduleCycle(delay);
                }
            }
        }
    }

    private boolean startNodeInternal() {
        if (node != null) return node.isStarted();
        BootstrapConfig bootstrap = BootstrapConfig.load(getApplicationContext());
        if (!bootstrap.valid) {
            status = "status_bootstrap_invalid";
            broadcastStatus();
            return false;
        }
        if (!bootstrap.isConfigured()) {
            status = "status_bootstrap_missing";
            broadcastStatus();
            return false;
        }
        status = "status_starting";
        broadcastStatus();
        try {
            node = Yggchat.Session.open(getApplicationContext(), bootstrap.peerList(), adminSocketPath());
            if (!node.isStarted()) {
                status = "status_temp_unavailable";
                broadcastStatus();
                return false;
            }
            configureLocalPresence();
            status = presenceConfigured ? "status_discovery_ready" : "status_presence_unavailable";
            broadcastStatus();
            return node.isStarted();
        } catch (Throwable error) {
            if (node != null) {
                try { node.stop(); } catch (Throwable ignored) { }
            }
            node = null;
            presenceConfigured = false;
            status = error instanceof UnsatisfiedLinkError ? "status_no_core" : "status_start_failed";
            Log.e(TAG, "Cannot start bundled YggChat core", error);
            broadcastStatus();
            return false;
        }
    }

    private String adminSocketPath() {
        return new File(new File(getFilesDir(), "yggdrasil"), "forum-admin.sock").getAbsolutePath();
    }

    private void configureLocalPresence() {
        myCode = safe(node.myCode());
        nodeKey = ForumMesh.normalizeNodeKey(node.publicKey());
        nodeAddress = ForumMesh.normalizeAddress(node.address());
        presenceConfigured = false;
        if (myCode.isEmpty() || nodeKey.isEmpty() || nodeAddress.isEmpty()) {
            forum.setMyCode(myCode);
            Log.w(TAG, "YggChat did not provide a public identity code; Forum discovery stays disabled");
            return;
        }
        forum.setMyCode(myCode);
        try {
            localPresence = mesh.createPresence(nodeKey, nodeAddress, myCode);
            node.configureForumNodeInfo(localPresence);
            String afterKey = ForumMesh.normalizeNodeKey(node.publicKey());
            String afterAddress = ForumMesh.normalizeAddress(node.address());
            String afterCode = safe(node.myCode());
            if (!nodeKey.equals(afterKey) || !ForumMesh.sameAddress(nodeAddress, afterAddress)
                    || !myCode.equals(afterCode)) {
                nodeKey = afterKey;
                nodeAddress = afterAddress;
                myCode = afterCode;
                forum.setMyCode(myCode);
                localPresence = mesh.createPresence(nodeKey, nodeAddress, myCode);
                node.configureForumNodeInfo(localPresence);
            }
            presenceConfigured = node.isStarted();
        } catch (Throwable error) {
            localPresence = null;
            presenceConfigured = false;
            Log.w(TAG, "Could not advertise signed Forum presence through Yggdrasil NodeInfo", error);
        }
    }

    private void pollAndSend() {
        if (node == null || !node.isStarted()) return;
        long now = System.currentTimeMillis();
        boolean discoveryReady = scanYggdrasilTree(now);
        receiveMessages(now);
        sendPendingEvents(now);
        try { configJson = memoryConfig(safe(node.configJson())); } catch (Throwable ignored) { }
        if (!presenceConfigured) status = "status_presence_unavailable";
        else status = discoveryReady ? "status_discovery_ready" : "status_discovery_unavailable";
        broadcastStatus();
    }

    /** Routing-tree entries are candidates only; app membership requires a signed NodeInfo proof. */
    private boolean scanYggdrasilTree(long now) {
        if (admin == null || !admin.isConfigured() || nodeKey.isEmpty() || !presenceConfigured) return false;
        if (cachedTree == null || now >= nextTreeRefreshAt) {
            try {
                cachedTree = admin.getTree();
                nextTreeRefreshAt = now + TREE_REFRESH_MS;
            } catch (IOException error) {
                cachedTree = null;
                nextTreeRefreshAt = now + 60L * 1000L;
                Log.w(TAG, "Yggdrasil routing-tree discovery is unavailable", error);
                return false;
            }
        }
        List<ForumMesh.Candidate> candidates = mesh.nextProbeCandidates(cachedTree, nodeKey, now,
                ForumMesh.MAX_PROBES_PER_CYCLE);
        if (candidates.isEmpty()) return true;

        ArrayList<Future<DiscoveryResult>> requests = new ArrayList<>();
        for (ForumMesh.Candidate candidate : candidates) {
            requests.add(discoveryPool.submit(() -> new DiscoveryResult(candidate, admin.getNodeInfo(candidate.nodeKey))));
        }
        for (Future<DiscoveryResult> request : requests) {
            try {
                DiscoveryResult result = request.get(9, TimeUnit.SECONDS);
                JSONObject presence = result.nodeInfo.optJSONObject(ForumMesh.NODEINFO_KEY);
                if (presence != null && mesh.acceptPresence(presence, result.candidate.nodeKey,
                        result.candidate.address, System.currentTimeMillis())) {
                    Log.d(TAG, "Validated Forum NodeInfo for Yggdrasil key …" + tail(result.candidate.nodeKey));
                }
            } catch (Exception error) {
                request.cancel(true);
                Log.d(TAG, "A routing-tree candidate did not answer with a valid Forum NodeInfo");
            }
        }
        return true;
    }

    private void receiveMessages(long now) {
        if (node == null || !node.isStarted()) return;
        JSONObject state = parseObject(safe(node.statusJson()));
        JSONArray threads = state.optJSONArray("threads");
        for (String peer : mesh.nextThreadPeers(threads, MAX_THREADS_PER_CYCLE)) {
            try {
                JSONObject thread = parseObject(safe(node.threadJson(peer)));
                JSONArray messages = thread.optJSONArray("msgs");
                if (messages == null) continue;
                int first = Math.max(0, messages.length() - MAX_MESSAGES_PER_THREAD);
                for (int i = first; i < messages.length(); i++) {
                    JSONObject message = messages.optJSONObject(i);
                    if (message == null || message.optBoolean("out", false)) continue;
                    String id = message.optString("id", "");
                    if (id.isEmpty() || mesh.isMessageSeen(peer, id)) continue;
                    String text = message.optString("text", "");
                    if (text.isEmpty() || text.length() > 16384) {
                        mesh.markMessageSeen(peer, id, now);
                        continue;
                    }
                    boolean retryLater = processIncoming(peer, text, now);
                    if (!retryLater) mesh.markMessageSeen(peer, id, System.currentTimeMillis());
                }
            } catch (Throwable error) {
                Log.d(TAG, "Could not read a YggChat thread during Forum sync");
            }
        }
    }

    /** Returns true only when a transient NodeInfo query failed and this message should be retried. */
    private boolean processIncoming(String fromPeer, String text, long now) {
        JSONObject payload;
        try { payload = new JSONObject(text); }
        catch (JSONException ignored) { return false; }

        if (ForumMesh.MESH_APP_ID.equals(payload.optString("app"))) {
            ForumMesh.VerifiedEnvelope verified = mesh.verifyEnvelope(payload, nodeKey, fromPeer);
            if (verified == null) return false;
            if ("hello".equals(verified.type)) {
                mesh.acceptPresence(verified.presence, verified.senderNodeKey, verified.senderAddress, now);
                mesh.noteAuthenticatedPeer(verified.senderNodeKey, verified.senderIdentityId, now);
                return false;
            }
            ForumMesh.Origin origin = mesh.verifyOriginEvent(verified.event);
            if (origin == null) return false;
            Boolean bound = confirmOriginBinding(origin, verified, fromPeer, now);
            if (bound == null) return true;
            if (!bound) return false;
            mesh.noteAuthenticatedPeer(verified.senderNodeKey, verified.senderIdentityId, now);
            forum.receiveEvent(verified.event, fromPeer);
            return false;
        }

        // Private answer bodies remain direct YggChat messages and are never gossiped.
        if (ForumCore.APP_ID.equals(payload.optString("app"))
                && "private_answer".equals(payload.optString("type"))) {
            forum.receiveEvent(payload, fromPeer);
        }
        // Unsigned public question/notice messages are deliberately not accepted.
        return false;
    }

    /** Check the origin-to-Yggdrasil-key binding before accepting a relayed record. */
    private Boolean confirmOriginBinding(ForumMesh.Origin origin, ForumMesh.VerifiedEnvelope sender,
                                         String fromPeer, long now) {
        if (origin.nodeKey.equals(nodeKey) && origin.identityId.equals(mesh.localIdentityId())) return true;
        if (origin.nodeKey.equals(sender.senderNodeKey) && origin.identityId.equals(sender.senderIdentityId)
                && ForumMesh.sameAddress(origin.address, fromPeer)) return true;
        if (mesh.hasMemberBinding(origin.nodeKey, origin.identityId, now)) return true;
        try {
            JSONObject remoteInfo = admin.getNodeInfo(origin.nodeKey);
            JSONObject presence = remoteInfo.optJSONObject(ForumMesh.NODEINFO_KEY);
            if (presence == null || !mesh.acceptPresence(presence, origin.nodeKey, origin.address, now)) return false;
            return origin.identityId.equals(presence.optString("identityId", ""));
        } catch (IOException error) {
            Log.d(TAG, "Could not confirm an offline record origin yet");
            return null;
        }
    }

    private void sendPendingEvents(long now) {
        if (node == null || !node.isStarted()) return;
        for (ForumCore.PendingEvent pending : forum.pendingEvents()) {
            if (pending.id().isEmpty()) continue;
            if ("direct".equals(pending.route)) {
                sendDirect(pending);
            } else if ("public".equals(pending.route) && presenceConfigured && ForumMesh.isPublicEvent(pending.event)) {
                sendReplicas(pending, now);
            }
        }
    }

    private void sendDirect(ForumCore.PendingEvent pending) {
        String target = pending.target == null ? "" : pending.target.trim();
        if (target.isEmpty()) return;
        try {
            String peerId = safe(node.addContactCode(target, Strings.text(this, "anonymous_asker")));
            if (peerId.isEmpty()) return;
            node.send(peerId, pending.event.toString());
            forum.markSent(pending.id());
        } catch (Throwable error) {
            Log.w(TAG, "Private reply remains queued", error);
        }
    }

    private void sendReplicas(ForumCore.PendingEvent pending, long now) {
        JSONObject originSigned;
        try { originSigned = mesh.signOriginEvent(pending.event, nodeKey, nodeAddress); }
        catch (Exception error) {
            Log.w(TAG, "Could not sign public Forum record", error);
            return;
        }
        List<ForumMesh.Member> targets = mesh.replicaTargets(pending.id(), nodeKey, now, ForumMesh.MAX_REPLICAS);
        for (ForumMesh.Member target : targets) {
            if (target.nodeKey.equals(nodeKey) || ForumMesh.sameAddress(target.address, pending.excludePeer)
                    || !mesh.shouldAttemptReplica(pending.id(), target.nodeKey, now)) continue;
            try {
                String peerId = safe(node.addContactCode(target.contactCode, Strings.text(this, "forum_peer")));
                if (peerId.isEmpty()) continue;
                String resolved = ForumMesh.normalizeAddress(peerId);
                if (!resolved.isEmpty() && !ForumMesh.sameAddress(resolved, target.address)) {
                    Log.w(TAG, "YggChat contact code resolved to a different address; refusing replica send");
                    continue;
                }
                if (localPresence != null && mesh.shouldSendHello(target.nodeKey, now)) {
                    JSONObject hello = mesh.createHelloEnvelope(localPresence, target.nodeKey);
                    node.send(peerId, hello.toString());
                    mesh.markHelloSent(target.nodeKey, System.currentTimeMillis());
                }
                JSONObject envelope = mesh.createRecordEnvelope(originSigned, nodeKey, nodeAddress, target.nodeKey);
                node.send(peerId, envelope.toString());
                mesh.markReplicaAttempt(pending.id(), target.nodeKey, System.currentTimeMillis());
            } catch (Throwable error) {
                Log.w(TAG, "Public record remains queued for a later replica attempt", error);
            }
        }
    }

    private void purgeInternal() {
        if (node == null) startNodeInternal();
        if (node != null) {
            try {
                node.purgeVault();
                status = "status_cache_purged";
            } catch (Throwable error) {
                status = "status_purge_failed";
                Log.w(TAG, "Vault purge failed", error);
            }
        }
        broadcastStatus();
    }

    private String statusText() { return Strings.text(this, status); }

    private void broadcastStatus() {
        Intent intent = new Intent(ACTION_STATUS).setPackage(getPackageName());
        boolean online = false;
        try { online = node != null && node.isStarted(); } catch (Throwable ignored) { }
        intent.putExtra("online", online);
        intent.putExtra("status", statusText());
        intent.putExtra("configJson", configJson);
        intent.putExtra("coreVersion", Yggchat.CoreVersion);
        sendBroadcast(intent);
        updateForegroundNotification();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, Strings.text(this, "channel_title"), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(Strings.text(this, "channel_description"));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setContentTitle(Strings.text(this, "notification_title"))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentIntent(content)
                .setOngoing(true);
        return builder.build();
    }

    private void updateForegroundNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, notification(statusText()));
    }

    private String memoryConfig(String value) {
        JSONObject config = parseObject(value);
        JSONObject peers = config.optJSONObject("Peers");
        if (peers != null) {
            peers.remove("Perspectives");
            try { config.put("Peers", peers); } catch (JSONException ignored) { }
        }
        return config.toString();
    }

    private static JSONObject parseObject(String value) {
        try { return new JSONObject(value); } catch (JSONException ignored) { return new JSONObject(); }
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static String tail(String key) { return key.length() <= 8 ? key : key.substring(key.length() - 8); }

    @Override public void onDestroy() {
        if (worker != null) worker.shutdownNow();
        if (discoveryPool != null) discoveryPool.shutdownNow();
        if (node != null) {
            try { node.stop(); } catch (Throwable error) { Log.w(TAG, "Node stop failed", error); }
            node = null;
        }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private static final class DiscoveryResult {
        final ForumMesh.Candidate candidate;
        final JSONObject nodeInfo;
        DiscoveryResult(ForumMesh.Candidate candidate, JSONObject nodeInfo) {
            this.candidate = candidate; this.nodeInfo = nodeInfo;
        }
    }
}
