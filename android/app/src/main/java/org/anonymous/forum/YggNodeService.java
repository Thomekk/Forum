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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Owns the optional YggChat node and gossips public forum events between saved contacts. */
public final class YggNodeService extends Service {
    public static final String ACTION_STATUS = "org.anonymous.forum.NETWORK_STATUS";
    private static final String ACTION_START = "org.anonymous.forum.START_NODE";
    private static final String ACTION_SYNC = "org.anonymous.forum.SYNC_NODE";
    private static final String ACTION_ADD_PEER = "org.anonymous.forum.ADD_PEER";
    private static final String ACTION_PURGE = "org.anonymous.forum.PURGE_SHARDS";
    private static final String ACTION_STOP = "org.anonymous.forum.STOP_NODE";
    private static final String EXTRA_CODE = "contact_code";
    private static final String CHANNEL_ID = "forum-network";
    private static final int NOTIFICATION_ID = 417;
    private static final String TAG = "ForumNetwork";
    private static final long POLL_SECONDS = 18L;

    private ForumCore forum;
    private Yggchat.Session node;
    private ScheduledExecutorService worker;
    private final Map<String, String> peers = new LinkedHashMap<>(); // code -> YggChat peer id
    private volatile String status = "status_starting";
    private volatile String myCode = "";
    private volatile String configJson = "{}";
    private volatile boolean starting;

    public static void start(Context context) { dispatch(context, ACTION_START, null); }
    public static void sync(Context context) { dispatch(context, ACTION_SYNC, null); }
    public static void addPeer(Context context, String code) { dispatch(context, ACTION_ADD_PEER, code); }
    public static void purgeShards(Context context) { dispatch(context, ACTION_PURGE, null); }
    public static void stopNode(Context context) { context.stopService(new Intent(context, YggNodeService.class)); }

    private static void dispatch(Context context, String action, String code) {
        Intent intent = new Intent(context, YggNodeService.class).setAction(action);
        if (code != null) intent.putExtra(EXTRA_CODE, code);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    @Override public void onCreate() {
        super.onCreate();
        forum = new ForumCore(getApplicationContext());
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "forum-ygg-worker");
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
        } else if (ACTION_ADD_PEER.equals(intent.getAction())) {
            String code = intent.getStringExtra(EXTRA_CODE);
            worker.execute(() -> addPeerInternal(code));
            ensurePolling();
        } else if (ACTION_PURGE.equals(intent.getAction())) {
            worker.execute(this::purgeInternal);
            ensurePolling();
        } else if (ACTION_STOP.equals(intent.getAction())) {
            stopSelf(startId);
        }
        broadcastStatus();
        return START_STICKY;
    }

    private synchronized void ensurePolling() {
        if (worker == null || worker.isShutdown()) return;
        if (starting) return;
        starting = true;
        worker.scheduleWithFixedDelay(() -> {
            try {
                if (node == null) startNodeInternal();
                if (node != null && node.isStarted()) pollAndSend();
            } catch (Throwable error) {
                Log.w(TAG, "Yggdrasil sync cycle failed", error);
                status = "status_temp_unavailable";
                broadcastStatus();
            }
        }, 0, POLL_SECONDS, TimeUnit.SECONDS);
    }

    private void startNodeInternal() {
        if (node != null) return;
        status = "status_starting";
        broadcastStatus();
        try {
            node = Yggchat.Session.open(getApplicationContext());
            myCode = safe(node.myCode());
            configJson = memoryConfig(safe(node.configJson()));
            forum.setMyCode(myCode);
            restorePeers();
            status = "status_node_running";
            broadcastStatus();
        } catch (Throwable error) {
            node = null;
            status = error instanceof UnsatisfiedLinkError
                    ? "status_no_core"
                    : "status_start_failed";
            Log.e(TAG, "Cannot start bundled YggChat core", error);
            broadcastStatus();
        }
    }

    private void restorePeers() {
        for (String code : forum.peerCodes()) {
            if (peers.containsKey(code)) continue;
            try {
                String peer = safe(node.addContactCode(code, Strings.text(this, "forum_peer")));
                if (!peer.isEmpty()) peers.put(code, peer);
            } catch (Throwable error) {
                Log.w(TAG, "Saved contact code rejected", error);
            }
        }
    }

    private void addPeerInternal(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (code.isEmpty()) return;
        try {
            if (node == null) startNodeInternal();
            if (node == null) {
                // Keep the user-entered code; the periodic retry can attach it after the core starts.
                forum.addPeerCode(code);
                status = "status_contact_waiting";
                broadcastStatus();
                return;
            }
            String peer = safe(node.addContactCode(code, Strings.text(this, "forum_peer")));
            if (peer.isEmpty()) throw new IllegalArgumentException("YggChat returned an empty peer id");
            forum.addPeerCode(code);
            peers.put(code, peer);
            status = "status_peer_added";
            broadcastStatus();
            pollAndSend();
        } catch (ForumCore.RuleException error) {
            status = "status_peer_invalid";
            broadcastStatus();
        } catch (Throwable error) {
            Log.w(TAG, "Could not add YggChat contact", error);
            status = "status_peer_add_failed";
            broadcastStatus();
        }
    }

    private void pollAndSend() {
        if (node == null || !node.isStarted()) return;
        restorePeers();
        boolean changed = false;
        for (Map.Entry<String, String> peer : new ArrayList<>(peers.entrySet())) {
            String peerId = peer.getValue();
            try {
                JSONObject thread = new JSONObject(node.threadJson(peerId));
                JSONArray messages = thread.optJSONArray("msgs");
                if (messages == null) continue;
                for (int i = 0; i < messages.length(); i++) {
                    JSONObject message = messages.optJSONObject(i);
                    if (message == null || message.optBoolean("out", false)) continue;
                    String text = message.optString("text", "").trim();
                    if (!text.startsWith("{")) continue;
                    JSONObject event;
                    try { event = new JSONObject(text); }
                    catch (JSONException ignored) { continue; }
                    if (forum.receiveEvent(event, peerId)) changed = true;
                }
            } catch (Throwable error) {
                Log.w(TAG, "Could not poll a YggChat thread", error);
            }
        }

        for (ForumCore.PendingEvent pending : forum.pendingEvents()) {
            if (pending.id().isEmpty()) continue;
            try {
                boolean sent = false;
                if ("direct".equals(pending.route)) {
                    String target = pending.target.trim();
                    if (!target.isEmpty()) {
                        String peerId = safe(node.addContactCode(target, Strings.text(this, "anonymous_asker")));
                        if (!peerId.isEmpty()) {
                            node.send(peerId, pending.event.toString());
                            sent = true;
                        }
                    }
                } else {
                    for (String peerId : new ArrayList<>(peers.values())) {
                        if (peerId.isEmpty() || peerId.equals(pending.excludePeer)) continue;
                        try {
                            node.send(peerId, pending.event.toString());
                            sent = true;
                        } catch (Throwable error) {
                            Log.w(TAG, "Could not send event to a peer", error);
                        }
                    }
                }
                if (sent) {
                    forum.markSent(pending.id());
                    changed = true;
                }
            } catch (Throwable error) {
                Log.w(TAG, "Outbox item remains queued", error);
            }
        }

        if (changed) forum.expireNow();
        status = peers.isEmpty() ? "status_node_add_peer" : "status_node_running";
        try { configJson = memoryConfig(safe(node.configJson())); } catch (Throwable ignored) { }
        broadcastStatus();
    }

    private void purgeInternal() {
        if (node == null) { startNodeInternal(); }
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
        intent.putExtra("myCode", myCode);
        intent.putExtra("peerCount", peers.size());
        intent.putExtra("pendingCount", forum == null ? 0 : forum.pendingCount());
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
        Intent stop = new Intent(this, YggNodeService.class).setAction(ACTION_STOP);
        PendingIntent stopAction = PendingIntent.getService(this, 2, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setContentTitle(Strings.text(this, "notification_title"))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentIntent(content)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, Strings.text(this, "notification_stop"), stopAction);
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

    private JSONObject parseObject(String value) {
        try { return new JSONObject(value); } catch (JSONException ignored) { return new JSONObject(); }
    }

    private static String safe(String value) { return value == null ? "" : value; }

    @Override public void onDestroy() {
        if (worker != null) worker.shutdownNow();
        if (node != null) {
            try { node.stop(); } catch (Throwable error) { Log.w(TAG, "Node stop failed", error); }
            node = null;
        }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
