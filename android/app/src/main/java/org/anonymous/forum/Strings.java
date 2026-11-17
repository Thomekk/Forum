package org.anonymous.forum;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads sectioned language JSON from assets/lang, following the web project's lang/ layout. */
public final class Strings {
    public static final String AUTO = "auto";
    private static final String PREFS = "forum_settings";
    private static final Object CACHE_LOCK = new Object();
    private static final Map<String, JSONObject> DICTIONARIES = new HashMap<>();
    private static final Map<String, String> KEY_SECTIONS = createKeySections();
    private static final List<Topic> TOPICS = createTopics();

    private static Context appContext;
    private static List<Language> languageCache;

    private Strings() { }

    /** Keep only the application context; the dictionaries are loaded lazily on first use. */
    public static void initialize(Context context) {
        if (context == null) return;
        synchronized (CACHE_LOCK) {
            if (appContext == null) appContext = context.getApplicationContext();
        }
    }

    public static String effectiveLanguage(Context context) {
        initialize(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String chosen = prefs.getString("language", AUTO);
        if (AUTO.equals(chosen)) {
            String device = Locale.getDefault().getLanguage().toLowerCase(Locale.ROOT);
            return isSupported(device) ? device : "en";
        }
        return isSupported(chosen) ? chosen : "en";
    }

    public static String languagePreference(Context context) {
        initialize(context);
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("language", AUTO);
    }

    public static void setLanguagePreference(Context context, String code) {
        initialize(context);
        if (AUTO.equals(code) || isSupported(code)) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("language", code).apply();
        }
    }

    public static String text(Context context, String key) {
        return text(effectiveLanguage(context), key);
    }

    /** Translation fallback order: selected language, English in the same section, then the key. */
    public static String text(String lang, String key) {
        if (key == null) return "";
        String code = isSupported(lang) ? lang : "en";
        String section = sectionFor(key);
        String value = lookup(section, code, key);
        if (value != null) return value;
        if (!"en".equals(code)) {
            value = lookup(section, "en", key);
            if (value != null) return value;
        }
        return key;
    }

    private static String lookup(String section, String lang, String key) {
        JSONObject dictionary = dictionary(section, lang);
        if (!dictionary.has(key)) return null;
        Object value = dictionary.opt(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static JSONObject dictionary(String section, String lang) {
        String cacheKey = section + "/" + lang;
        synchronized (CACHE_LOCK) {
            JSONObject cached = DICTIONARIES.get(cacheKey);
            if (cached != null) return cached;
            JSONObject loaded = readJsonAsset("lang/" + section + "/" + lang + ".json");
            DICTIONARIES.put(cacheKey, loaded);
            return loaded;
        }
    }

    private static JSONObject readJsonAsset(String path) {
        Context context = appContext;
        if (context == null) return new JSONObject();
        try (InputStream input = context.getAssets().open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > 0) output.write(buffer, 0, count);
            }
            return new JSONObject(new String(output.toByteArray(), "UTF-8"));
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static String sectionFor(String key) {
        String section = KEY_SECTIONS.get(key);
        if (section != null) return section;
        if (key.startsWith("topic.") || key.startsWith("subtopic.")) return "topics";
        if (key.startsWith("q_")) return "q";
        if (key.startsWith("a_")) return "a";
        return "_common";
    }

    private static Map<String, String> createKeySections() {
        Map<String, String> sections = new LinkedHashMap<>();
        addKeys(sections, "_common", "app", "ask", "answer", "settings", "question", "menu", "close",
                "theme_system", "theme_dark", "theme_light", "unanswered_ttl", "anonymous");
        addKeys(sections, "q", "q_welcome", "q_description", "q_placeholder", "send", "question_posted", "write_first");
        addKeys(sections, "l", "search", "empty_title", "empty_feed", "no_matches", "answers", "views", "answer_count_one");
        addKeys(sections, "a", "a_placeholder", "answer_posted", "no_reply_route", "answer_private", "waiting",
                "your_question", "your_answer", "expired", "back", "earn_credit", "write_answer_first");
        addKeys(sections, "settings", "credits", "free_today", "free_left", "free_used", "credit_rule", "no_credits",
                "language", "language_auto", "appearance", "privacy", "credits_local", "credit_badge_free", "credit_badge");
        addKeys(sections, "network", "network", "online", "connecting", "offline", "network_hint",
                "status_starting", "status_no_core", "status_start_failed", "status_temp_unavailable",
                "status_bootstrap_missing", "status_bootstrap_invalid", "status_discovery_ready",
                "status_discovery_unavailable", "status_presence_unavailable", "status_node_running",
                "status_cache_purged", "status_purge_failed", "notification_title",
                "channel_title", "channel_description", "forum_peer", "anonymous_asker");
        addKeys(sections, "memory", "memory", "memory_hint", "shard_size", "redundancy", "fanout", "vault", "ttl", "purge", "purge_confirm");
        addKeys(sections, "topics", "all");
        return Collections.unmodifiableMap(sections);
    }

    private static void addKeys(Map<String, String> map, String section, String... keys) {
        for (String key : keys) map.put(key, section);
    }

    public static boolean isSupported(String code) {
        if (code == null || AUTO.equals(code)) return false;
        for (Language language : languages()) if (language.code.equals(code)) return true;
        return false;
    }

    public static List<Language> languages() {
        synchronized (CACHE_LOCK) {
            if (languageCache != null) return languageCache;
            ArrayList<Language> result = new ArrayList<>();
            result.add(new Language(AUTO, ""));
            JSONObject catalog = readJsonAsset("lang/languages.json");
            JSONArray entries = catalog.optJSONArray("languages");
            if (entries != null) {
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.optJSONObject(i);
                    if (entry == null) continue;
                    String code = entry.optString("code", "");
                    String name = entry.optString("name", code);
                    if (!code.isEmpty() && !AUTO.equals(code)) result.add(new Language(code, name));
                }
            }
            if (result.size() == 1) {
                result.add(new Language("en", "English"));
                result.add(new Language("ru", "Русский"));
            }
            languageCache = Collections.unmodifiableList(result);
            return languageCache;
        }
    }

    public static List<Topic> topics() { return TOPICS; }

    public static Topic topic(String id) {
        for (Topic topic : TOPICS) if (topic.id.equals(id)) return topic;
        return TOPICS.get(0);
    }

    public static String topicName(String lang, String id) { return topic(id).name(lang); }

    public static String subtopicName(String lang, String topicId, String subId) {
        for (Subtopic sub : topic(topicId).subtopics) if (sub.id.equals(subId)) return sub.name(lang);
        return topicName(lang, topicId);
    }

    public static boolean validTopic(String id) {
        for (Topic topic : TOPICS) if (topic.id.equals(id)) return true;
        return false;
    }

    private static List<Topic> createTopics() {
        ArrayList<Topic> topics = new ArrayList<>();
        topics.add(new Topic("all", "•", Collections.<Subtopic>emptyList()));
        topics.add(new Topic("programming", ">_", Arrays.asList(
                new Subtopic("programming", "bugs", "🐞"),
                new Subtopic("programming", "review", "👀"),
                new Subtopic("programming", "architecture", "🏗️"),
                new Subtopic("programming", "ithumor", ":-)"),
                new Subtopic("programming", "itinside", "O_o"))));
        topics.add(new Topic("psychology", "🧠", Arrays.asList(
                new Subtopic("psychology", "self-acceptance", "🌱"),
                new Subtopic("psychology", "triggers", "⚡"),
                new Subtopic("psychology", "relationships", "💞"))));
        topics.add(new Topic("text", "📝", Arrays.asList(
                new Subtopic("text", "editing", "✏️"),
                new Subtopic("text", "translation", "🌐"),
                new Subtopic("text", "copywriting", "📣"))));
        topics.add(new Topic("misc", "💬", Collections.<Subtopic>emptyList()));
        return Collections.unmodifiableList(topics);
    }

    public static final class Language {
        public final String code;
        public final String name;
        Language(String code, String name) { this.code = code; this.name = name; }
        @Override public String toString() { return name; }
    }

    public static final class Topic {
        public final String id;
        public final String icon;
        public final List<Subtopic> subtopics;
        Topic(String id, String icon, List<Subtopic> subtopics) {
            this.id = id; this.icon = icon;
            this.subtopics = Collections.unmodifiableList(new ArrayList<>(subtopics));
        }
        public String name(String lang) { return Strings.text(lang, "topic." + id); }
    }

    public static final class Subtopic {
        public final String parentId;
        public final String id;
        public final String icon;
        Subtopic(String parentId, String id, String icon) {
            this.parentId = parentId; this.id = id; this.icon = icon;
        }
        public String name(String lang) { return Strings.text(lang, "subtopic." + parentId + "." + id); }
    }
}
