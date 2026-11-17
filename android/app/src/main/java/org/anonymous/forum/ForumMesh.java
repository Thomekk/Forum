package org.anonymous.forum;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * App-level Yggdrasil discovery, identity proofs, local member cache and public-record placement.
 * A routing-tree entry is only a candidate: it becomes a Forum member after a fresh, signed
 * NodeInfo response for that exact Yggdrasil key and address.
 */
public final class ForumMesh {
    public static final String NODEINFO_KEY = "anonymous-forum";
    public static final String MESH_APP_ID = "anonymous-forum-mesh";
    public static final int PROTOCOL_VERSION = 1;
    public static final long MEMBER_TTL_MS = 6L * 60L * 60L * 1000L;
    public static final long PROBE_INTERVAL_MS = 30L * 60L * 1000L;
    public static final int MAX_PROBES_PER_CYCLE = 12;
    public static final int MAX_REPLICAS = 3;

    private static final String PREFS_NAME = "forum_mesh";
    private static final String STATE_KEY = "mesh_state_v1";
    private static final long REPLICA_RETRY_MS = 30L * 60L * 1000L;
    private static final long ATTEMPT_RETENTION_MS = 48L * 60L * 60L * 1000L;
    private static final long MESSAGE_RETENTION_MS = 14L * 24L * 60L * 60L * 1000L;
    private static final int MAX_MEMBERS = 4096;
    private static final int MAX_TRACKED_PROBES = 16384;
    private static final int MAX_REPLICA_EVENTS = 4096;
    private static final int MAX_SEEN_MESSAGES = 8192;
    private static final int MAX_TREE_ENTRIES = 20000;
    private static final int MAX_MESSAGE_CHARS = 16384;
    private static final int MAX_CONTACT_CODE_CHARS = 4096;
    private static final long FUTURE_CLOCK_SKEW_MS = 5L * 60L * 1000L;
    private static final Object STATE_LOCK = new Object();

    private final SharedPreferences prefs;
    private final ForumIdentity identity;

    public ForumMesh(Context context) {
        Context app = context.getApplicationContext();
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        identity = new ForumIdentity(app);
    }

    public String identityKey() { return identity.publicKeyBase64(); }
    public String localIdentityId() { return identityId(identity.publicKeyBase64()); }

    /** Signed, public-only metadata returned by Yggdrasil NodeInfo. */
    public JSONObject createPresence(String nodeKeyValue, String addressValue, String contactCodeValue) {
        String nodeKey = normalizeNodeKey(nodeKeyValue);
        String address = normalizeAddress(addressValue);
        String contactCode = contactCodeValue == null ? "" : contactCodeValue.trim();
        if (nodeKey.isEmpty() || address.isEmpty() || contactCode.isEmpty()
                || contactCode.length() > MAX_CONTACT_CODE_CHARS
                || !address.equals(addressForNodeKey(nodeKey))) {
            throw new IllegalArgumentException("The local Yggdrasil identity is incomplete");
        }
        try {
            JSONObject presence = new JSONObject();
            put(presence, "app", ForumCore.APP_ID);
            put(presence, "v", PROTOCOL_VERSION);
            put(presence, "identityKey", identity.publicKeyBase64());
            put(presence, "identityId", identityId(identity.publicKeyBase64()));
            put(presence, "nodeKey", nodeKey);
            put(presence, "address", address);
            put(presence, "contactCode", contactCode);
            JSONArray capabilities = new JSONArray();
            capabilities.put("questions-v1");
            capabilities.put("replica-v1");
            put(presence, "capabilities", capabilities);
            signObject(presence, "anonymous-forum-presence-v1");
            return presence;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot sign the Forum presence record", error);
        }
    }

    /** Verifies a NodeInfo record against the key/address used for the Yggdrasil query. */
    public boolean verifyPresence(JSONObject presence, String expectedNodeKey, String expectedAddress) {
        if (presence == null || !ForumCore.APP_ID.equals(presence.optString("app"))
                || presence.optInt("v", 0) != PROTOCOL_VERSION) return false;
        String nodeKey = normalizeNodeKey(presence.optString("nodeKey", ""));
        String address = normalizeAddress(presence.optString("address", ""));
        String expectedKey = normalizeNodeKey(expectedNodeKey);
        String expectedAddr = normalizeAddress(expectedAddress);
        String contactCode = presence.optString("contactCode", "").trim();
        String publicKey = presence.optString("identityKey", "");
        if (nodeKey.isEmpty() || expectedKey.isEmpty() || !nodeKey.equals(expectedKey)
                || address.isEmpty() || expectedAddr.isEmpty() || !sameAddress(address, expectedAddr)
                || !sameAddress(address, addressForNodeKey(nodeKey))
                || contactCode.isEmpty() || contactCode.length() > MAX_CONTACT_CODE_CHARS
                || containsControlCharacter(contactCode)) return false;
        if (!hasCapability(presence.optJSONArray("capabilities"), "replica-v1")) return false;
        if (!identityId(publicKey).equals(presence.optString("identityId", ""))) return false;
        return verifyObject(presence, "signature", "anonymous-forum-presence-v1", publicKey);
    }

    /** Persist a verified participant; only this method can promote a tree candidate into the cache. */
    public boolean acceptPresence(JSONObject presence, String expectedNodeKey, String expectedAddress, long now) {
        if (!verifyPresence(presence, expectedNodeKey, expectedAddress)) return false;
        String key = normalizeNodeKey(expectedNodeKey);
        synchronized (STATE_LOCK) {
            JSONObject state = readState();
            JSONArray members = array(state, "members");
            JSONObject entry = new JSONObject();
            put(entry, "presence", copy(presence));
            put(entry, "lastSeen", now);
            int found = -1;
            for (int i = 0; i < members.length(); i++) {
                JSONObject old = members.optJSONObject(i);
                JSONObject oldPresence = old == null ? null : old.optJSONObject("presence");
                if (oldPresence != null && key.equals(normalizeNodeKey(oldPresence.optString("nodeKey", "")))) {
                    found = i;
                    break;
                }
            }
            if (found >= 0) put(members, found, entry);
            else members.put(entry);
            trimMembers(members);
            writeState(state);
        }
        return true;
    }

    /** Return a copy of recently observed members, expiring old cache entries first. */
    public List<Member> activeMembers(long now) {
        synchronized (STATE_LOCK) {
            JSONObject state = readState();
            JSONArray members = array(state, "members");
            ArrayList<Member> result = new ArrayList<>();
            for (int i = members.length() - 1; i >= 0; i--) {
                JSONObject entry = members.optJSONObject(i);
                JSONObject presence = entry == null ? null : entry.optJSONObject("presence");
                long seen = entry == null ? 0L : entry.optLong("lastSeen", 0L);
                if (presence == null || !withinMemberTtl(seen, now)) {
                    members.remove(i);
                    continue;
                }
                result.add(new Member(presence, seen));
            }
            if (members.length() > MAX_MEMBERS) trimMembers(members);
            writeState(state);
            Collections.sort(result, new Comparator<Member>() {
                @Override public int compare(Member left, Member right) {
                    return left.nodeKey.compareTo(right.nodeKey);
                }
            });
            return Collections.unmodifiableList(result);
        }
    }

    public boolean hasMemberBinding(String nodeKeyValue, String identityIdValue, long now) {
        String nodeKey = normalizeNodeKey(nodeKeyValue);
        if (nodeKey.isEmpty() || identityIdValue == null || identityIdValue.isEmpty()) return false;
        for (Member member : activeMembers(now)) {
            if (nodeKey.equals(member.nodeKey) && identityIdValue.equals(member.identityId)) return true;
        }
        return false;
    }

    /** Refresh lastSeen after a signature-verified direct message from a cached participant. */
    public void noteAuthenticatedPeer(String nodeKeyValue, String identityIdValue, long now) {
        String nodeKey = normalizeNodeKey(nodeKeyValue);
        if (nodeKey.isEmpty() || identityIdValue == null || identityIdValue.isEmpty()) return;
        synchronized (STATE_LOCK) {
            JSONObject state = readState();
            JSONArray members = array(state, "members");
            for (int i = 0; i < members.length(); i++) {
                JSONObject entry = members.optJSONObject(i);
                JSONObject presence = entry == null ? null : entry.optJSONObject("presence");
                if (presence != null && nodeKey.equals(normalizeNodeKey(presence.optString("nodeKey", "")))
                        && identityIdValue.equals(presence.optString("identityId", ""))) {
                    put(entry, "lastSeen", now);
                    writeState(state);
                    return;
                }
            }
        }
    }

    /**
     * Select a bounded, rotating sample of actual routing-tree entries. These are candidates only;
     * the caller must query NodeInfo and validate the signed Forum presence before storing them.
     */
    public List<Candidate> nextProbeCandidates(JSONArray tree, String localNodeKey, long now, int maxCount) {
        if (tree == null || tree.length() == 0 || maxCount <= 0) return Collections.emptyList();
        String local = normalizeNodeKey(localNodeKey);
        TreeMap<String, Candidate> unique = new TreeMap<>();
        int length = Math.min(tree.length(), MAX_TREE_ENTRIES);
        for (int i = 0; i < length; i++) {
            JSONObject entry = tree.optJSONObject(i);
            if (entry == null) continue;
            String key = normalizeNodeKey(entry.optString("key", ""));
            if (key.isEmpty() || key.equals(local)) continue;
            String address = normalizeAddress(entry.optString("address", ""));
            String derived = addressForNodeKey(key);
            if (address.isEmpty()) address = derived;
            if (address.isEmpty() || !sameAddress(address, derived)) continue;
            unique.put(key, new Candidate(key, address));
        }
        ArrayList<Candidate> candidates = new ArrayList<>(unique.values());
        if (candidates.isEmpty()) return Collections.emptyList();

        synchronized (STATE_LOCK) {
            JSONObject state = readState();
            JSONObject probes = object(state, "probes");
            int cursor = positiveModulo(state.optInt("treeCursor", 0), candidates.size());
            ArrayList<Candidate> selected = new ArrayList<>();
            int examined = 0;
            int limit = Math.min(maxCount, MAX_PROBES_PER_CYCLE);
            while (examined < candidates.size() && selected.size() < limit) {
                Candidate candidate = candidates.get((cursor + examined) % candidates.size());
                long last = probes.optLong(candidate.nodeKey, 0L);
                if (last <= 0L || now - Math.min(last, now) >= PROBE_INTERVAL_MS) {
                    selected.add(candidate);
                    put(probes, candidate.nodeKey, now);
                }
                examined++;
            }
            put(state, "treeCursor", (cursor + Math.max(1, examined)) % candidates.size());
            pruneTimestampObject(probes, now - 3L * 24L * 60L * 60L * 1000L, MAX_TRACKED_PROBES);
            writeState(state);
            return Collections.unmodifiableList(selected);
        }
    }

    /** Rendezvous-hash selection: local copies are retained by ForumCore; this returns remote replicas. */
    public List<Member> replicaTargets(String eventId, String localNodeKey, long now, int maxCount) {
        if (eventId == null || eventId.isEmpty() || maxCount <= 0) return Collections.emptyList();
        String local = normalizeNodeKey(localNodeKey);
        ArrayList<Member> eligible = new ArrayList<>();
        for (Member member : activeMembers(now)) if (!member.nodeKey.equals(local)) eligible.add(member);
        Collections.sort(eligible, new Comparator<Member>() {
            @Override public int compare(Member left, Member right) {
                byte[] a = sha256(("anonymous-forum-replica-v1\n" + eventId + "\n" + left.nodeKey).getBytes(StandardCharsets.UTF_8));
                byte[] b = sha256(("anonymous-forum-replica-v1\n" + eventId + "\n" + right.nodeKey).getBytes(StandardCharsets.UTF_8));
                return -compareUnsigned(a, b);
            }
        });
        int limit = Math.min(Math.min(maxCount, MAX_REPLICAS), eligible.size());
        return Collections.unmodifiableList(new ArrayList<>(eligible.subList(0, limit)));
    }

    public boolean shouldAttemptReplica(String eventId, String targetNodeKey, long now) {
        String key = normalizeNodeKey(targetNodeKey);
        if (eventId == null || eventId.isEmpty() || key.isEmpty()) return false;
        synchronized (STATE_LOCK) {
            JSONObject state = readState();
            JSONObject events = object(state, "replicaAttempts");
            JSONObject attempts = events.optJSONObject(eventId);
            long last = attempts == null ? 0L : attempts.optLong(key, 0L);
            return last <= 0L || now - Math.min(last, now) >= REPLICA_RETRY_MS;
        }
    }

    public void markReplicaAttempt(String eventId, String targetNodeKey, long now) {
        String key = normalizeNodeKey(targetNodeKey);
        if (eventId == null || eventId.isEmpty() || key.isEmpty()) return;
        synchronized (STATE_LOCK) {
            JSONObject state = readState();
            JSONObject events = object(state, "replicaAttempts");
            JSONObject attempts = events.optJSONObject(eventId);
            if (attempts == null) { attempts = new JSONObject(); put(events, eventId, attempts); }
            put(attempts, key, now);
            pruneReplicaAttempts(events, now);
            writeState(state);
        }
    }

    /** Sign an original public event once. Private answer bodies must never enter the replica protocol. */
    public JSONObject signOriginEvent(JSONObject event, String nodeKeyValue, String addressValue) throws Exception {
        if (event == null || !isPublicEvent(event)) throw new IllegalArgumentException("Only public Forum records may be replicated");
        String nodeKey = normalizeNodeKey(nodeKeyValue);
        String address = normalizeAddress(addressValue);
        if (nodeKey.isEmpty() || address.isEmpty() || !sameAddress(address, addressForNodeKey(nodeKey))) {
            throw new IllegalArgumentException("Invalid origin Yggdrasil identity");
        }
        JSONObject signed = copy(event);
        if (signed.has("originSignature")) {
            if (verifyOriginEvent(signed) == null) throw new IllegalArgumentException("Invalid existing origin signature");
            return signed;
        }
        put(signed, "originNodeKey", nodeKey);
        put(signed, "originAddress", address);
        put(signed, "originIdentityKey", identity.publicKeyBase64());
        put(signed, "originIdentityId", identityId(identity.publicKeyBase64()));
        byte[] payload = originPayload(signed);
        put(signed, "originSignature", Base64.encodeToString(identity.sign(payload), Base64.NO_WRAP));
        return signed;
    }

    /** Verify an origin signature; the caller separately confirms its Yggdrasil-key binding. */
    public Origin verifyOriginEvent(JSONObject event) {
        if (event == null || !isPublicEvent(event)) return null;
        String nodeKey = normalizeNodeKey(event.optString("originNodeKey", ""));
        String address = normalizeAddress(event.optString("originAddress", ""));
        String publicKey = event.optString("originIdentityKey", "");
        String id = event.optString("originIdentityId", "");
        if (nodeKey.isEmpty() || address.isEmpty() || !sameAddress(address, addressForNodeKey(nodeKey))
                || publicKey.isEmpty() || !id.equals(identityId(publicKey))) return null;
        String signature = event.optString("originSignature", "");
        try {
            byte[] sig = Base64.decode(signature, Base64.DEFAULT);
            if (sig.length == 0 || sig.length > 1024 || !ForumIdentity.verify(publicKey, originPayload(event), sig)) return null;
        } catch (Exception ignored) {
            return null;
        }
        return new Origin(nodeKey, address, publicKey, id);
    }

    /** Outer signature binds a direct YggChat sender and target to this signed public record. */
    public JSONObject createRecordEnvelope(JSONObject event, String senderNodeKeyValue,
                                           String senderAddressValue, String targetNodeKeyValue) throws Exception {
        String senderNodeKey = normalizeNodeKey(senderNodeKeyValue);
        String senderAddress = normalizeAddress(senderAddressValue);
        String targetNodeKey = normalizeNodeKey(targetNodeKeyValue);
        if (senderNodeKey.isEmpty() || senderAddress.isEmpty() || targetNodeKey.isEmpty()
                || !sameAddress(senderAddress, addressForNodeKey(senderNodeKey)) || !isPublicEvent(event)
                || verifyOriginEvent(event) == null) {
            throw new IllegalArgumentException("Invalid Forum replica envelope");
        }
        JSONObject envelope = new JSONObject();
        put(envelope, "app", MESH_APP_ID);
        put(envelope, "v", PROTOCOL_VERSION);
        put(envelope, "type", "record");
        put(envelope, "senderNodeKey", senderNodeKey);
        put(envelope, "senderAddress", senderAddress);
        put(envelope, "senderIdentityKey", identity.publicKeyBase64());
        put(envelope, "senderIdentityId", identityId(identity.publicKeyBase64()));
        put(envelope, "targetNodeKey", targetNodeKey);
        put(envelope, "event", copy(event));
        signObject(envelope, "anonymous-forum-envelope-v1");
        if (envelope.toString().length() > MAX_MESSAGE_CHARS) throw new IllegalArgumentException("Forum record is too large");
        return envelope;
    }

    public JSONObject createHelloEnvelope(JSONObject presence, String targetNodeKeyValue) throws Exception {
        String targetNodeKey = normalizeNodeKey(targetNodeKeyValue);
        String localNodeKey = normalizeNodeKey(presence == null ? "" : presence.optString("nodeKey", ""));
        String localAddress = normalizeAddress(presence == null ? "" : presence.optString("address", ""));
        if (targetNodeKey.isEmpty() || localNodeKey.isEmpty() || localAddress.isEmpty()
                || !verifyPresence(presence, localNodeKey, localAddress)) {
            throw new IllegalArgumentException("Invalid Forum presence handshake");
        }
        JSONObject envelope = new JSONObject();
        put(envelope, "app", MESH_APP_ID);
        put(envelope, "v", PROTOCOL_VERSION);
        put(envelope, "type", "hello");
        put(envelope, "senderNodeKey", localNodeKey);
        put(envelope, "senderAddress", localAddress);
        put(envelope, "senderIdentityKey", presence.optString("identityKey"));
        put(envelope, "senderIdentityId", presence.optString("identityId"));
        put(envelope, "targetNodeKey", targetNodeKey);
        put(envelope, "presence", copy(presence));
        signObject(envelope, "anonymous-forum-envelope-v1");
        return envelope;
    }

    /** Verify a message source against its YggChat thread peer; transport peers alone never pass. */
    public VerifiedEnvelope verifyEnvelope(JSONObject envelope, String localNodeKeyValue, String fromPeer) {
        if (envelope == null || envelope.toString().length() > MAX_MESSAGE_CHARS
                || !MESH_APP_ID.equals(envelope.optString("app"))
                || envelope.optInt("v", 0) != PROTOCOL_VERSION) return null;
        String type = envelope.optString("type", "");
        if (!"record".equals(type) && !"hello".equals(type)) return null;
        String senderNodeKey = normalizeNodeKey(envelope.optString("senderNodeKey", ""));
        String senderAddress = normalizeAddress(envelope.optString("senderAddress", ""));
        String senderIdentityKey = envelope.optString("senderIdentityKey", "");
        String senderIdentityId = envelope.optString("senderIdentityId", "");
        if (senderNodeKey.isEmpty() || senderAddress.isEmpty()
                || !sameAddress(senderAddress, addressForNodeKey(senderNodeKey))
                || !sameAddress(senderAddress, normalizeAddress(fromPeer))
                || !senderIdentityId.equals(identityId(senderIdentityKey))
                || !verifyObject(envelope, "signature", "anonymous-forum-envelope-v1", senderIdentityKey)) return null;
        String target = normalizeNodeKey(envelope.optString("targetNodeKey", ""));
        String local = normalizeNodeKey(localNodeKeyValue);
        if (!target.isEmpty() && !target.equals(local)) return null;

        JSONObject event = null;
        JSONObject presence = null;
        if ("record".equals(type)) {
            event = envelope.optJSONObject("event");
            if (!isPublicEvent(event) || verifyOriginEvent(event) == null) return null;
        } else {
            presence = envelope.optJSONObject("presence");
            if (!verifyPresence(presence, senderNodeKey, senderAddress)
                    || !senderIdentityId.equals(presence.optString("identityId", ""))) return null;
        }
        return new VerifiedEnvelope(type, senderNodeKey, senderAddress, senderIdentityKey,
                senderIdentityId, target, event, presence);
    }

    public boolean shouldSendHello(String nodeKeyValue, long now) {
        String nodeKey = normalizeNodeKey(nodeKeyValue);
        if (nodeKey.isEmpty()) return false;
        synchronized (STATE_LOCK) {
            JSONObject state = readState();
            JSONObject sent = object(state, "helloSent");
            long last = sent.optLong(nodeKey, 0L);
            return last <= 0L || now - Math.min(last, now) >= 12L * 60L * 60L * 1000L;
        }
    }

    public void markHelloSent(String nodeKeyValue, long now) {
        String nodeKey = normalizeNodeKey(nodeKeyValue);
        if (nodeKey.isEmpty()) return;
        synchronized (STATE_LOCK) {
            JSONObject state = readState();
            JSONObject sent = object(state, "helloSent");
            put(sent, nodeKey, now);
            pruneTimestampObject(sent, now - 3L * MEMBER_TTL_MS, MAX_MEMBERS);
            writeState(state);
        }
    }

    /** Message-ID deduplication for incremental polling of YggChat thread histories. */
    public boolean isMessageSeen(String peer, String messageId) {
        String id = messageKey(peer, messageId);
        if (id.isEmpty()) return false;
        synchronized (STATE_LOCK) { return object(readState(), "seenMessages").has(id); }
    }

    public boolean markMessageSeen(String peer, String messageId, long now) {
        String id = messageKey(peer, messageId);
        if (id.isEmpty()) return false;
        synchronized (STATE_LOCK) {
            JSONObject state = readState();
            JSONObject seen = object(state, "seenMessages");
            if (seen.has(id)) return false;
            put(seen, id, now);
            pruneTimestampObject(seen, now - MESSAGE_RETENTION_MS, MAX_SEEN_MESSAGES);
            writeState(state);
            return true;
        }
    }

    private static String messageKey(String peer, String messageId) {
        if (peer == null || peer.isEmpty() || messageId == null || messageId.isEmpty()) return "";
        return sha256Hex((peer + "\n" + messageId).getBytes(StandardCharsets.UTF_8));
    }

    /** Fairly rotate through chat threads so a large local cache does not monopolize one sync cycle. */
    public List<String> nextThreadPeers(JSONArray threads, int maxCount) {
        if (threads == null || threads.length() == 0 || maxCount <= 0) return Collections.emptyList();
        LinkedHashMap<String, Boolean> unique = new LinkedHashMap<>();
        for (int i = 0; i < threads.length(); i++) {
            JSONObject thread = threads.optJSONObject(i);
            String peer = thread == null ? "" : thread.optString("peer", "").trim();
            if (!peer.isEmpty()) unique.put(peer, Boolean.TRUE);
        }
        ArrayList<String> peers = new ArrayList<>(unique.keySet());
        if (peers.isEmpty()) return Collections.emptyList();
        synchronized (STATE_LOCK) {
            JSONObject state = readState();
            int cursor = positiveModulo(state.optInt("threadCursor", 0), peers.size());
            int count = Math.min(maxCount, peers.size());
            ArrayList<String> selected = new ArrayList<>(count);
            for (int i = 0; i < count; i++) selected.add(peers.get((cursor + i) % peers.size()));
            put(state, "threadCursor", (cursor + count) % peers.size());
            writeState(state);
            return Collections.unmodifiableList(selected);
        }
    }

    public static boolean isPublicEvent(JSONObject event) {
        if (event == null || !ForumCore.APP_ID.equals(event.optString("app")) || event.optInt("v", 0) != 1) return false;
        String type = event.optString("type", "");
        if (!"question".equals(type) && !"answer_notice".equals(type)) return false;
        String id = event.optString("id", "");
        return !id.isEmpty() && id.length() <= 160 && event.toString().length() <= MAX_MESSAGE_CHARS;
    }

    public static String normalizeNodeKey(String value) {
        if (value == null) return "";
        String text = value.trim();
        if (text.startsWith("ed25519:")) text = text.substring("ed25519:".length());
        byte[] bytes = null;
        if (text.matches("(?i)[0-9a-f]{64}")) bytes = decodeHex(text);
        else {
            try { bytes = Base64.decode(text, Base64.DEFAULT); } catch (Exception ignored) { }
        }
        return bytes == null || bytes.length != 32 ? "" : encodeHex(bytes);
    }

    public static String normalizeAddress(String value) {
        if (value == null) return "";
        String text = value.trim();
        if (text.startsWith("[") && text.endsWith("]")) text = text.substring(1, text.length() - 1);
        if (text.indexOf(':') < 0 || text.indexOf('%') >= 0) return "";
        try {
            byte[] bytes = InetAddress.getByName(text).getAddress();
            if (bytes.length != 16 || bytes[0] != 0x02) return "";
            return InetAddress.getByAddress(bytes).getHostAddress().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static boolean sameAddress(String left, String right) {
        String a = normalizeAddress(left);
        String b = normalizeAddress(right);
        if (a.isEmpty() || b.isEmpty()) return false;
        try {
            return Arrays.equals(InetAddress.getByName(a).getAddress(), InetAddress.getByName(b).getAddress());
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Yggdrasil v0.5 address derivation from a 32-byte Ed25519 node public key. */
    public static String addressForNodeKey(String nodeKeyValue) {
        String nodeKey = normalizeNodeKey(nodeKeyValue);
        if (nodeKey.isEmpty()) return "";
        byte[] publicKey = decodeHex(nodeKey);
        byte[] address = new byte[16];
        address[0] = 0x02;
        int leadingOnes = 0;
        boolean started = false;
        int destinationBit = 0;
        for (int i = 0; i < publicKey.length * 8 && destinationBit < 112; i++) {
            int bit = ((~publicKey[i / 8]) & 0xff) >>> (7 - (i % 8)) & 1;
            if (!started) {
                if (bit == 1) leadingOnes++;
                else started = true;
                continue; // The first zero bit is omitted by the Yggdrasil address scheme.
            }
            if (bit != 0) address[2 + destinationBit / 8] |= (byte) (0x80 >>> (destinationBit % 8));
            destinationBit++;
        }
        if (leadingOnes > 255) return "";
        address[1] = (byte) leadingOnes;
        try { return InetAddress.getByAddress(address).getHostAddress().toLowerCase(Locale.ROOT); }
        catch (Exception ignored) { return ""; }
    }

    private static byte[] originPayload(JSONObject event) {
        String nodeKey = normalizeNodeKey(event.optString("originNodeKey", ""));
        String address = normalizeAddress(event.optString("originAddress", ""));
        String id = event.optString("originIdentityId", "");
        JSONObject body = without(event, "originNodeKey", "originAddress", "originIdentityKey",
                "originIdentityId", "originSignature");
        String value = "anonymous-forum-origin-v1\n" + nodeKey + "\n" + address + "\n" + id + "\n" + canonical(body);
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private void signObject(JSONObject value, String domain) throws Exception {
        JSONObject body = without(value, "signature");
        byte[] signature = identity.sign((domain + "\n" + canonical(body)).getBytes(StandardCharsets.UTF_8));
        put(value, "signature", Base64.encodeToString(signature, Base64.NO_WRAP));
    }

    private static boolean verifyObject(JSONObject value, String signatureField, String domain, String publicKey) {
        if (value == null || publicKey == null || publicKey.isEmpty()) return false;
        try {
            byte[] signature = Base64.decode(value.optString(signatureField, ""), Base64.DEFAULT);
            if (signature.length == 0 || signature.length > 1024) return false;
            JSONObject body = without(value, signatureField);
            byte[] bytes = (domain + "\n" + canonical(body)).getBytes(StandardCharsets.UTF_8);
            return ForumIdentity.verify(publicKey, bytes, signature);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String identityId(String publicKeyBase64) {
        if (publicKeyBase64 == null || publicKeyBase64.isEmpty()) return "";
        try { return sha256Hex(Base64.decode(publicKeyBase64, Base64.DEFAULT)); }
        catch (Exception ignored) { return ""; }
    }

    private static JSONObject readStateFrom(SharedPreferences prefs) {
        try { return new JSONObject(prefs.getString(STATE_KEY, "{}")); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private JSONObject readState() { return readStateFrom(prefs); }

    private void writeState(JSONObject state) {
        prefs.edit().putString(STATE_KEY, state.toString()).apply();
    }

    private static JSONObject object(JSONObject parent, String key) {
        JSONObject value = parent.optJSONObject(key);
        if (value == null) { value = new JSONObject(); put(parent, key, value); }
        return value;
    }

    private static JSONArray array(JSONObject parent, String key) {
        JSONArray value = parent.optJSONArray(key);
        if (value == null) { value = new JSONArray(); put(parent, key, value); }
        return value;
    }

    private static boolean withinMemberTtl(long lastSeen, long now) {
        if (lastSeen <= 0L || lastSeen > now + FUTURE_CLOCK_SKEW_MS) return false;
        return now - Math.min(lastSeen, now) <= MEMBER_TTL_MS;
    }

    private static boolean hasCapability(JSONArray values, String expected) {
        if (values == null) return false;
        for (int i = 0; i < values.length(); i++) if (expected.equals(values.optString(i))) return true;
        return false;
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return true;
        return false;
    }

    private static void trimMembers(JSONArray members) {
        while (members.length() > MAX_MEMBERS) members.remove(0);
    }

    private static void pruneTimestampObject(JSONObject object, long minTimestamp, int maxEntries) {
        ArrayList<TimestampEntry> entries = new ArrayList<>();
        ArrayList<String> expired = new ArrayList<>();
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            long timestamp = object.optLong(key, 0L);
            if (timestamp < minTimestamp) expired.add(key);
            else entries.add(new TimestampEntry(key, timestamp));
        }
        for (String key : expired) object.remove(key);
        if (entries.size() <= maxEntries) return;
        Collections.sort(entries, new Comparator<TimestampEntry>() {
            @Override public int compare(TimestampEntry left, TimestampEntry right) {
                return Long.compare(left.timestamp, right.timestamp);
            }
        });
        for (int i = 0; i < entries.size() - maxEntries; i++) object.remove(entries.get(i).key);
    }

    private static void pruneReplicaAttempts(JSONObject events, long now) {
        ArrayList<TimestampEntry> entries = new ArrayList<>();
        ArrayList<String> emptyEvents = new ArrayList<>();
        ArrayList<String> eventKeys = new ArrayList<>();
        Iterator<String> keys = events.keys();
        while (keys.hasNext()) eventKeys.add(keys.next());
        for (String eventId : eventKeys) {
            JSONObject targets = events.optJSONObject(eventId);
            if (targets == null) { emptyEvents.add(eventId); continue; }
            long newest = 0L;
            ArrayList<String> targetKeys = new ArrayList<>();
            Iterator<String> targetIterator = targets.keys();
            while (targetIterator.hasNext()) targetKeys.add(targetIterator.next());
            for (String target : targetKeys) {
                long timestamp = targets.optLong(target, 0L);
                if (timestamp <= 0L || now - Math.min(timestamp, now) > ATTEMPT_RETENTION_MS) targets.remove(target);
                else newest = Math.max(newest, timestamp);
            }
            if (targets.length() == 0 || newest == 0L) emptyEvents.add(eventId);
            else entries.add(new TimestampEntry(eventId, newest));
        }
        for (String eventId : emptyEvents) events.remove(eventId);
        if (entries.size() <= MAX_REPLICA_EVENTS) return;
        Collections.sort(entries, new Comparator<TimestampEntry>() {
            @Override public int compare(TimestampEntry left, TimestampEntry right) {
                return Long.compare(left.timestamp, right.timestamp);
            }
        });
        for (int i = 0; i < entries.size() - MAX_REPLICA_EVENTS; i++) events.remove(entries.get(i).key);
    }

    private static JSONObject without(JSONObject source, String... excluded) {
        JSONObject result = new JSONObject();
        if (source == null) return result;
        ArrayList<String> keys = new ArrayList<>();
        Iterator<String> iterator = source.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            boolean skip = false;
            for (String name : excluded) if (name.equals(key)) { skip = true; break; }
            if (!skip) keys.add(key);
        }
        Collections.sort(keys);
        for (String key : keys) put(result, key, source.opt(key));
        return result;
    }

    private static String canonical(Object value) {
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            ArrayList<String> keys = new ArrayList<>();
            Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) keys.add(iterator.next());
            Collections.sort(keys);
            StringBuilder out = new StringBuilder("{");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) out.append(',');
                String key = keys.get(i);
                out.append(JSONObject.quote(key)).append(':').append(canonical(object.opt(key)));
            }
            return out.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) out.append(',');
                out.append(canonical(array.opt(i)));
            }
            return out.append(']').toString();
        }
        if (value instanceof String) return JSONObject.quote((String) value);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        return JSONObject.quote(String.valueOf(value));
    }

    private static int positiveModulo(int value, int divisor) {
        int result = value % divisor;
        return result < 0 ? result + divisor : result;
    }

    private static int compareUnsigned(byte[] left, byte[] right) {
        int length = Math.min(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int a = left[i] & 0xff;
            int b = right[i] & 0xff;
            if (a != b) return Integer.compare(a, b);
        }
        return Integer.compare(left.length, right.length);
    }

    private static byte[] sha256(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (Exception error) { throw new IllegalStateException("SHA-256 unavailable", error); }
    }

    private static String sha256Hex(byte[] value) { return encodeHex(sha256(value)); }

    private static String encodeHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return out.toString();
    }

    private static byte[] decodeHex(String text) {
        if (text == null || (text.length() & 1) != 0) return null;
        byte[] result = new byte[text.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(text.charAt(2 * i), 16);
            int low = Character.digit(text.charAt(2 * i + 1), 16);
            if (high < 0 || low < 0) return null;
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }

    private static JSONObject copy(JSONObject source) {
        try { return source == null ? new JSONObject() : new JSONObject(source.toString()); }
        catch (JSONException error) { throw new IllegalStateException(error); }
    }

    private static void put(JSONObject target, String key, Object value) {
        try { target.put(key, value); }
        catch (JSONException error) { throw new IllegalStateException(error); }
    }

    private static void put(JSONArray target, int index, Object value) {
        try { target.put(index, value); }
        catch (JSONException error) { throw new IllegalStateException(error); }
    }

    public static final class Candidate {
        public final String nodeKey;
        public final String address;
        Candidate(String nodeKey, String address) { this.nodeKey = nodeKey; this.address = address; }
    }

    public static final class Member {
        public final String nodeKey;
        public final String address;
        public final String identityKey;
        public final String identityId;
        public final String contactCode;
        public final long lastSeen;
        public final JSONObject presence;
        Member(JSONObject presence, long lastSeen) {
            this.presence = copy(presence);
            nodeKey = normalizeNodeKey(presence.optString("nodeKey", ""));
            address = normalizeAddress(presence.optString("address", ""));
            identityKey = presence.optString("identityKey", "");
            identityId = presence.optString("identityId", "");
            contactCode = presence.optString("contactCode", "");
            this.lastSeen = lastSeen;
        }
    }

    public static final class Origin {
        public final String nodeKey;
        public final String address;
        public final String identityKey;
        public final String identityId;
        Origin(String nodeKey, String address, String identityKey, String identityId) {
            this.nodeKey = nodeKey; this.address = address; this.identityKey = identityKey; this.identityId = identityId;
        }
    }

    public static final class VerifiedEnvelope {
        public final String type;
        public final String senderNodeKey;
        public final String senderAddress;
        public final String senderIdentityKey;
        public final String senderIdentityId;
        public final String targetNodeKey;
        public final JSONObject event;
        public final JSONObject presence;
        VerifiedEnvelope(String type, String senderNodeKey, String senderAddress, String senderIdentityKey,
                         String senderIdentityId, String targetNodeKey, JSONObject event, JSONObject presence) {
            this.type = type; this.senderNodeKey = senderNodeKey; this.senderAddress = senderAddress;
            this.senderIdentityKey = senderIdentityKey; this.senderIdentityId = senderIdentityId;
            this.targetNodeKey = targetNodeKey; this.event = event; this.presence = presence;
        }
    }

    private static final class TimestampEntry {
        final String key;
        final long timestamp;
        TimestampEntry(String key, long timestamp) { this.key = key; this.timestamp = timestamp; }
    }
}
