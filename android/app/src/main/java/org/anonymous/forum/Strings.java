package org.anonymous.forum;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Compact in-code catalogue keeps the Android port resource-light and self-contained. */
public final class Strings {
    public static final String AUTO = "auto";
    private static final String PREFS = "forum_settings";
    private static final String[] CODES = {"en", "ru", "kk", "uz", "de", "fr", "es", "pt", "tr", "zh", "ja", "ko", "hi"};
    private static final String[] LANGUAGE_NAMES = {"English", "Русский", "Қазақша", "Oʻzbekcha", "Deutsch", "Français", "Español", "Português", "Türkçe", "简体中文", "日本語", "한국어", "हिन्दी"};
    private static final Map<String, Map<String, String>> TEXT = new LinkedHashMap<>();
    private static final Map<String, String[]> GREETINGS = new LinkedHashMap<>();
    private static final List<Topic> TOPICS;

    static {
        Map<String, String> en = new LinkedHashMap<>();
        Map<String, String> ru = new LinkedHashMap<>();
        add(en, "app", "Question → Answer"); add(ru, "app", "Вопрос → Ответ");
        add(en, "ask", "Ask"); add(ru, "ask", "Спросить");
        add(en, "answer", "Answer"); add(ru, "answer", "Ответить");
        add(en, "settings", "Settings"); add(ru, "settings", "Настройки");
        add(en, "question", "Question"); add(ru, "question", "Вопрос");
        add(en, "menu", "Menu"); add(ru, "menu", "Меню");
        add(en, "close", "Close"); add(ru, "close", "Закрыть");
        add(en, "q_welcome", "Ask people your question"); add(ru, "q_welcome", "Задайте вопрос людям");
        add(en, "q_description", "Real opinions and experience, shared anonymously"); add(ru, "q_description", "Мнения и опыт реальных людей — анонимно");
        add(en, "q_placeholder", "Your question…"); add(ru, "q_placeholder", "Ваш вопрос…");
        add(en, "a_placeholder", "Your answer…"); add(ru, "a_placeholder", "Ваш ответ…");
        add(en, "send", "Send"); add(ru, "send", "Отправить");
        add(en, "all", "All"); add(ru, "all", "Все");
        add(en, "search", "Search questions"); add(ru, "search", "Поиск вопросов");
        add(en, "empty_title", "Nothing here yet"); add(ru, "empty_title", "Пока тихо");
        add(en, "empty_feed", "No open questions in this topic"); add(ru, "empty_feed", "В этой теме пока нет открытых вопросов");
        add(en, "no_matches", "No matching questions"); add(ru, "no_matches", "Вопросы не найдены");
        add(en, "answers", "answers"); add(ru, "answers", "ответов");
        add(en, "views", "views"); add(ru, "views", "просмотров");
        add(en, "back", "Back"); add(ru, "back", "Назад");
        add(en, "credits", "credits"); add(ru, "credits", "кредитов");
        add(en, "free_today", "1 free question today"); add(ru, "free_today", "1 бесплатный вопрос сегодня");
        add(en, "free_left", "Free question available today"); add(ru, "free_left", "Бесплатный вопрос на сегодня доступен");
        add(en, "free_used", "Today's free question used"); add(ru, "free_used", "Бесплатный вопрос на сегодня использован");
        add(en, "credit_rule", "Each additional question costs 1 credit. Each answer earns 1 credit."); add(ru, "credit_rule", "Каждый следующий вопрос стоит 1 кредит. Каждый ответ приносит 1 кредит.");
        add(en, "no_credits", "Your free question is used. Answer someone to earn a credit."); add(ru, "no_credits", "Бесплатный вопрос уже использован. Ответьте кому-нибудь, чтобы получить кредит.");
        add(en, "question_posted", "Question saved. It will sync when a peer is reachable."); add(ru, "question_posted", "Вопрос сохранён. Он синхронизируется, когда будет доступен узел сети.");
        add(en, "answer_posted", "Answer queued privately for the asker. +1 question credit."); add(ru, "answer_posted", "Ответ сохранён для автора как приватный. +1 кредит на вопрос.");
        add(en, "no_reply_route", "This question has no usable reply code. Ask the question author to refresh their node code."); add(ru, "no_reply_route", "Для вопроса нет кода ответа. Попросите автора обновить код узла.");
        add(en, "answer_private", "Answers are private: only the question author can read them."); add(ru, "answer_private", "Ответы приватны: их видит только автор вопроса.");
        add(en, "waiting", "Waiting for anonymous answers…"); add(ru, "waiting", "Ожидаем анонимные ответы…");
        add(en, "your_question", "Your question"); add(ru, "your_question", "Ваш вопрос");
        add(en, "your_answer", "You already answered this question"); add(ru, "your_answer", "Вы уже ответили на этот вопрос");
        add(en, "answer_count_one", "answer"); add(ru, "answer_count_one", "ответ");
        add(en, "expired", "This unanswered question has expired"); add(ru, "expired", "Срок вопроса без ответа истёк");
        add(en, "language", "Language"); add(ru, "language", "Язык");
        add(en, "language_auto", "Use device language"); add(ru, "language_auto", "Язык устройства");
        add(en, "appearance", "Appearance"); add(ru, "appearance", "Оформление");
        add(en, "theme_system", "Theme follows device"); add(ru, "theme_system", "Тема как на устройстве");
        add(en, "theme_dark", "Dark theme"); add(ru, "theme_dark", "Тёмная тема");
        add(en, "theme_light", "Light theme"); add(ru, "theme_light", "Светлая тема");
        add(en, "network", "Yggdrasil network"); add(ru, "network", "Сеть Yggdrasil");
        add(en, "contacts", "contacts"); add(ru, "contacts", "контактов");
        add(en, "pending", "pending"); add(ru, "pending", "в очереди");
        add(en, "online", "Node running"); add(ru, "online", "Узел запущен");
        add(en, "connecting", "Starting node…"); add(ru, "connecting", "Запускаем узел…");
        add(en, "offline", "Offline — saved on this device"); add(ru, "offline", "Нет связи — сохранено на устройстве");
        add(en, "network_hint", "No central server. Add a peer code from another user to join the forum mesh."); add(ru, "network_hint", "Центрального сервера нет. Добавьте код другого узла, чтобы войти в форумную сеть.");
        add(en, "share_code", "Share my node code"); add(ru, "share_code", "Поделиться кодом узла");
        add(en, "add_peer", "Add a peer"); add(ru, "add_peer", "Добавить узел");
        add(en, "add_peer_hint", "Paste a YggChat contact code shared by another forum user."); add(ru, "add_peer_hint", "Вставьте код контакта YggChat, которым поделился другой участник.");
        add(en, "contact_code", "Contact code"); add(ru, "contact_code", "Код контакта");
        add(en, "copy", "Copy"); add(ru, "copy", "Копировать");
        add(en, "copied", "Copied to clipboard"); add(ru, "copied", "Скопировано");
        add(en, "peer_added", "Peer added"); add(ru, "peer_added", "Узел добавлен");
        add(en, "peer_required", "Add at least one peer to exchange questions."); add(ru, "peer_required", "Добавьте хотя бы один узел, чтобы обмениваться вопросами.");
        add(en, "anonymous", "Anonymous"); add(ru, "anonymous", "Аноним");
        add(en, "memory", "Holographic shard storage"); add(ru, "memory", "Голографическое хранение осколками");
        add(en, "memory_hint", "YggChat splits and redundantly distributes messages; shards expire according to the node vault policy."); add(ru, "memory_hint", "YggChat разбивает сообщения на осколки и дублирует их в сети; срок хранения задаёт политика хранилища узла.");
        add(en, "shard_size", "Shard size"); add(ru, "shard_size", "Размер осколка");
        add(en, "redundancy", "Redundancy"); add(ru, "redundancy", "Избыточность");
        add(en, "fanout", "Fan-out"); add(ru, "fanout", "Рассылка");
        add(en, "vault", "Vault limit"); add(ru, "vault", "Лимит хранилища");
        add(en, "ttl", "Shard retention"); add(ru, "ttl", "Срок хранения осколков");
        add(en, "purge", "Purge local shard cache"); add(ru, "purge", "Очистить локальный кэш осколков");
        add(en, "purge_confirm", "Remove this node's cached shards? Forum questions stored locally will remain."); add(ru, "purge_confirm", "Удалить кэш осколков этого узла? Локальные вопросы форума останутся.");
        add(en, "privacy", "Anonymous means pseudonymous: your node code is used to deliver private answers. Do not include identifying details in question text."); add(ru, "privacy", "Анонимность здесь псевдонимная: код узла нужен для доставки приватных ответов. Не указывайте личные данные в тексте вопроса.");
        add(en, "credits_local", "Credits and the daily free-question counter are stored on this device. Without accounts or a global consensus ledger they are not Sybil-proof."); add(ru, "credits_local", "Кредиты и суточный лимит хранятся на устройстве. Без аккаунтов и глобального консенсуса их нельзя защитить от повторных идентичностей.");
        add(en, "unanswered_ttl", "Unanswered questions expire after 24 hours."); add(ru, "unanswered_ttl", "Вопросы без ответов удаляются через 24 часа.");
        add(en, "close", "Close"); add(ru, "close", "Закрыть");
        TEXT.put("en", Collections.unmodifiableMap(en));
        TEXT.put("ru", Collections.unmodifiableMap(ru));

        GREETINGS.put("en", new String[]{"Ask people your question", "Real opinions and experience"});
        GREETINGS.put("ru", new String[]{"Задайте ваш вопрос людям!", "Мнения и опыт реальных людей"});
        GREETINGS.put("kk", new String[]{"Сұрағыңызды адамдарға қойыңыз!", "Нақты адамдардың пікірлері мен тәжірибесі"});
        GREETINGS.put("uz", new String[]{"Savolingizni odamlarga bering!", "Haqiqiy odamlarning fikrlari va tajribasi"});
        GREETINGS.put("de", new String[]{"Stelle deine Frage", "Echte Meinungen und Erfahrungen"});
        GREETINGS.put("fr", new String[]{"Posez votre question", "Des avis et expériences réels"});
        GREETINGS.put("es", new String[]{"¡Haz tu pregunta!", "Opiniones y experiencias reales"});
        GREETINGS.put("pt", new String[]{"Faça sua pergunta!", "Opiniões e experiências reais"});
        GREETINGS.put("tr", new String[]{"Sorunu insanlara sor", "Gerçek insanların görüşleri ve deneyimleri"});
        GREETINGS.put("zh", new String[]{"向大家提出你的问题吧！", "真实用户的真实观点与经验"});
        GREETINGS.put("ja", new String[]{"みんなに質問してみましょう！", "リアルな人々の意見と体験談"});
        GREETINGS.put("ko", new String[]{"사람들에게 질문해 보세요!", "실제 사람들의 의견과 경험"});
        GREETINGS.put("hi", new String[]{"लोगों से अपना प्रश्न पूछें!", "असली लोगों की राय और अनुभव"});

        ArrayList<Topic> topics = new ArrayList<>();
        topics.add(new Topic("all", "•", "Все", "All", Collections.<Subtopic>emptyList()));
        topics.add(new Topic("programming", ">_", "Программирование", "Programming", Arrays.asList(
                new Subtopic("bugs", "🐞", "Поиск ошибок", "Bug fixing"),
                new Subtopic("review", "👀", "Код-ревью", "Code review"),
                new Subtopic("architecture", "🏗️", "Архитектура", "Architecture"),
                new Subtopic("ithumor", ":-)", "IT-Юмор", "IT-Humor"),
                new Subtopic("itinside", "O_o", "IT-Инсайд", "IT-Inside"))));
        topics.add(new Topic("psychology", "🧠", "Психология", "Psychology", Arrays.asList(
                new Subtopic("self-acceptance", "🌱", "Принятие себя", "Self-acceptance"),
                new Subtopic("triggers", "⚡", "Триггеры", "Triggers"),
                new Subtopic("relationships", "💞", "Отношения", "Relationships"))));
        topics.add(new Topic("text", "📝", "Работа с текстом", "Text work", Arrays.asList(
                new Subtopic("editing", "✏️", "Редактирование", "Editing"),
                new Subtopic("translation", "🌐", "Перевод", "Translation"),
                new Subtopic("copywriting", "📣", "Копирайтинг", "Copywriting"))));
        topics.add(new Topic("misc", "💬", "Разное", "Other", Collections.<Subtopic>emptyList()));
        TOPICS = Collections.unmodifiableList(topics);
    }

    private Strings() { }

    private static void add(Map<String, String> target, String key, String value) {
        target.put(key, value);
    }

    public static String effectiveLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String chosen = prefs.getString("language", AUTO);
        if (AUTO.equals(chosen)) {
            String device = Locale.getDefault().getLanguage().toLowerCase(Locale.ROOT);
            return isSupported(device) ? device : "en";
        }
        return isSupported(chosen) ? chosen : "en";
    }

    public static String languagePreference(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("language", AUTO);
    }

    public static void setLanguagePreference(Context context, String code) {
        if (AUTO.equals(code) || isSupported(code)) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("language", code).apply();
        }
    }

    public static String text(Context context, String key) {
        return text(effectiveLanguage(context), key);
    }

    public static String text(String lang, String key) {
        String code = isSupported(lang) ? lang : "en";
        if ("q_welcome".equals(key) || "q_description".equals(key)) {
            String[] q = GREETINGS.get(code);
            if (q != null) return q["q_welcome".equals(key) ? 0 : 1];
        }
        Map<String, String> dict = TEXT.get(code);
        String value = dict == null ? null : dict.get(key);
        if (value == null) value = TEXT.get("en").get(key);
        return value == null ? key : value;
    }

    public static boolean isSupported(String code) {
        if (code == null) return false;
        for (String item : CODES) if (item.equals(code)) return true;
        return false;
    }

    public static List<Language> languages() {
        ArrayList<Language> result = new ArrayList<>();
        result.add(new Language(AUTO, "Device language"));
        for (int i = 0; i < CODES.length; i++) result.add(new Language(CODES[i], LANGUAGE_NAMES[i]));
        return result;
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

    public static final class Language {
        public final String code;
        public final String name;
        Language(String code, String name) { this.code = code; this.name = name; }
        @Override public String toString() { return name; }
    }

    public static final class Topic {
        public final String id;
        public final String icon;
        public final String ru;
        public final String en;
        public final List<Subtopic> subtopics;
        Topic(String id, String icon, String ru, String en, List<Subtopic> subtopics) {
            this.id = id; this.icon = icon; this.ru = ru; this.en = en;
            this.subtopics = Collections.unmodifiableList(new ArrayList<>(subtopics));
        }
        public String name(String lang) {
            if ("ru".equals(lang)) return ru;
            return en;
        }
    }

    public static final class Subtopic {
        public final String id;
        public final String icon;
        public final String ru;
        public final String en;
        Subtopic(String id, String icon, String ru, String en) {
            this.id = id; this.icon = icon; this.ru = ru; this.en = en;
        }
        public String name(String lang) { return "ru".equals(lang) ? ru : en; }
    }
}
