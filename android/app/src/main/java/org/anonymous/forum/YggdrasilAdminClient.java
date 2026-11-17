package org.anonymous.forum;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Small client for Yggdrasil's local JSON admin protocol over an app-private Unix socket. */
final class YggdrasilAdminClient {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final int TREE_TIMEOUT_MS = 4000;
    private static final int NODEINFO_TIMEOUT_MS = 8500;

    private final String socketPath;

    YggdrasilAdminClient(String socketPath) { this.socketPath = socketPath == null ? "" : socketPath; }

    boolean isConfigured() { return !socketPath.isEmpty(); }

    JSONArray getTree() throws IOException {
        JSONObject reply = request("getTree", new JSONObject(), TREE_TIMEOUT_MS);
        JSONArray tree = reply.optJSONArray("tree");
        if (tree == null) throw new IOException("Yggdrasil admin returned no routing tree");
        return tree;
    }

    JSONObject getNodeInfo(String nodeKey) throws IOException {
        JSONObject arguments = new JSONObject();
        try { arguments.put("key", nodeKey); }
        catch (JSONException error) { throw new IOException("Could not build NodeInfo request", error); }
        JSONObject reply = request("getNodeInfo", arguments, NODEINFO_TIMEOUT_MS);
        JSONObject info = reply.optJSONObject(nodeKey);
        if (info == null) throw new IOException("Yggdrasil returned no NodeInfo for the requested key");
        return info;
    }

    private JSONObject request(String name, JSONObject arguments, int timeoutMs) throws IOException {
        if (socketPath.isEmpty()) throw new IOException("Yggdrasil admin socket is not configured");
        JSONObject request = new JSONObject();
        try {
            request.put("request", name);
            request.put("arguments", arguments == null ? new JSONObject() : arguments);
        } catch (JSONException error) {
            throw new IOException("Could not build Yggdrasil admin request", error);
        }

        LocalSocket socket = new LocalSocket();
        try {
            socket.connect(new LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM));
            socket.setSoTimeout(timeoutMs);
            OutputStream output = socket.getOutputStream();
            output.write((request.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            output.flush();

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            InputStream input = socket.getInputStream();
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) throw new IOException("Yggdrasil admin response is too large");
                bytes.write(buffer, 0, count);
            }
            JSONObject envelope;
            try { envelope = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8)); }
            catch (JSONException error) { throw new IOException("Invalid Yggdrasil admin response", error); }
            if (!"success".equals(envelope.optString("status"))) {
                String detail = envelope.optString("error", "request failed");
                throw new IOException("Yggdrasil admin " + name + ": " + detail);
            }
            JSONObject response = envelope.optJSONObject("response");
            if (response == null) throw new IOException("Yggdrasil admin returned no response body");
            return response;
        } finally {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }
}
