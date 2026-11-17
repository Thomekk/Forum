// Native JNI signatures mirror the generated bindings embedded in the supplied
// YggChat 0.2.0 APK. Keep package, names, fields and signatures stable.
package net.yggchat.yggchat;

import android.content.Context;
import go.Seq;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public final class Yggchat {
    public static final String CoreVersion = "v0.5.15";
    public static final String Version = "0.2.0";

    static {
        Seq.touch();
        _init();
    }

    private Yggchat() { }
    private static native void _init();

    public static native String codeFromPrivateKey(String privateKey);
    public static native Exception getErrBadCode();
    public static native Exception getErrBadConfig();
    public static native Exception getErrBadPeer();
    public static native Exception getErrEmptyText();
    public static native Exception getErrNeedKey();
    public static native Exception getErrNotRunning();
    public static native Node newNode(String storagePath);
    public static native void setErrBadCode(Exception error);
    public static native void setErrBadConfig(Exception error);
    public static native void setErrBadPeer(Exception error);
    public static native void setErrEmptyText(Exception error);
    public static native void setErrNeedKey(Exception error);
    public static native void setErrNotRunning(Exception error);
    public static void touch() { }

    /** Small public facade; the generated Node class remains package-private. */
    public static final class Session {
        private final Node node;
        private final String adminSocketPath;

        private Session(Node node, String adminSocketPath) {
            this.node = node;
            this.adminSocketPath = adminSocketPath == null ? "" : adminSocketPath;
        }

        /** Start using the operator-configured Yggdrasil transport peers. */
        public static Session open(Context context, String peerUris) {
            return open(context, peerUris, "");
        }

        /** Start with the admin API restricted to a Unix socket inside this app's private files. */
        public static Session open(Context context, String peerUris, String adminSocketPath) {
            Context app = context.getApplicationContext();
            Seq.setContext(app);
            String path = new File(app.getFilesDir(), "yggdrasil").getAbsolutePath();
            Node node = Yggchat.newNode(path);
            node.setPeers(peerUris == null ? "" : peerUris);
            String config = node.configJSON();
            if (adminSocketPath != null && !adminSocketPath.isEmpty()) {
                config = configuredJson(config, adminSocketPath, null);
            }
            node.start(config);
            return new Session(node, adminSocketPath);
        }

        /** Advertise a signed Forum presence record through Yggdrasil NodeInfo. */
        public synchronized void configureForumNodeInfo(JSONObject presence) {
            if (adminSocketPath.isEmpty()) throw new IllegalStateException("Yggdrasil admin socket is not configured");
            String config = configuredJson(node.configJSON(), adminSocketPath, presence);
            node.restart(config);
        }

        private static String configuredJson(String raw, String adminSocketPath, JSONObject presence) {
            try {
                JSONObject config = new JSONObject(raw == null || raw.isEmpty() ? "{}" : raw);
                config.put("AdminListen", "unix://" + adminSocketPath);
                config.put("NodeInfoPrivacy", true);
                JSONObject info = config.optJSONObject("NodeInfo");
                if (info == null) info = new JSONObject();
                if (presence == null) info.remove("anonymous-forum");
                else info.put("anonymous-forum", presence);
                config.put("NodeInfo", info);
                return config.toString();
            } catch (JSONException error) {
                throw new IllegalStateException("Could not configure the Yggdrasil admin socket and NodeInfo", error);
            }
        }

        public String addContactCode(String code, String nickname) {
            return node.addContactCode(code, nickname);
        }
        public String address() { return node.address(); }
        public String configJson() { return node.configJSON(); }
        public String myCode() { return node.myCode(); }
        public String nick() { return node.nick(); }
        public String peersJson() { return node.peers(); }
        public String publicKey() { return node.publicKey(); }
        public String send(String peer, String text) { return node.send(peer, text); }
        public String statusJson() { return node.statusJSON(); }
        public String subnet() { return node.subnet(); }
        public String threadJson(String peer) { return node.threadJSON(peer); }
        public boolean isStarted() { return node.started(); }
        public void purgeVault() { node.purgeVault(); }
        public void stop() { node.stop(); }
    }
}

final class Config implements Seq.GoObject {
    private final int refnum;
    public Config() { refnum = __New(); Seq.trackGoRef(refnum, this); }
    Config(int refnum) { this.refnum = refnum; Seq.trackGoRef(refnum, this); }
    private static native int __New();
    @Override public final int incRefnum() { Seq.incGoRef(refnum, this); return refnum; }
    @Override public boolean equals(Object other) { return other instanceof Config && refnum == ((Config) other).refnum; }
    @Override public int hashCode() { return refnum; }
    @Override public String toString() { return "Config"; }

    public final native String getNick();
    public final native void setNick(String value);
    public final native String getGroupPassword();
    public final native void setGroupPassword(String value);
    public final native String getNodeName();
    public final native void setNodeName(String value);
    public final native boolean getPrivateNode();
    public final native void setPrivateNode(boolean value);
    public final native boolean getRelay();
    public final native void setRelay(boolean value);
    public final native long getFanout();
    public final native void setFanout(long value);
    public final native long getRedundancy();
    public final native void setRedundancy(long value);
    public final native long getShardSize();
    public final native void setShardSize(long value);
    public final native long getTTLHours();
    public final native void setTTLHours(long value);
    public final native long getVaultMax();
    public final native void setVaultMax(long value);
}

final class Message implements Seq.GoObject {
    private final int refnum;
    public Message() { refnum = __New(); Seq.trackGoRef(refnum, this); }
    Message(int refnum) { this.refnum = refnum; Seq.trackGoRef(refnum, this); }
    private static native int __New();
    @Override public final int incRefnum() { Seq.incGoRef(refnum, this); return refnum; }
    @Override public boolean equals(Object other) { return other instanceof Message && refnum == ((Message) other).refnum; }
    @Override public int hashCode() { return refnum; }
    @Override public String toString() { return "Message"; }

    public final native String getID();
    public final native void setID(String value);
    public final native boolean getOut();
    public final native void setOut(boolean value);
    public final native String getText();
    public final native void setText(String value);
    public final native long getTS();
    public final native void setTS(long value);
    public final native String getStatus();
    public final native void setStatus(String value);
}

final class Node implements Seq.GoObject {
    private final int refnum;
    Node(int refnum) { this.refnum = refnum; Seq.trackGoRef(refnum, this); }
    public Node(String storagePath) {
        refnum = __NewNode(storagePath);
        Seq.trackGoRef(refnum, this);
    }
    private static native int __NewNode(String storagePath);
    @Override public final int incRefnum() { Seq.incGoRef(refnum, this); return refnum; }
    @Override public boolean equals(Object other) { return other instanceof Node && refnum == ((Node) other).refnum; }
    @Override public int hashCode() { return refnum; }
    @Override public String toString() { return "YggdrasilNode"; }

    public native String addContactCode(String code, String nickname);
    public native String address();
    public native String configJSON();
    public native void deleteThread(String peer);
    public native void listen(String address);
    public native String listenerAddr();
    public native void markRead(String peer);
    public native String myCode();
    public native String nick();
    public native String peers();
    public native String publicKey();
    public native void purgeVault();
    public native void restart(String configJson);
    public native String send(String peer, String text);
    public native void setConfig(String configJson);
    public native void setNick(String nickname);
    public native void setPeers(String peers);
    public native void start(String configJson);
    public native boolean started();
    public native String statusJSON();
    public native void stop();
    public native String subnet();
    public native String threadJSON(String peer);
}

final class Thread implements Seq.GoObject {
    private final int refnum;
    public Thread() { refnum = __New(); Seq.trackGoRef(refnum, this); }
    Thread(int refnum) { this.refnum = refnum; Seq.trackGoRef(refnum, this); }
    private static native int __New();
    @Override public final int incRefnum() { Seq.incGoRef(refnum, this); return refnum; }
    @Override public boolean equals(Object other) { return other instanceof Thread && refnum == ((Thread) other).refnum; }
    @Override public int hashCode() { return refnum; }
    @Override public String toString() { return "Thread"; }

    public final native String getPeer();
    public final native void setPeer(String value);
    public final native String getNick();
    public final native void setNick(String value);
    public final native String getKey();
    public final native void setKey(String value);
    public final native long getUnread();
    public final native void setUnread(long value);
}
