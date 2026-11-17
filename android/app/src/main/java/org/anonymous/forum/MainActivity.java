package org.anonymous.forum;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small, code-built Android UI. The UI depends on ForumCore, never directly on JNI. */
public final class MainActivity extends Activity {
    static final String PAGE_ASK = "ask";
    static final String PAGE_FEED = "feed";
    static final String PAGE_DETAIL = "detail";
    static final String PAGE_SETTINGS = "settings";
    static final String SETTINGS_PREFS = "forum_settings";

    ForumCore forum;
    UiComponents ui;
    AskScreen askScreen;
    FeedScreen feedScreen;
    DetailScreen detailScreen;
    SettingsScreen settingsScreen;
    SidebarPanel sidebarPanel;
    private FrameLayout root;
    private LinearLayout appFrame;
    private LinearLayout drawer;
    private View scrim;
    private boolean drawerOpen;

    String page = PAGE_ASK;
    String selectedQuestionId = "";
    String askTopic = "all";
    String askSubtopic = "";
    String filterTopic = "all";
    String filterSubtopic = "";
    String searchText = "";
    String questionDraft = "";
    String answerDraft = "";
    String language;
    String networkStatus = "";
    String configJson = "{}";
    boolean online;

    private int bg;
    int surface;
    int surface2;
    int border;
    int textColor;
    int muted;
    int accent = Color.rgb(16, 185, 129);
    int accentSoft;
    boolean dark;

    LinearLayout feedList;
    LinearLayout askTopicPanel;
    EditText askInput;
    EditText answerInput;
    LinearLayout answerComposer;

    private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!YggNodeService.ACTION_STATUS.equals(intent.getAction())) return;
            online = intent.getBooleanExtra("online", false);
            networkStatus = intent.getStringExtra("status");
            if (networkStatus == null) networkStatus = "";
            configJson = intent.getStringExtra("configJson");
            if (configJson == null) configJson = "{}";
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
        ui = new UiComponents(this);
        askScreen = new AskScreen(this, ui);
        feedScreen = new FeedScreen(this, ui);
        detailScreen = new DetailScreen(this, ui);
        settingsScreen = new SettingsScreen(this, ui);
        sidebarPanel = new SidebarPanel(this, ui);
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

        drawer = sidebarPanel.build();
        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(ui.dp(304), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        drawer.setTranslationX(-ui.dp(304));
        root.addView(drawer, drawerParams);
        render();
        return root;
    }

    void render() {
        if (appFrame == null) return;
        appFrame.removeAllViews();
        appFrame.addView(buildToolbar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(60)));
        View body;
        if (PAGE_FEED.equals(page)) body = feedScreen.build();
        else if (PAGE_DETAIL.equals(page)) body = detailScreen.build();
        else if (PAGE_SETTINGS.equals(page)) body = settingsScreen.build();
        else body = askScreen.build();
        appFrame.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private View buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(ui.dp(8), 0, ui.dp(12), 0);
        bar.setBackgroundColor(surface);
        TextView menu = ui.iconButton("☰", 22);
        menu.setContentDescription(Strings.text(language, "menu"));
        menu.setOnClickListener(v -> openDrawer());
        bar.addView(menu, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)));

        View dot = new View(this);
        dot.setBackground(ui.round(accent, 8, 0, Color.TRANSPARENT));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(ui.dp(8), ui.dp(8));
        dotParams.setMargins(ui.dp(5), 0, ui.dp(8), 0);
        bar.addView(dot, dotParams);
        TextView title = ui.label(pageTitle(), 17, textColor, true);
        title.setSingleLine(true);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView network = ui.label(online ? "●" : "○", 15, online ? accent : muted, true);
        network.setGravity(Gravity.CENTER);
        network.setOnClickListener(v -> { page = PAGE_SETTINGS; render(); });
        bar.addView(network, new LinearLayout.LayoutParams(ui.dp(30), ui.dp(40)));
        ForumCore.Wallet wallet = forum.wallet();
        String creditText = wallet.freeQuestionsRemaining > 0
                ? Strings.text(language, "credit_badge_free") + " · " + wallet.credits
                : wallet.credits + " " + Strings.text(language, "credit_badge");
        TextView credits = ui.label("◈ " + creditText, 12, accent, true);
        credits.setGravity(Gravity.CENTER);
        credits.setPadding(ui.dp(8), ui.dp(5), ui.dp(8), ui.dp(5));
        credits.setBackground(ui.round(accentSoft, 18, 0, Color.TRANSPARENT));
        credits.setOnClickListener(v -> { page = PAGE_SETTINGS; render(); });
        bar.addView(credits, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(34)));
        return bar;
    }

    private String pageTitle() {
        if (PAGE_FEED.equals(page)) return Strings.text(language, "answer");
        if (PAGE_DETAIL.equals(page)) return Strings.text(language, "question");
        if (PAGE_SETTINGS.equals(page)) return Strings.text(language, "settings");
        return Strings.text(language, "ask");
    }

    void sendQuestion() {
        String draft = questionDraft.trim();
        if (draft.isEmpty()) return;
        try {
            forum.postQuestion(draft, askTopic, askSubtopic);
            questionDraft = "";
            if (askInput != null) askInput.setText("");
            askTopic = "all";
            askSubtopic = "";
            ui.toast(Strings.text(language, "question_posted"));
            YggNodeService.sync(this);
            render();
        } catch (ForumCore.RuleException error) {
            if ("NO_CREDITS".equals(error.code)) ui.toast(Strings.text(language, "no_credits"));
            else ui.toast(Strings.text(language, "write_first"));
        }
    }

    void openQuestion(String id) {
        selectedQuestionId = id;
        forum.recordView(id);
        page = PAGE_DETAIL;
        answerDraft = "";
        ui.hideKeyboard();
        render();
    }

    void sendAnswer(String questionId) {
        String draft = answerDraft.trim();
        if (draft.isEmpty()) return;
        try {
            forum.answer(questionId, draft);
            answerDraft = "";
            ui.toast(Strings.text(language, "answer_posted"));
            YggNodeService.sync(this);
            render();
        } catch (ForumCore.RuleException error) {
            switch (error.code) {
                case "EMPTY_ANSWER": ui.toast(Strings.text(language, "write_answer_first")); break;
                case "NO_REPLY_ROUTE": ui.toast(Strings.text(language, "no_reply_route")); break;
                case "ALREADY_ANSWERED": ui.toast(Strings.text(language, "your_answer")); break;
                case "EXPIRED": ui.toast(Strings.text(language, "expired")); break;
                default: ui.toast(Strings.text(language, "empty_feed"));
            }
        }
    }

    void cycleTheme() {
        String mode = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).getString("theme", "system");
        if ("system".equals(mode)) mode = dark ? "light" : "dark";
        else if ("dark".equals(mode)) mode = "light";
        else mode = "system";
        setThemeMode(mode);
    }

    void setThemeMode(String mode) {
        getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).edit().putString("theme", mode).apply();
        applyPalette();
        drawerOpen = false;
        setContentView(buildRoot());
    }

    void refreshDrawer() {
        if (root == null || drawer == null) return;
        root.removeView(drawer);
        drawer = sidebarPanel.build();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ui.dp(304), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        drawer.setTranslationX(drawerOpen ? 0 : -ui.dp(304));
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

    void closeDrawer() {
        if (!drawerOpen || drawer == null) return;
        drawerOpen = false;
        scrim.animate().alpha(0f).setDuration(170).start();
        drawer.animate().translationX(-ui.dp(304)).setDuration(210).withEndAction(() -> {
            if (!drawerOpen) { drawer.setVisibility(View.INVISIBLE); scrim.setVisibility(View.GONE); }
        }).start();
    }

}
