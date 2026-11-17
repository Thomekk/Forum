package org.anonymous.forum;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Loads the bundled Yggdrasil transport peer list. These are transport peer
 * URIs, not YggChat contact codes or forum member identifiers.
 */
final class BootstrapConfig {
    static final String ASSET_PATH = "network/bootstrap-nodes.json";
    static final int MIN_SEEDS = 3;
    static final int MAX_SEEDS = 5;

    final List<String> peers;
    final boolean valid;

    private BootstrapConfig(List<String> peers, boolean valid) {
        this.peers = Collections.unmodifiableList(new ArrayList<>(peers));
        this.valid = valid;
    }

    boolean isConfigured() { return valid && !peers.isEmpty(); }
    String peerList() { return join(peers); }

    static BootstrapConfig load(Context context) {
        try (InputStream input = context.getAssets().open(ASSET_PATH);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int count;
            while ((count = input.read(buffer)) != -1) if (count > 0) output.write(buffer, 0, count);
            return parse(new JSONObject(new String(output.toByteArray(), "UTF-8")));
        } catch (Exception ignored) {
            return new BootstrapConfig(Collections.<String>emptyList(), false);
        }
    }

    static BootstrapConfig parse(JSONObject source) {
        if (source == null) return new BootstrapConfig(Collections.<String>emptyList(), false);
        JSONArray values = source.optJSONArray("peers");
        if (values == null || values.length() > MAX_SEEDS) {
            return new BootstrapConfig(Collections.<String>emptyList(), false);
        }
        List<String> peers = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < values.length(); i++) {
            String raw = values.optString(i, "").trim();
            if (raw.isEmpty()) continue;
            String normalized = normalizePeer(raw);
            if (normalized == null) return new BootstrapConfig(Collections.<String>emptyList(), false);
            if (seen.add(normalizeForComparison(normalized))) peers.add(normalized);
        }
        if (!peers.isEmpty() && peers.size() < MIN_SEEDS) {
            return new BootstrapConfig(Collections.<String>emptyList(), false);
        }
        return new BootstrapConfig(peers, true);
    }

    private static String normalizeForComparison(String value) {
        URI uri = URI.create(value);
        StringBuilder key = new StringBuilder()
                .append(uri.getScheme().toLowerCase(Locale.ROOT))
                .append("://")
                .append(uri.getHost().toLowerCase(Locale.ROOT))
                .append(':')
                .append(uri.getPort());
        if (uri.getRawPath() != null) key.append(uri.getRawPath());
        if (uri.getRawQuery() != null) key.append('?').append(uri.getRawQuery());
        return key.toString();
    }

    private static String normalizePeer(String value) {
        if (value.length() > 1024) return null;
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null || !("tls".equalsIgnoreCase(scheme) || "quic".equalsIgnoreCase(scheme))) return null;
            if (uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535) return null;
            if (uri.getUserInfo() != null || uri.getFragment() != null) return null;
            return uri.toString();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String join(List<String> peers) {
        StringBuilder value = new StringBuilder();
        for (String peer : peers) {
            if (value.length() > 0) value.append(',');
            value.append(peer);
        }
        return value.toString();
    }
}
