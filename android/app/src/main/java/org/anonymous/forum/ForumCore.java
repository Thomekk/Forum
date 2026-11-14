package org.anonymous.forum;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Forum domain and small-device store. It is independent of the network adapter:
 * Q&A, quota and credits keep working locally if the optional native node is absent.
 */
public final class ForumCore {
    public static final String APP_ID = "anonymous-forum";
    public static final int QUESTION_LIMIT = 2000;
    public static final int ANSWER_LIMIT = 4000;
    public static final long UNANSWERED_TTL_MS = 24L * 60L * 60L * 1000L;
    private static final long ANSWERED_RETENTION_MS = 30L * UNANSWERED_TTL_MS;
    private static final String PREFS = "forum_data";
    private static final String STATE_KEY = "state_v1";
    private static final int MAX_QUESTIONS = 1000;
    private static final int MAX_OUTBOX = 256;
    private static final int MAX_SEEN = 3000;
    private static final int MAX_PEERS = 64;
    private static final Object LOCK = new Object();

    private final SharedPreferences prefs;

    public ForumCore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String localId() {
        synchronized (LOCK) {
            JSONObject root = read();
            ensureIdentity(root);
            write(root);
            return root.optString("userId");
        }
    }

    public String displayName() { return anonymousName(localId()); }

    public Wallet wallet() {
        synchronized (LOCK) {
            JSONObject root = read();
            ensureIdentity(root);
            resetDailyQuota(root);
            write(root);
            return new Wallet(root.optInt("credits", 0), root.optBoolean("freeUsed", false) ? 0 : 1,
                    root.optString("freeDate"));
        }
    }

    public List<Question> questions() {
        synchronized (LOCK) {
            JSONObject root = read();
            ensureIdentity(root);
            expire(root, System.currentTimeMillis());
            write(root);
            JSONArray array = root.optJSONArray("questions");
            ArrayList<Question> result = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject value = array.optJSONObject(i);
                    if (value != null) result.add(new Question(value));
                }
            }
            Collections.sort(result, new Comparator<Question>() {
                @Override public int compare(Question left, Question right) {
                    if (left.createdAt == right.createdAt) return 0;
                    return left.createdAt < right.createdAt ? 1 : -1;
                }
            });
            return Collections.unmodifiableList(result);
        }
    }

    public Question question(String id) {
        if (id == null) return null;
        synchronized (LOCK) {
            JSONObject root = read();
            expire(root, System.currentTimeMillis());
            JSONArray array = root.optJSONArray("questions");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject value = array.optJSONObject(i);
                    if (value != null && id.equals(value.optString("id"))) {
                        write(root);
                        return new Question(value);
                    }
                }
            }
            write(root);
            return null;
        }
    }

    public void recordView(String id) {
        if (id == null || id.isEmpty()) return;
        synchronized (LOCK) {
            JSONObject root = read();
            JSONObject question = findQuestion(root, id);
            if (question != null) put(question, "views", question.optInt("views", 0) + 1);
            write(root);
        }
    }

    public boolean hasAnswered(String questionId) {
        if (questionId == null) return false;
        synchronized (LOCK) { return contains(array(read(), "answeredQuestionIds"), questionId); }
    }

    public Question postQuestion(String rawText, String topicId, String subtopicId) throws RuleException {
        String text = clean(rawText, QUESTION_LIMIT);
        if (text.isEmpty()) throw new RuleException("Write a question first.");
        if (!Strings.validTopic(topicId)) topicId = "all";
        String sub = validSubtopic(topicId, subtopicId) ? subtopicId : "";
        long now = System.currentTimeMillis();

        synchronized (LOCK) {
            JSONObject root = read();
            ensureIdentity(root);
            resetDailyQuota(root);
            if (root.optBoolean("freeUsed", false)) {
                int credits = root.optInt("credits", 0);
                if (credits < 1) throw new RuleException("NO_CREDITS");
                put(root, "credits", credits - 1);
            } else {
                put(root, "freeUsed", true);
            }

            String user = root.optString("userId");
            String id = id();
            JSONObject question = new JSONObject();
            put(question, "id", id);
            put(question, "text", text);
            put(question, "topic", topicId);
            put(question, "subtopic", sub);
            put(question, "authorId", user);
            put(question, "authorName", anonymousName(user));
            put(question, "authorCode", root.optString("myCode", ""));
            put(question, "createdAt", now);
            put(question, "expiresAt", now + UNANSWERED_TTL_MS);
            put(question, "views", 0);
            put(question, "answerIds", new JSONArray());
            put(question, "answers", new JSONArray());
            JSONArray questions = array(root, "questions");
            questions.put(question);
            trimQuestions(questions);

            JSONObject event = new JSONObject();
            put(event, "app", APP_ID); put(event, "v", 1); put(event, "type", "question");
            put(event, "id", id); put(event, "questionId", id); put(event, "text", text);
            put(event, "topic", topicId); put(event, "subtopic", sub);
            put(event, "authorId", user); put(event, "authorName", anonymousName(user));
            put(event, "authorCode", root.optString("myCode", ""));
            put(event, "createdAt", now); put(event, "expiresAt", now + UNANSWERED_TTL_MS);
            remember(root, id);
            enqueue(root, event, "public", "", "");
            write(root);
            return new Question(question);
        }
    }

    /** Store the local anonymous contact code and attach it to questions still in the outbox. */
    public void setMyCode(String code) {
        String value = code == null ? "" : code.trim();
        if (value.length() > 4096) return;
        synchronized (LOCK) {
            JSONObject root = read();
            ensureIdentity(root);
            put(root, "myCode", value);
            String local = root.optString("userId");
            JSONArray questions = array(root, "questions");
            for (int i = 0; i < questions.length(); i++) {
                JSONObject q = questions.optJSONObject(i);
                if (q != null && local.equals(q.optString("authorId"))) put(q, "authorCode", value);
            }
            JSONArray outbox = array(root, "outbox");
            for (int i = 0; i < outbox.length(); i++) {
                JSONObject item = outbox.optJSONObject(i);
                JSONObject event = item == null ? null : item.optJSONObject("event");
                if (event != null && local.equals(event.optString("authorId"))
                        && ("question".equals(event.optString("type")) || "private_answer".equals(event.optString("type")))) {
                    put(event, "authorCode", value);
                }
            }
            write(root);
        }
    }

    /** Create a private answer plus a body-free public answer count event. */
    public void answer(String questionId, String rawText) throws RuleException {
        String text = clean(rawText, ANSWER_LIMIT);
        if (text.isEmpty()) throw new RuleException("Write an answer first.");
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            JSONObject root = read();
            ensureIdentity(root);
            resetDailyQuota(root);
            String user = root.optString("userId");
            JSONObject q = findQuestion(root, questionId);
            if (q == null) throw new RuleException("QUESTION_MISSING");
            if (user.equals(q.optString("authorId"))) throw new RuleException("OWN_QUESTION");
            if (q.optLong("expiresAt", 0L) <= now && answerCount(q) == 0) throw new RuleException("EXPIRED");
            if (contains(array(root, "answeredQuestionIds"), questionId)) throw new RuleException("ALREADY_ANSWERED");
            String targetCode = q.optString("authorCode", "");
            if (targetCode.trim().isEmpty()) throw new RuleException("NO_REPLY_ROUTE");

            String answerId = id();
            String answerer = anonymousName(user);
            JSONObject direct = new JSONObject();
            put(direct, "app", APP_ID); put(direct, "v", 1); put(direct, "type", "private_answer");
            put(direct, "id", "p-" + answerId); put(direct, "answerId", answerId);
            put(direct, "questionId", questionId); put(direct, "authorId", user);
            put(direct, "authorName", answerer); put(direct, "authorCode", root.optString("myCode", ""));
            put(direct, "text", text); put(direct, "createdAt", now);

            JSONObject notice = new JSONObject();
            put(notice, "app", APP_ID); put(notice, "v", 1); put(notice, "type", "answer_notice");
            put(notice, "id", "n-" + answerId); put(notice, "answerId", answerId);
            put(notice, "questionId", questionId); put(notice, "answererId", user);
            put(notice, "answererName", answerer); put(notice, "createdAt", now);

            JSONArray answered = array(root, "answeredQuestionIds");
            answered.put(questionId);
            addAnswerId(q, answerId);
            put(root, "credits", root.optInt("credits", 0) + 1);
            remember(root, direct.optString("id"));
            remember(root, notice.optString("id"));
            enqueue(root, direct, "direct", targetCode, "");
            enqueue(root, notice, "public", "", "");
            write(root);
        }
    }

    /**
     * Apply a new event received in a YggChat thread. Public events are queued for
     * gossip to the rest of the known mesh; answer bodies are never relayed.
     */
    public boolean receiveEvent(JSONObject event, String fromPeer) {
        if (event == null || !APP_ID.equals(event.optString("app")) || event.optInt("v", 0) != 1) return false;
        String eventId = event.optString("id", "").trim();
        String type = event.optString("type", "");
        if (eventId.isEmpty() || eventId.length() > 160) return false;
        if (!"question".equals(type) && !"answer_notice".equals(type) && !"private_answer".equals(type)) return false;

        synchronized (LOCK) {
            JSONObject root = read();
            ensureIdentity(root);
            if (contains(array(root, "seenEventIds"), eventId)) return false;
            long now = System.currentTimeMillis();
            boolean accepted = false;

            if ("question".equals(type)) {
                String qid = bounded(event.optString("questionId", eventId), 160);
                String text = clean(event.optString("text", ""), QUESTION_LIMIT);
                long created = event.optLong("createdAt", now);
                if (created > now + 5L * 60L * 1000L) created = now;
                long deadline = Math.min(event.optLong("expiresAt", created + UNANSWERED_TTL_MS), created + UNANSWERED_TTL_MS);
                if (!qid.isEmpty() && !text.isEmpty() && deadline > now && !containsQuestion(root, qid)) {
                    JSONObject q = new JSONObject();
                    put(q, "id", qid); put(q, "text", text);
                    String topic = event.optString("topic", "all");
                    if (!Strings.validTopic(topic)) topic = "all";
                    String sub = event.optString("subtopic", "");
                    if (!validSubtopic(topic, sub)) sub = "";
                    put(q, "topic", topic); put(q, "subtopic", sub);
                    String authorId = bounded(event.optString("authorId", ""), 128);
                    if (authorId.isEmpty()) authorId = "remote:" + shortHash(event.optString("authorCode", qid));
                    put(q, "authorId", authorId);
                    put(q, "authorName", bounded(event.optString("authorName", Strings.text("en", "anonymous")), 64));
                    put(q, "authorCode", bounded(event.optString("authorCode", ""), 4096));
                    put(q, "createdAt", created); put(q, "expiresAt", Math.min(deadline, created + UNANSWERED_TTL_MS));
                    put(q, "views", 0); put(q, "answerIds", new JSONArray()); put(q, "answers", new JSONArray());
                    JSONArray questions = array(root, "questions");
                    prepend(questions, q);
                    trimQuestions(questions);
                    applyOrphanNotices(root, q);
                    accepted = true;
                }
            } else if ("answer_notice".equals(type)) {
                String qid = event.optString("questionId", "");
                String answerId = bounded(event.optString("answerId", eventId), 160);
                JSONObject q = findQuestion(root, qid);
                if (q != null) {
                    JSONArray ids = array(q, "answerIds");
                    if (!contains(ids, answerId)) {
                        addAnswerId(q, answerId);
                        accepted = true;
                    }
                } else if (!qid.isEmpty()) {
                    JSONArray orphans = array(root, "orphanNotices");
                    if (!containsObjectString(orphans, "answerId", answerId) && orphans.length() < 500) {
                        orphans.put(copy(event));
                        accepted = true;
                    }
                }
            } else {
                String qid = event.optString("questionId", "");
                JSONObject q = findQuestion(root, qid);
                String local = root.optString("userId");
                if (q != null && local.equals(q.optString("authorId"))) {
                    String answerId = bounded(event.optString("answerId", eventId), 160);
                    if (!contains(array(q, "answerIds"), answerId)) addAnswerId(q, answerId);
                    JSONArray answers = array(q, "answers");
                    if (!containsObjectString(answers, "id", answerId)) {
                        JSONObject answer = new JSONObject();
                        put(answer, "id", answerId);
                        put(answer, "text", clean(event.optString("text", ""), ANSWER_LIMIT));
                        put(answer, "authorName", bounded(event.optString("authorName", Strings.text("en", "anonymous")), 64));
                        put(answer, "createdAt", event.optLong("createdAt", now));
                        answers.put(answer);
                        while (answers.length() > 100) answers.remove(0);
                        accepted = true;
                    }
                }
            }

            remember(root, eventId);
            if (accepted && ("question".equals(type) || "answer_notice".equals(type))) {
                enqueue(root, event, "public", "", bounded(fromPeer, 4096));
            }
            write(root);
            return accepted;
        }
    }

    public List<PendingEvent> pendingEvents() {
        synchronized (LOCK) {
            JSONObject root = read();
            expire(root, System.currentTimeMillis());
            JSONArray outbox = array(root, "outbox");
            ArrayList<PendingEvent> result = new ArrayList<>();
            for (int i = 0; i < outbox.length(); i++) {
                JSONObject item = outbox.optJSONObject(i);
                JSONObject event = item == null ? null : item.optJSONObject("event");
                if (event != null) result.add(new PendingEvent(event, item.optString("route", "public"),
                        item.optString("target", ""), item.optString("exclude", "")));
            }
            write(root);
            return result;
        }
    }

    public void markSent(String eventId) {
        if (eventId == null || eventId.isEmpty()) return;
        synchronized (LOCK) {
            JSONObject root = read();
            JSONArray outbox = array(root, "outbox");
            for (int i = outbox.length() - 1; i >= 0; i--) {
                JSONObject item = outbox.optJSONObject(i);
                JSONObject event = item == null ? null : item.optJSONObject("event");
                if (event != null && eventId.equals(event.optString("id"))) outbox.remove(i);
            }
            write(root);
        }
    }

    public List<String> peerCodes() {
        synchronized (LOCK) {
            JSONArray array = array(read(), "peerCodes");
            ArrayList<String> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                String code = array.optString(i, "").trim();
                if (!code.isEmpty()) result.add(code);
            }
            return Collections.unmodifiableList(result);
        }
    }

    public boolean addPeerCode(String rawCode) throws RuleException {
        String code = rawCode == null ? "" : rawCode.trim();
        if (code.length() < 8 || code.length() > 4096) throw new RuleException("INVALID_PEER_CODE");
        synchronized (LOCK) {
            JSONObject root = read();
            JSONArray codes = array(root, "peerCodes");
            for (int i = 0; i < codes.length(); i++) if (code.equals(codes.optString(i))) return false;
            if (codes.length() >= MAX_PEERS) throw new RuleException("PEER_LIMIT");
            codes.put(code);
            write(root);
            return true;
        }
    }

    public String myCode() {
        synchronized (LOCK) { return read().optString("myCode", ""); }
    }

    public int pendingCount() {
        synchronized (LOCK) { return array(read(), "outbox").length(); }
    }

    public void expireNow() {
        synchronized (LOCK) {
            JSONObject root = read();
            expire(root, System.currentTimeMillis());
            write(root);
        }
    }

    private JSONObject read() {
        String raw = prefs.getString(STATE_KEY, "");
        if (raw == null || raw.isEmpty()) return new JSONObject();
        try { return new JSONObject(raw); }
        catch (JSONException ignored) { return new JSONObject(); }
    }

    private void write(JSONObject root) {
        prefs.edit().putString(STATE_KEY, root.toString()).apply();
    }

    private static void ensureIdentity(JSONObject root) {
        if (root.optString("userId", "").isEmpty()) put(root, "userId", UUID.randomUUID().toString());
        if (root.optInt("credits", -1) < 0) put(root, "credits", 0);
        if (root.optJSONArray("questions") == null) put(root, "questions", new JSONArray());
        if (root.optJSONArray("outbox") == null) put(root, "outbox", new JSONArray());
        if (root.optJSONArray("seenEventIds") == null) put(root, "seenEventIds", new JSONArray());
        if (root.optJSONArray("answeredQuestionIds") == null) put(root, "answeredQuestionIds", new JSONArray());
        if (root.optJSONArray("peerCodes") == null) put(root, "peerCodes", new JSONArray());
        if (root.optJSONArray("orphanNotices") == null) put(root, "orphanNotices", new JSONArray());
        if (root.optString("myCode", "").length() > 4096) put(root, "myCode", "");
    }

    private static void resetDailyQuota(JSONObject root) {
        SimpleDateFormat utcDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        utcDate.setTimeZone(TimeZone.getTimeZone("UTC"));
        String today = utcDate.format(new Date());
        if (!today.equals(root.optString("freeDate", ""))) {
            put(root, "freeDate", today);
            put(root, "freeUsed", false);
        }
    }

    private static void expire(JSONObject root, long now) {
        JSONArray questions = array(root, "questions");
        Set<String> removed = new HashSet<>();
        for (int i = questions.length() - 1; i >= 0; i--) {
            JSONObject q = questions.optJSONObject(i);
            if (q == null) { questions.remove(i); continue; }
            long age = now - q.optLong("createdAt", now);
            int answers = answerCount(q);
            boolean expiredOpen = answers == 0 && q.optLong("expiresAt", Long.MAX_VALUE) <= now;
            boolean oldAnswered = answers > 0 && age > ANSWERED_RETENTION_MS;
            if (expiredOpen || oldAnswered) {
                removed.add(q.optString("id"));
                questions.remove(i);
            }
        }
        if (!removed.isEmpty()) {
            JSONArray outbox = array(root, "outbox");
            for (int i = outbox.length() - 1; i >= 0; i--) {
                JSONObject item = outbox.optJSONObject(i);
                JSONObject event = item == null ? null : item.optJSONObject("event");
                if (event != null && "question".equals(event.optString("type")) && removed.contains(event.optString("questionId"))) outbox.remove(i);
            }
        }
        trimQuestions(questions);
    }

    private static void prepend(JSONArray array, JSONObject value) {
        array.put(value);
        for (int i = array.length() - 1; i > 0; i--) put(array, i, array.opt(i - 1));
        put(array, 0, value);
    }

    private static void trimQuestions(JSONArray questions) {
        while (questions.length() > MAX_QUESTIONS) questions.remove(questions.length() - 1);
    }

    private static JSONObject findQuestion(JSONObject root, String id) {
        if (id == null || id.isEmpty()) return null;
        JSONArray questions = array(root, "questions");
        for (int i = 0; i < questions.length(); i++) {
            JSONObject q = questions.optJSONObject(i);
            if (q != null && id.equals(q.optString("id"))) return q;
        }
        return null;
    }

    private static boolean containsQuestion(JSONObject root, String id) { return findQuestion(root, id) != null; }

    private static void addAnswerId(JSONObject question, String answerId) {
        if (answerId == null || answerId.isEmpty()) return;
        JSONArray ids = array(question, "answerIds");
        if (!contains(ids, answerId)) ids.put(answerId);
        put(question, "answerCount", ids.length());
    }

    private static void applyOrphanNotices(JSONObject root, JSONObject question) {
        JSONArray orphans = array(root, "orphanNotices");
        for (int i = orphans.length() - 1; i >= 0; i--) {
            JSONObject event = orphans.optJSONObject(i);
            if (event != null && question.optString("id").equals(event.optString("questionId"))) {
                addAnswerId(question, event.optString("answerId", event.optString("id")));
                orphans.remove(i);
            }
        }
    }

    private static void enqueue(JSONObject root, JSONObject event, String route, String target, String exclude) {
        String id = event.optString("id", "");
        if (id.isEmpty()) return;
        JSONArray outbox = array(root, "outbox");
        for (int i = 0; i < outbox.length(); i++) {
            JSONObject item = outbox.optJSONObject(i);
            JSONObject queued = item == null ? null : item.optJSONObject("event");
            if (queued != null && id.equals(queued.optString("id"))) return;
        }
        JSONObject item = new JSONObject();
        put(item, "event", copy(event));
        put(item, "route", route);
        put(item, "target", target == null ? "" : target);
        put(item, "exclude", exclude == null ? "" : exclude);
        put(item, "queuedAt", System.currentTimeMillis());
        outbox.put(item);
        while (outbox.length() > MAX_OUTBOX) outbox.remove(0);
    }

    private static void remember(JSONObject root, String eventId) {
        if (eventId == null || eventId.isEmpty()) return;
        JSONArray seen = array(root, "seenEventIds");
        if (contains(seen, eventId)) return;
        seen.put(eventId);
        while (seen.length() > MAX_SEEN) seen.remove(0);
    }

    private static int answerCount(JSONObject question) {
        JSONArray ids = question.optJSONArray("answerIds");
        return ids == null ? question.optInt("answerCount", 0) : Math.max(ids.length(), question.optInt("answerCount", 0));
    }

    private static JSONObject copy(JSONObject source) {
        try { return new JSONObject(source.toString()); }
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

    private static JSONArray array(JSONObject root, String key) {
        JSONArray result = root.optJSONArray(key);
        if (result == null) { result = new JSONArray(); put(root, key, result); }
        return result;
    }

    private static boolean contains(JSONArray array, String value) {
        if (array == null || value == null) return false;
        for (int i = 0; i < array.length(); i++) if (value.equals(array.optString(i))) return true;
        return false;
    }

    private static boolean containsObjectString(JSONArray array, String key, String value) {
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && value.equals(item.optString(key))) return true;
        }
        return false;
    }

    private static String clean(String raw, int limit) {
        if (raw == null) return "";
        String result = raw.replace('\u0000', ' ').trim();
        if (result.length() > limit) result = result.substring(0, limit).trim();
        return result;
    }

    private static String bounded(String value, int limit) {
        if (value == null) return "";
        String result = value.trim();
        return result.length() > limit ? result.substring(0, limit) : result;
    }

    private static String id() { return UUID.randomUUID().toString().replace("-", ""); }

    private static String anonymousName(String id) {
        String compact = id == null ? "0000" : id.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (compact.length() > 4) compact = compact.substring(0, 4);
        while (compact.length() < 4) compact += "0";
        return "Anonymous #" + compact;
    }

    private static String shortHash(String text) {
        int h = text == null ? 0 : text.hashCode();
        return Integer.toHexString(h);
    }

    private static boolean validSubtopic(String topicId, String subtopicId) {
        if (subtopicId == null || subtopicId.isEmpty()) return true;
        for (Strings.Subtopic sub : Strings.topic(topicId).subtopics) if (sub.id.equals(subtopicId)) return true;
        return false;
    }

    public static String timeAgo(long timestamp, String lang) {
        long seconds = Math.max(0, (System.currentTimeMillis() - timestamp) / 1000L);
        if (seconds < 60) return seconds + ("ru".equals(lang) ? " с" : " s");
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + ("ru".equals(lang) ? " м" : " min");
        long hours = minutes / 60;
        if (hours < 24) return hours + ("ru".equals(lang) ? " ч" : " h");
        return (hours / 24) + ("ru".equals(lang) ? " д" : " d");
    }

    public static final class Wallet {
        public final int credits;
        public final int freeQuestionsRemaining;
        public final String resetDateUtc;
        Wallet(int credits, int freeQuestionsRemaining, String resetDateUtc) {
            this.credits = credits; this.freeQuestionsRemaining = freeQuestionsRemaining; this.resetDateUtc = resetDateUtc;
        }
    }

    public static final class Question {
        public final String id;
        public final String text;
        public final String topic;
        public final String subtopic;
        public final String authorId;
        public final String authorName;
        public final String authorCode;
        public final long createdAt;
        public final long expiresAt;
        public final int views;
        public final int answerCount;
        public final List<Answer> answers;
        Question(JSONObject value) {
            id = value.optString("id", ""); text = value.optString("text", "");
            topic = value.optString("topic", "all"); subtopic = value.optString("subtopic", "");
            authorId = value.optString("authorId", ""); authorName = value.optString("authorName", "Anonymous");
            authorCode = value.optString("authorCode", ""); createdAt = value.optLong("createdAt", 0L);
            expiresAt = value.optLong("expiresAt", 0L); views = value.optInt("views", 0);
            JSONArray ids = value.optJSONArray("answerIds");
            answerCount = ids == null ? value.optInt("answerCount", 0) : Math.max(ids.length(), value.optInt("answerCount", 0));
            ArrayList<Answer> items = new ArrayList<>();
            JSONArray stored = value.optJSONArray("answers");
            if (stored != null) for (int i = 0; i < stored.length(); i++) {
                JSONObject answer = stored.optJSONObject(i);
                if (answer != null) items.add(new Answer(answer));
            }
            Collections.sort(items, new Comparator<Answer>() {
                @Override public int compare(Answer left, Answer right) {
                    if (left.createdAt == right.createdAt) return 0;
                    return left.createdAt < right.createdAt ? -1 : 1;
                }
            });
            answers = Collections.unmodifiableList(items);
        }
    }

    public static final class Answer {
        public final String id;
        public final String text;
        public final String authorName;
        public final long createdAt;
        Answer(JSONObject value) {
            id = value.optString("id", ""); text = value.optString("text", "");
            authorName = value.optString("authorName", "Anonymous"); createdAt = value.optLong("createdAt", 0L);
        }
    }

    public static final class PendingEvent {
        public final JSONObject event;
        public final String route;
        public final String target;
        public final String excludePeer;
        PendingEvent(JSONObject event, String route, String target, String excludePeer) {
            this.event = event; this.route = route; this.target = target; this.excludePeer = excludePeer;
        }
        public String id() { return event.optString("id", ""); }
    }

    public static final class RuleException extends Exception {
        public final String code;
        RuleException(String code) { super(code); this.code = code; }
    }
}
