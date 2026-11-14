package org.anonymous.forum;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small, code-built Android UI. The UI depends on ForumCore, never directly on JNI. */
public final class MainActivity extends Activity {
    private static final String PAGE_ASK = "ask";
    private static final String PAGE_FEED = "feed";
    private static final String PAGE_DETAIL = "detail";
    private static final String PAGE_SETTINGS = "settings";
    private static final String SETTINGS_PREFS = "forum_settings";

    private ForumCore forum;
    private FrameLayout root;
    private LinearLayout appFrame;
    private LinearLayout drawer;
    private View scrim;
    private boolean drawerOpen;

    private String page = PAGE_ASK;
    private String selectedQuestionId = "";
    private String askTopic = "all";
    private String askSubtopic = "";
    private String filterTopic = "all";
    private String filterSubtopic = "";
    private String searchText = "";
    private String questionDraft = "";
    private String answerDraft = "";
    private String language;
    private String networkStatus = "";
    private String myCode = "";
    private String configJson = "{}";
    private int peerCount;
    private int pendingCount;
    private boolean online;

    private int bg;
    private int surface;
    private int surface2;
    private int border;
    private int textColor;
    private int muted;
    private int accent = Color.rgb(16, 185, 129);
    private int accentSoft;
    private boolean dark;

    private LinearLayout feedList;
    private LinearLayout askTopicPanel;
    private EditText askInput;
    private EditText answerInput;
    private LinearLayout answerComposer;
    private boolean rendering;

    private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!YggNodeService.ACTION_STATUS.equals(intent.getAction())) return;
            online = intent.getBooleanExtra("online", false);
            networkStatus = intent.getStringExtra("status");
            if (networkStatus == null) networkStatus = "";
            myCode = intent.getStringExtra("myCode");
            if (myCode == null) myCode = "";
            peerCount = intent.getIntExtra("peerCount", 0);
            pendingCount = intent.getIntExtra("pendingCount", 0);
            configJson = intent.getStringExtra("configJson");
            if (configJson == null) configJson = "{}";
            if (!myCode.isEmpty()) forum.setMyCode(myCode);
            if (PAGE_ASK.equals(page) && askInput != null && askInput.hasFocus()) return;
            render();
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        forum = new ForumCore(this);
        language = Strings.effectiveLanguage(this);
        networkStatus = Strings.text(language, "status_starting");
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        applyPalette();
        setContentView(buildRoot());
        registerNetworkReceiver();
        YggNodeService.start(this);
    }

    @Override protected void onResume() {
        super.onResume();
        language = Strings.effectiveLanguage(this);
        render();
        YggNodeService.sync(this);
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(networkReceiver); } catch (IllegalArgumentException ignored) { }
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (drawerOpen) { closeDrawer(); return; }
        if (PAGE_DETAIL.equals(page)) { page = PAGE_FEED; answerDraft = ""; render(); return; }
        if (PAGE_SETTINGS.equals(page) || PAGE_FEED.equals(page)) { page = PAGE_ASK; render(); return; }
        super.onBackPressed();
    }

    private void registerNetworkReceiver() {
        IntentFilter filter = new IntentFilter(YggNodeService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(networkReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(networkReceiver, filter);
    }

    private void applyPalette() {
        SharedPreferences prefs = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE);
        String mode = prefs.getString("theme", "system");
        boolean systemDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        dark = "dark".equals(mode) || ("system".equals(mode) && systemDark);
        if (dark) {
            bg = Color.rgb(10, 10, 12); surface = Color.rgb(20, 20, 23); surface2 = Color.rgb(28, 28, 33);
            border = Color.rgb(38, 38, 44); textColor = Color.rgb(242, 242, 244); muted = Color.rgb(139, 139, 147);
            accentSoft = Color.argb(38, 16, 185, 129);
        } else {
            bg = Color.rgb(244, 244, 246); surface = Color.WHITE; surface2 = Color.rgb(247, 247, 249);
            border = Color.rgb(230, 230, 234); textColor = Color.rgb(17, 17, 20); muted = Color.rgb(113, 113, 122);
            accentSoft = Color.argb(30, 16, 185, 129);
        }
        Window window = getWindow();
        window.setStatusBarColor(bg);
        window.setNavigationBarColor(surface);
        int flags = window.getDecorView().getSystemUiVisibility();
        flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private View buildRoot() {
        root = new FrameLayout(this);
        root.setBackgroundColor(bg);
        appFrame = new LinearLayout(this);
        appFrame.setOrientation(LinearLayout.VERTICAL);
        appFrame.setBackgroundColor(surface);
        root.addView(appFrame, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        scrim = new View(this);
        scrim.setBackgroundColor(Color.argb(150, 0, 0, 0));
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(v -> closeDrawer());
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        drawer = buildDrawer();
        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(dp(304), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        drawer.setTranslationX(-dp(304));
        root.addView(drawer, drawerParams);
        render();
        return root;
    }

    private void render() {
        if (appFrame == null) return;
        rendering = true;
        appFrame.removeAllViews();
        appFrame.addView(buildToolbar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
        View body;
        if (PAGE_FEED.equals(page)) body = buildFeedPage();
        else if (PAGE_DETAIL.equals(page)) body = buildDetailPage();
        else if (PAGE_SETTINGS.equals(page)) body = buildSettingsPage();
        else body = buildAskPage();
        appFrame.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        rendering = false;
    }

    private View buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(12), 0);
        bar.setBackgroundColor(surface);
        TextView menu = iconButton("☰", 22);
        menu.setContentDescription(Strings.text(language, "menu"));
        menu.setOnClickListener(v -> openDrawer());
        bar.addView(menu, new LinearLayout.LayoutParams(dp(44), dp(44)));

        View dot = new View(this);
        dot.setBackground(round(accent, 8, 0, Color.TRANSPARENT));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotParams.setMargins(dp(5), 0, dp(8), 0);
        bar.addView(dot, dotParams);
        TextView title = label(pageTitle(), 17, textColor, true);
        title.setSingleLine(true);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView network = label(online ? "●" : "○", 15, online ? accent : muted, true);
        network.setGravity(Gravity.CENTER);
        network.setOnClickListener(v -> { page = PAGE_SETTINGS; render(); });
        bar.addView(network, new LinearLayout.LayoutParams(dp(30), dp(40)));
        ForumCore.Wallet wallet = forum.wallet();
        String creditText = wallet.freeQuestionsRemaining > 0
                ? Strings.text(language, "credit_badge_free") + " · " + wallet.credits
                : wallet.credits + " " + Strings.text(language, "credit_badge");
        TextView credits = label("◈ " + creditText, 12, accent, true);
        credits.setGravity(Gravity.CENTER);
        credits.setPadding(dp(8), dp(5), dp(8), dp(5));
        credits.setBackground(round(accentSoft, 18, 0, Color.TRANSPARENT));
        credits.setOnClickListener(v -> { page = PAGE_SETTINGS; render(); });
        bar.addView(credits, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        return bar;
    }

    private String pageTitle() {
        if (PAGE_FEED.equals(page)) return Strings.text(language, "answer");
        if (PAGE_DETAIL.equals(page)) return Strings.text(language, "question");
        if (PAGE_SETTINGS.equals(page)) return Strings.text(language, "settings");
        return Strings.text(language, "ask");
    }

    private LinearLayout buildAskPage() {
        LinearLayout pageView = column();
        pageView.setBackgroundColor(surface);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout hero = column();
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(28), dp(28), dp(28), dp(24));
        TextView bubble = label("💬", 34, accent, false);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(round(accentSoft, 40, 0, Color.TRANSPARENT));
        hero.addView(bubble, new LinearLayout.LayoutParams(dp(76), dp(76)));
        TextView title = label(Strings.text(language, "q_welcome"), 23, textColor, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(18), 0, dp(8));
        hero.addView(title, titleParams);
        TextView description = label(Strings.text(language, "q_description"), 14, muted, false);
        description.setGravity(Gravity.CENTER);
        hero.addView(description);

        ForumCore.Wallet wallet = forum.wallet();
        LinearLayout quota = cardColumn();
        TextView quotaTitle = label(wallet.freeQuestionsRemaining > 0
                ? Strings.text(language, "free_left") : Strings.text(language, "free_used"), 14, textColor, true);
        quota.addView(quotaTitle);
        TextView quotaInfo = label(Strings.text(language, "credit_rule"), 12, muted, false);
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qp.setMargins(0, dp(6), 0, 0);
        quota.addView(quotaInfo, qp);
        LinearLayout.LayoutParams qbox = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qbox.setMargins(0, dp(24), 0, 0);
        hero.addView(quota, qbox);
        scroll.addView(hero, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pageView.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout composer = column();
        composer.setPadding(dp(12), dp(8), dp(12), dp(12));
        composer.setBackground(round(surface, 18, 1, border));
        askTopicPanel = column();
        askTopicPanel.setVisibility(questionDraft.trim().isEmpty() ? View.GONE : View.VISIBLE);
        composer.addView(askTopicPanel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        updateAskTopicPanel();

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.BOTTOM);
        askInput = new EditText(this);
        askInput.setTextColor(textColor);
        askInput.setHintTextColor(muted);
        askInput.setTextSize(15);
        askInput.setHint(Strings.text(language, "q_placeholder"));
        askInput.setGravity(Gravity.TOP | Gravity.START);
        askInput.setMinLines(1);
        askInput.setMaxLines(5);
        askInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(ForumCore.QUESTION_LIMIT)});
        askInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        askInput.setPadding(dp(13), dp(12), dp(13), dp(10));
        askInput.setBackground(round(surface2, 14, 1, border));
        askInput.setText(questionDraft);
        row.addView(askInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView send = label("↑", 24, Color.WHITE, true);
        send.setGravity(Gravity.CENTER);
        send.setBackground(round(accent, 14, 0, Color.TRANSPARENT));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        sendParams.setMargins(dp(8), 0, 0, 0);
        row.addView(send, sendParams);
        composer.addView(row);
        pageView.addView(composer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        askInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                questionDraft = s.toString();
                if (send != null) send.setAlpha(questionDraft.trim().isEmpty() ? 0.45f : 1f);
                if (askTopicPanel != null) {
                    askTopicPanel.setVisibility(questionDraft.trim().isEmpty() ? View.GONE : View.VISIBLE);
                    updateAskTopicPanel();
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        send.setAlpha(questionDraft.trim().isEmpty() ? 0.45f : 1f);
        send.setOnClickListener(v -> sendQuestion());
        return pageView;
    }

    private void updateAskTopicPanel() {
        if (askTopicPanel == null) return;
        askTopicPanel.removeAllViews();
        if (questionDraft.trim().isEmpty()) return;
        askTopicPanel.addView(topicChips(askTopic, false, topic -> {
            askTopic = topic;
            askSubtopic = "";
            updateAskTopicPanel();
        }));
        Strings.Topic chosen = Strings.topic(askTopic);
        if (!chosen.subtopics.isEmpty()) {
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subParams.setMargins(0, dp(5), 0, dp(7));
            askTopicPanel.addView(subtopicChips(chosen, askSubtopic, sub -> {
                askSubtopic = askSubtopic.equals(sub) ? "" : sub;
                updateAskTopicPanel();
            }), subParams);
        }
    }

    private void sendQuestion() {
        String draft = questionDraft.trim();
        if (draft.isEmpty()) return;
        try {
            forum.postQuestion(draft, askTopic, askSubtopic);
            questionDraft = "";
            if (askInput != null) askInput.setText("");
            askTopic = "all";
            askSubtopic = "";
            toast(Strings.text(language, "question_posted"));
            YggNodeService.sync(this);
            render();
        } catch (ForumCore.RuleException error) {
            if ("NO_CREDITS".equals(error.code)) toast(Strings.text(language, "no_credits"));
            else toast(Strings.text(language, "write_first"));
        }
    }

    private LinearLayout buildFeedPage() {
        LinearLayout container = column();
        container.setBackgroundColor(surface);
        LinearLayout header = column();
        header.setPadding(dp(16), dp(12), dp(16), dp(8));
        TextView title = label(Strings.text(language, "answer"), 21, textColor, true);
        header.addView(title);
        TextView subtitle = label(peerCount == 0 ? Strings.text(language, "peer_required")
                : (Strings.text(language, online ? "online" : "connecting") + " · " + peerCount), 12, muted, false);
        LinearLayout.LayoutParams subTitleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subTitleParams.setMargins(0, dp(4), 0, dp(8));
        header.addView(subtitle, subTitleParams);
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setTextColor(textColor);
        search.setHintTextColor(muted);
        search.setHint(Strings.text(language, "search"));
        search.setPadding(dp(12), dp(9), dp(12), dp(9));
        search.setBackground(round(surface2, 12, 1, border));
        search.setText(searchText);
        header.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        HorizontalScrollView topicsScroll = horizontalScroll();
        LinearLayout topicsRow = new LinearLayout(this);
        topicsRow.setGravity(Gravity.CENTER_VERTICAL);
        for (Strings.Topic topic : Strings.topics()) {
            TextView chip = chip(topic.icon + " " + topic.name(language), topic.id.equals(filterTopic), false);
            chip.setOnClickListener(v -> {
                filterTopic = topic.id;
                filterSubtopic = "";
                render();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            cp.setMargins(0, 0, dp(7), 0);
            topicsRow.addView(chip, cp);
        }
        topicsScroll.addView(topicsRow);
        LinearLayout.LayoutParams topicParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        topicParams.setMargins(0, dp(8), 0, 0);
        header.addView(topicsScroll, topicParams);
        if (!"all".equals(filterTopic) && !Strings.topic(filterTopic).subtopics.isEmpty()) {
            HorizontalScrollView subScroll = horizontalScroll();
            LinearLayout subRow = new LinearLayout(this);
            for (Strings.Subtopic sub : Strings.topic(filterTopic).subtopics) {
                TextView chip = chip(sub.name(language), sub.id.equals(filterSubtopic), true);
                chip.setOnClickListener(v -> {
                    filterSubtopic = filterSubtopic.equals(sub.id) ? "" : sub.id;
                    render();
                });
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
                cp.setMargins(0, 0, dp(6), 0);
                subRow.addView(chip, cp);
            }
            subScroll.addView(subRow);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
            sp.setMargins(0, dp(4), 0, 0);
            header.addView(subScroll, sp);
        }
        container.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        feedList = column();
        feedList.setPadding(dp(10), dp(4), dp(10), dp(12));
        ScrollView listScroll = new ScrollView(this);
        listScroll.setFillViewport(true);
        listScroll.addView(feedList);
        container.addView(listScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchText = s.toString();
                renderFeedList();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        renderFeedList();
        return container;
    }

    private void renderFeedList() {
        if (feedList == null) return;
        feedList.removeAllViews();
        String localId = forum.localId();
        String query = searchText.trim().toLowerCase(Locale.forLanguageTag(language));
        ArrayList<ForumCore.Question> filtered = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ForumCore.Question q : forum.questions()) {
            if (localId.equals(q.authorId)) continue;
            if (q.answerCount == 0 && q.expiresAt <= now) continue;
            if (!"all".equals(filterTopic) && !filterTopic.equals(q.topic)) continue;
            if (!filterSubtopic.isEmpty() && !filterSubtopic.equals(q.subtopic)) continue;
            if (!query.isEmpty() && !q.text.toLowerCase(Locale.forLanguageTag(language)).contains(query)) continue;
            filtered.add(q);
        }
        if (filtered.isEmpty()) {
            LinearLayout empty = cardColumn();
            TextView icon = label("✦", 28, accent, true); icon.setGravity(Gravity.CENTER);
            empty.addView(icon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
            TextView title = label(query.isEmpty() ? Strings.text(language, "empty_title") : Strings.text(language, "no_matches"), 17, textColor, true);
            title.setGravity(Gravity.CENTER);
            empty.addView(title);
            TextView hint = label(query.isEmpty() ? Strings.text(language, "empty_feed") : Strings.text(language, "network_hint"), 13, muted, false);
            hint.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.setMargins(0, dp(6), 0, 0);
            empty.addView(hint, hp);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ep.setMargins(dp(2), dp(26), dp(2), 0);
            feedList.addView(empty, ep);
            if (peerCount == 0) {
                TextView add = primaryButton(Strings.text(language, "add_peer"));
                add.setOnClickListener(v -> showAddPeerDialog());
                LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
                ap.setMargins(dp(2), dp(12), dp(2), 0);
                feedList.addView(add, ap);
            }
            return;
        }
        for (ForumCore.Question q : filtered) feedList.addView(questionCard(q), cardParams());
        TextView ttl = label(Strings.text(language, "unanswered_ttl"), 11, muted, false);
        ttl.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.setMargins(0, dp(8), 0, dp(8));
        feedList.addView(ttl, tp);
    }

    private View questionCard(ForumCore.Question q) {
        LinearLayout card = cardColumn();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView title = label(q.text, 15, textColor, false);
        title.setLineSpacing(dp(2), 1f);
        card.addView(title);
        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(0, dp(9), 0, 0);
        TextView topic = chip(topicTag(q), false, true);
        topic.setPadding(dp(8), dp(3), dp(8), dp(3));
        meta.addView(topic, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(25)));
        TextView stats = label("  " + answerCountLabel(q.answerCount) + "   ·   ◉ " + q.views + "   ·   " + ForumCore.timeAgo(q.createdAt, language), 11, muted, false);
        stats.setSingleLine(true);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        statsParams.setMargins(dp(4), 0, 0, 0);
        meta.addView(stats, statsParams);
        card.addView(meta);
        card.setOnClickListener(v -> openQuestion(q.id));
        card.setClickable(true);
        return card;
    }

    private LinearLayout buildDetailPage() {
        LinearLayout pageView = column();
        pageView.setBackgroundColor(surface);
        ForumCore.Question q = forum.question(selectedQuestionId);
        if (q == null) {
            pageView.addView(backRow(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            TextView missing = label(Strings.text(language, "empty_title"), 17, muted, false);
            missing.setGravity(Gravity.CENTER);
            pageView.addView(missing, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return pageView;
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(dp(16), dp(12), dp(16), dp(18));
        content.addView(backRow(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        LinearLayout questionCard = cardColumn();
        TextView topic = chip(topicTag(q), false, true);
        topic.setPadding(dp(9), dp(4), dp(9), dp(4));
        questionCard.addView(topic, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));
        TextView questionText = label(q.text, 19, textColor, true);
        questionText.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams qtp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qtp.setMargins(0, dp(12), 0, dp(9));
        questionCard.addView(questionText, qtp);
        TextView metadata = label(q.authorName + "  ·  " + ForumCore.timeAgo(q.createdAt, language)
                + "  ·  " + q.views + " " + Strings.text(language, "views"), 12, muted, false);
        questionCard.addView(metadata);
        LinearLayout.LayoutParams cardMargin = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardMargin.setMargins(0, dp(4), 0, dp(12));
        content.addView(questionCard, cardMargin);

        boolean own = forum.localId().equals(q.authorId);
        TextView count = label(answerCountLabel(q.answerCount), 15, textColor, true);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countParams.setMargins(dp(2), dp(4), 0, dp(8));
        content.addView(count, countParams);
        TextView privacy = label(Strings.text(language, "answer_private"), 12, muted, false);
        privacy.setPadding(dp(12), dp(10), dp(12), dp(10));
        privacy.setBackground(round(surface2, 12, 1, border));
        content.addView(privacy);

        if (own) {
            if (q.answers.isEmpty()) {
                TextView waiting = label(Strings.text(language, "waiting"), 14, muted, false);
                waiting.setGravity(Gravity.CENTER);
                waiting.setPadding(dp(12), dp(28), dp(12), dp(28));
                content.addView(waiting);
            } else {
                for (ForumCore.Answer answer : q.answers) {
                    LinearLayout answerCard = cardColumn();
                    TextView answerText = label(answer.text, 15, textColor, false);
                    answerCard.addView(answerText);
                    TextView answerMeta = label(answer.authorName + "  ·  " + ForumCore.timeAgo(answer.createdAt, language), 11, muted, false);
                    LinearLayout.LayoutParams amp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    amp.setMargins(0, dp(8), 0, 0);
                    answerCard.addView(answerMeta, amp);
                    LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    ap.setMargins(0, dp(8), 0, 0);
                    content.addView(answerCard, ap);
                }
                if (q.answerCount > q.answers.size()) {
                    TextView more = label("+ " + (q.answerCount - q.answers.size()) + " " + Strings.text(language, "answer_private"), 12, muted, false);
                    more.setPadding(dp(4), dp(10), dp(4), dp(10));
                    content.addView(more);
                }
            }
        } else if (forum.hasAnswered(selectedQuestionId)) {
            TextView done = label("✓  " + Strings.text(language, "your_answer") + "\n+1 " + Strings.text(language, "credits"), 14, accent, true);
            done.setPadding(dp(14), dp(14), dp(14), dp(14));
            done.setBackground(round(accentSoft, 14, 0, Color.TRANSPARENT));
            LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            doneParams.setMargins(0, dp(12), 0, 0);
            content.addView(done, doneParams);
        } else if (q.answerCount == 0 && q.expiresAt <= System.currentTimeMillis()) {
            TextView expired = label(Strings.text(language, "expired"), 14, muted, false);
            expired.setPadding(0, dp(18), 0, dp(18));
            content.addView(expired);
        } else {
            TextView earn = label(Strings.text(language, "earn_credit"), 12, accent, true);
            LinearLayout.LayoutParams earnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            earnParams.setMargins(0, dp(14), 0, dp(8));
            content.addView(earn, earnParams);
        }
        scroll.addView(content);
        pageView.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (!own && !forum.hasAnswered(q.id) && !(q.answerCount == 0 && q.expiresAt <= System.currentTimeMillis())) {
            answerComposer = column();
            answerComposer.setPadding(dp(12), dp(8), dp(12), dp(12));
            answerComposer.setBackground(round(surface, 18, 1, border));
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.BOTTOM);
            answerInput = new EditText(this);
            answerInput.setTextColor(textColor); answerInput.setHintTextColor(muted);
            answerInput.setHint(Strings.text(language, "a_placeholder"));
            answerInput.setTextSize(15); answerInput.setMinLines(1); answerInput.setMaxLines(5);
            answerInput.setGravity(Gravity.TOP | Gravity.START);
            answerInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            answerInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(ForumCore.ANSWER_LIMIT)});
            answerInput.setPadding(dp(13), dp(12), dp(13), dp(10));
            answerInput.setBackground(round(surface2, 14, 1, border));
            answerInput.setText(answerDraft);
            row.addView(answerInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView send = label("↑", 24, Color.WHITE, true);
            send.setGravity(Gravity.CENTER);
            send.setBackground(round(accent, 14, 0, Color.TRANSPARENT));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(46), dp(46));
            sp.setMargins(dp(8), 0, 0, 0);
            row.addView(send, sp);
            answerComposer.addView(row);
            pageView.addView(answerComposer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            answerInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { answerDraft = s.toString(); send.setAlpha(answerDraft.trim().isEmpty() ? 0.45f : 1f); }
                @Override public void afterTextChanged(Editable s) { }
            });
            send.setAlpha(answerDraft.trim().isEmpty() ? 0.45f : 1f);
            send.setOnClickListener(v -> sendAnswer(q.id));
        } else {
            answerComposer = null; answerInput = null;
        }
        return pageView;
    }

    private View backRow() {
        TextView back = label("‹  " + Strings.text(language, "back"), 14, accent, true);
        back.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        back.setOnClickListener(v -> { hideKeyboard(); page = PAGE_FEED; answerDraft = ""; render(); });
        return back;
    }

    private void openQuestion(String id) {
        selectedQuestionId = id;
        forum.recordView(id);
        page = PAGE_DETAIL;
        answerDraft = "";
        hideKeyboard();
        render();
    }

    private void sendAnswer(String questionId) {
        String draft = answerDraft.trim();
        if (draft.isEmpty()) return;
        try {
            forum.answer(questionId, draft);
            answerDraft = "";
            toast(Strings.text(language, "answer_posted"));
            YggNodeService.sync(this);
            render();
        } catch (ForumCore.RuleException error) {
            switch (error.code) {
                case "EMPTY_ANSWER": toast(Strings.text(language, "write_answer_first")); break;
                case "NO_REPLY_ROUTE": toast(Strings.text(language, "no_reply_route")); break;
                case "ALREADY_ANSWERED": toast(Strings.text(language, "your_answer")); break;
                case "EXPIRED": toast(Strings.text(language, "expired")); break;
                default: toast(Strings.text(language, "empty_feed"));
            }
        }
    }

    private LinearLayout buildSettingsPage() {
        LinearLayout outer = column();
        outer.setBackgroundColor(surface);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(dp(16), dp(14), dp(16), dp(24));
        TextView title = label(Strings.text(language, "settings"), 22, textColor, true);
        content.addView(title);

        LinearLayout wallet = cardColumn();
        LinearLayout walletHeader = new LinearLayout(this);
        walletHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView walletLabel = label("◈  " + Strings.text(language, "credits"), 14, muted, true);
        walletHeader.addView(walletLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ForumCore.Wallet balance = forum.wallet();
        TextView balanceText = label(String.valueOf(balance.credits), 25, accent, true);
        walletHeader.addView(balanceText);
        wallet.addView(walletHeader);
        TextView free = label(balance.freeQuestionsRemaining > 0 ? Strings.text(language, "free_left") : Strings.text(language, "free_used"), 13, textColor, true);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.setMargins(0, dp(8), 0, 0);
        wallet.addView(free, fp);
        TextView rule = label(Strings.text(language, "credit_rule"), 12, muted, false);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, dp(6), 0, 0);
        wallet.addView(rule, rp);
        addCard(content, wallet);

        View langButton = settingRow("🌐", Strings.text(language, "language"), selectedLanguageName());
        langButton.setPadding(dp(14), dp(12), dp(14), dp(12));
        langButton.setBackground(round(surface2, 14, 1, border));
        langButton.setOnClickListener(v -> showLanguageDialog());
        addCard(content, langButton);

        LinearLayout networkCard = cardColumn();
        LinearLayout networkHeader = new LinearLayout(this);
        networkHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView networkTitle = label("◉  " + Strings.text(language, "network"), 15, textColor, true);
        networkHeader.addView(networkTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView nodeState = label(online ? "●  " + Strings.text(language, "online") : "○  " + networkStatus, 11, online ? accent : muted, true);
        nodeState.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        networkHeader.addView(nodeState);
        networkCard.addView(networkHeader);
        TextView peerLine = label(peerCount + " " + Strings.text(language, "contacts") + " · " + pendingCount + " " + Strings.text(language, "pending"), 12, muted, false);
        LinearLayout.LayoutParams peerp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        peerp.setMargins(0, dp(6), 0, dp(2));
        networkCard.addView(peerLine, peerp);
        TextView networkHint = label(Strings.text(language, "network_hint"), 12, muted, false);
        LinearLayout.LayoutParams nhp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nhp.setMargins(0, dp(5), 0, dp(10));
        networkCard.addView(networkHint, nhp);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView share = secondaryButton(Strings.text(language, "share_code"));
        TextView add = primaryButton(Strings.text(language, "add_peer"));
        actions.addView(share, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams addp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        addp.setMargins(dp(8), 0, 0, 0);
        actions.addView(add, addp);
        networkCard.addView(actions);
        share.setOnClickListener(v -> shareMyCode());
        add.setOnClickListener(v -> showAddPeerDialog());
        addCard(content, networkCard);

        LinearLayout shards = cardColumn();
        TextView memoryTitle = label("◈  " + Strings.text(language, "memory"), 15, textColor, true);
        shards.addView(memoryTitle);
        TextView memoryHint = label(Strings.text(language, "memory_hint"), 12, muted, false);
        LinearLayout.LayoutParams mhp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mhp.setMargins(0, dp(6), 0, dp(10));
        shards.addView(memoryHint, mhp);
        JSONObject cfg = parseConfig(configJson);
        addConfigLine(shards, Strings.text(language, "shard_size"), cfg.optString("ShardSize", "default"));
        addConfigLine(shards, Strings.text(language, "redundancy"), cfg.optString("Redundancy", "default"));
        addConfigLine(shards, Strings.text(language, "fanout"), cfg.optString("Fanout", "default"));
        addConfigLine(shards, Strings.text(language, "vault"), cfg.optString("VaultMax", "default"));
        addConfigLine(shards, Strings.text(language, "ttl"), cfg.optString("TTLHours", "default") + " h");
        TextView purge = secondaryButton(Strings.text(language, "purge"));
        LinearLayout.LayoutParams purgeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        purgeParams.setMargins(0, dp(10), 0, 0);
        shards.addView(purge, purgeParams);
        purge.setOnClickListener(v -> confirmPurge());
        addCard(content, shards);

        TextView privacy = label(Strings.text(language, "privacy"), 12, muted, false);
        privacy.setPadding(dp(4), dp(6), dp(4), dp(12));
        content.addView(privacy);
        TextView creditsNote = label(Strings.text(language, "credits_local"), 12, muted, false);
        creditsNote.setPadding(dp(4), dp(2), dp(4), dp(12));
        content.addView(creditsNote);
        scroll.addView(content);
        outer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return outer;
    }

    private void addConfigLine(LinearLayout parent, String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView left = label(title, 12, muted, false);
        row.addView(left, new LinearLayout.LayoutParams(0, dp(25), 1f));
        TextView right = label(value, 12, textColor, true);
        row.addView(right);
        parent.addView(row);
    }

    private JSONObject parseConfig(String json) {
        try { return new JSONObject(json == null ? "{}" : json); }
        catch (JSONException ignored) { return new JSONObject(); }
    }

    private View settingRow(String icon, String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView ico = label(icon, 18, accent, false);
        row.addView(ico, new LinearLayout.LayoutParams(dp(30), dp(36)));
        LinearLayout text = column();
        text.addView(label(title, 14, textColor, true));
        TextView detail = label(value, 11, muted, false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.setMargins(0, dp(3), 0, 0);
        text.addView(detail, detailParams);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = label("›", 24, muted, false);
        row.addView(arrow);
        return row;
    }

    private String selectedLanguageName() {
        String selected = Strings.languagePreference(this);
        for (Strings.Language item : Strings.languages()) if (item.code.equals(selected)) {
            return Strings.AUTO.equals(selected) ? Strings.text(language, "language_auto") : item.name;
        }
        return Strings.text(language, "language_auto");
    }

    private void showLanguageDialog() {
        List<Strings.Language> languages = Strings.languages();
        String[] names = new String[languages.size()];
        String selected = Strings.languagePreference(this);
        int checked = 0;
        for (int i = 0; i < languages.size(); i++) {
            Strings.Language item = languages.get(i);
            names[i] = Strings.AUTO.equals(item.code) ? Strings.text(language, "language_auto") : item.name;
            if (item.code.equals(selected)) checked = i;
        }
        new AlertDialog.Builder(this).setTitle(Strings.text(language, "language"))
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    Strings.setLanguagePreference(this, languages.get(which).code);
                    language = Strings.effectiveLanguage(this);
                    YggNodeService.sync(this);
                    dialog.dismiss();
                    refreshDrawer();
                    render();
                }).setNegativeButton(Strings.text(language, "close"), null).show();
    }

    private void showAddPeerDialog() {
        LinearLayout box = column();
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView hint = label(Strings.text(language, "add_peer_hint"), 13, muted, false);
        box.addView(hint);
        EditText code = new EditText(this);
        code.setTextColor(textColor); code.setHintTextColor(muted);
        code.setHint(Strings.text(language, "contact_code"));
        code.setTextSize(13); code.setMinLines(2); code.setMaxLines(5);
        code.setGravity(Gravity.TOP | Gravity.START);
        code.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setPadding(dp(12), dp(10), dp(12), dp(10));
        code.setBackground(round(surface2, 12, 1, border));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, dp(12), 0, 0);
        box.addView(code, cp);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(Strings.text(language, "add_peer"))
                .setView(box).setNegativeButton(Strings.text(language, "close"), null)
                .setPositiveButton(Strings.text(language, "add_peer"), null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = code.getText().toString().trim();
            if (value.length() < 8 || value.length() > 4096) {
                toast(Strings.text(language, "invalid_contact"));
                return;
            }
            YggNodeService.addPeer(this, value);
            dialog.dismiss();
            toast(Strings.text(language, "peer_added"));
        }));
        dialog.show();
    }

    private void shareMyCode() {
        String code = myCode.isEmpty() ? forum.myCode() : myCode;
        if (code.isEmpty()) {
            toast(Strings.text(language, "connecting"));
            YggNodeService.sync(this);
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, code);
        startActivity(Intent.createChooser(send, Strings.text(language, "share_code")));
    }

    private void confirmPurge() {
        new AlertDialog.Builder(this).setTitle(Strings.text(language, "memory"))
                .setMessage(Strings.text(language, "purge_confirm"))
                .setNegativeButton(Strings.text(language, "close"), null)
                .setPositiveButton(Strings.text(language, "purge"), (dialog, which) -> YggNodeService.purgeShards(this)).show();
    }

    private LinearLayout buildDrawer() {
        LinearLayout panel = column();
        panel.setBackgroundColor(surface);
        panel.setElevation(dp(12));
        panel.setPadding(dp(14), dp(16), dp(14), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        View logo = new View(this);
        logo.setBackground(round(accent, 10, 0, Color.TRANSPARENT));
        header.addView(logo, new LinearLayout.LayoutParams(dp(10), dp(10)));
        TextView name = label(Strings.text(language, "app"), 15, textColor, true);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        np.setMargins(dp(8), 0, 0, 0);
        header.addView(name, np);
        TextView close = iconButton("×", 22);
        close.setOnClickListener(v -> closeDrawer());
        header.addView(close, new LinearLayout.LayoutParams(dp(38), dp(38)));
        panel.addView(header);

        TextView section = label("Q&A", 11, muted, true);
        section.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sectionParams.setMargins(dp(6), dp(28), dp(6), dp(8));
        panel.addView(section, sectionParams);
        panel.addView(drawerItem("❓", Strings.text(language, "ask"), PAGE_ASK), drawerItemParams());
        panel.addView(drawerItem("✦", Strings.text(language, "answer"), PAGE_FEED), drawerItemParams());
        panel.addView(drawerItem("⚙", Strings.text(language, "settings"), PAGE_SETTINGS), drawerItemParams());

        View divider = new View(this);
        divider.setBackgroundColor(border);
        LinearLayout.LayoutParams divp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        divp.setMargins(dp(4), dp(12), dp(4), dp(12));
        panel.addView(divider, divp);
        TextView foot = label(Strings.text(language, "unanswered_ttl"), 11, muted, false);
        foot.setPadding(dp(6), dp(2), dp(6), dp(4));
        panel.addView(foot);
        Space spacer = new Space(this);
        panel.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        View bottomDivider = new View(this);
        bottomDivider.setBackgroundColor(border);
        LinearLayout.LayoutParams bottomDivParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        bottomDivParams.setMargins(dp(4), dp(10), dp(4), dp(8));
        panel.addView(bottomDivider, bottomDivParams);
        panel.addView(themeDrawerItem()); // deliberately the last row in the sidebar
        return panel;
    }

    private View drawerItem(String icon, String title, String destination) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        boolean active = (PAGE_ASK.equals(destination) && PAGE_ASK.equals(page))
                || (PAGE_FEED.equals(destination) && (PAGE_FEED.equals(page) || PAGE_DETAIL.equals(page)))
                || (PAGE_SETTINGS.equals(destination) && PAGE_SETTINGS.equals(page));
        row.setBackground(round(active ? accentSoft : surface, 11, 0, Color.TRANSPARENT));
        TextView ico = label(icon, 16, active ? accent : muted, false);
        ico.setGravity(Gravity.CENTER);
        row.addView(ico, new LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView label = label(title, 14, active ? accent : textColor, active);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        textParams.setMargins(dp(8), 0, 0, 0);
        row.addView(label, textParams);
        row.setOnClickListener(v -> { page = destination; closeDrawer(); render(); });
        return row;
    }

    private View themeDrawerItem() {
        SharedPreferences prefs = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE);
        String mode = prefs.getString("theme", "system");
        String current = "system".equals(mode) ? Strings.text(language, "theme_system")
                : ("dark".equals(mode) ? Strings.text(language, "theme_dark") : Strings.text(language, "theme_light"));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setBackground(round(surface2, 11, 1, border));
        TextView icon = label("◐", 19, accent, true);
        row.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(40)));
        LinearLayout labels = column();
        labels.addView(label(Strings.text(language, "appearance"), 12, muted, false));
        TextView value = label(current, 14, textColor, true);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.setMargins(0, dp(2), 0, 0);
        labels.addView(value, vp);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setOnClickListener(v -> cycleTheme());
        row.setOnLongClickListener(v -> { setThemeMode("system"); return true; });
        return row;
    }

    private void cycleTheme() {
        String mode = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).getString("theme", "system");
        if ("system".equals(mode)) mode = dark ? "light" : "dark";
        else if ("dark".equals(mode)) mode = "light";
        else mode = "system";
        setThemeMode(mode);
    }

    private void setThemeMode(String mode) {
        getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).edit().putString("theme", mode).apply();
        applyPalette();
        drawerOpen = false;
        setContentView(buildRoot());
    }

    private void refreshDrawer() {
        if (root == null || drawer == null) return;
        root.removeView(drawer);
        drawer = buildDrawer();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(304), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        drawer.setTranslationX(drawerOpen ? 0 : -dp(304));
        root.addView(drawer, params);
    }

    private void openDrawer() {
        if (drawerOpen || drawer == null) return;
        drawerOpen = true;
        scrim.setVisibility(View.VISIBLE);
        drawer.setVisibility(View.VISIBLE);
        scrim.animate().alpha(1f).setDuration(180).start();
        drawer.animate().translationX(0).setDuration(220).start();
    }

    private void closeDrawer() {
        if (!drawerOpen || drawer == null) return;
        drawerOpen = false;
        scrim.animate().alpha(0f).setDuration(170).start();
        drawer.animate().translationX(-dp(304)).setDuration(210).withEndAction(() -> {
            if (!drawerOpen) { drawer.setVisibility(View.INVISIBLE); scrim.setVisibility(View.GONE); }
        }).start();
    }

    private View topicChips(String active, boolean compact, OnTopicSelected listener) {
        HorizontalScrollView scroll = horizontalScroll();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (Strings.Topic topic : Strings.topics()) {
            TextView chip = chip(topic.icon + " " + topic.name(language), topic.id.equals(active), compact);
            chip.setOnClickListener(v -> listener.onSelected(topic.id));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(compact ? 32 : 36));
            cp.setMargins(0, 0, dp(6), 0);
            row.addView(chip, cp);
        }
        scroll.addView(row);
        return scroll;
    }

    private View subtopicChips(Strings.Topic topic, String active, OnTopicSelected listener) {
        HorizontalScrollView scroll = horizontalScroll();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (Strings.Subtopic sub : topic.subtopics) {
            TextView chip = chip(sub.name(language), sub.id.equals(active), true);
            chip.setOnClickListener(v -> listener.onSelected(sub.id));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(31));
            cp.setMargins(0, 0, dp(6), 0);
            row.addView(chip, cp);
        }
        scroll.addView(row);
        return scroll;
    }

    private String topicTag(ForumCore.Question q) {
        if (!q.subtopic.isEmpty()) return Strings.subtopicName(language, q.topic, q.subtopic);
        return Strings.topicName(language, q.topic);
    }

    private String answerCountLabel(int count) {
        if ("ru".equals(language)) {
            int mod10 = count % 10, mod100 = count % 100;
            String word = mod10 == 1 && mod100 != 11 ? "ответ" : (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20) ? "ответа" : "ответов");
            return count + " " + word;
        }
        return count + " " + (count == 1 ? Strings.text(language, "answer_count_one") : Strings.text(language, "answers"));
    }

    private LinearLayout cardColumn() {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(round(surface2, 14, 1, border));
        return card;
    }

    private void addCard(LinearLayout parent, View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(10), 0, 0);
        parent.addView(card, params);
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(7));
        return params;
    }

    private TextView chip(String value, boolean active, boolean compact) {
        TextView view = label(value, compact ? 11 : 12, active ? Color.WHITE : (compact ? muted : textColor), active);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(compact ? 10 : 12), 0, dp(compact ? 10 : 12), 0);
        view.setSingleLine(true);
        view.setBackground(round(active ? accent : (compact ? surface : surface2), 18, 1, active ? accent : border));
        return view;
    }

    private TextView primaryButton(String value) {
        TextView button = label(value, 13, Color.WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(4), dp(12), dp(4));
        button.setBackground(round(accent, 12, 0, Color.TRANSPARENT));
        return button;
    }

    private TextView secondaryButton(String value) {
        TextView button = label(value, 12, textColor, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), dp(4), dp(8), dp(4));
        button.setBackground(round(surface2, 11, 1, border));
        return button;
    }

    private TextView iconButton(String value, int size) {
        TextView button = label(value, size, textColor, false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(round(surface, 10, 0, Color.TRANSPARENT));
        return button;
    }

    private LinearLayout column() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(true);
        return view;
    }

    private HorizontalScrollView horizontalScroll() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        return scroll;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams drawerItemParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        params.setMargins(0, 0, 0, dp(4));
        return params;
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) manager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
    private interface OnTopicSelected { void onSelected(String value); }
}
