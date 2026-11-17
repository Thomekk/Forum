package org.anonymous.forum;

import android.graphics.Color;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Ask screen: UI rendering and interaction wiring. */
final class AskScreen {
    private final MainActivity host;
    private final UiComponents ui;

    AskScreen(MainActivity host, UiComponents ui) { this.host = host; this.ui = ui; }

    LinearLayout build() {
        LinearLayout pageView = ui.column();
        pageView.setBackgroundColor(host.surface);
        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(true);
        LinearLayout hero = ui.column();
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(ui.dp(28), ui.dp(28), ui.dp(28), ui.dp(24));
        TextView bubble = ui.label("💬", 34, host.accent, false);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(ui.round(host.accentSoft, 40, 0, Color.TRANSPARENT));
        hero.addView(bubble, new LinearLayout.LayoutParams(ui.dp(76), ui.dp(76)));
        TextView title = ui.label(Strings.text(host.language, "q_welcome"), 23, host.textColor, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, ui.dp(18), 0, ui.dp(8));
        hero.addView(title, titleParams);
        TextView description = ui.label(Strings.text(host.language, "q_description"), 14, host.muted, false);
        description.setGravity(Gravity.CENTER);
        hero.addView(description);

        ForumCore.Wallet wallet = host.forum.wallet();
        LinearLayout quota = ui.cardColumn();
        TextView quotaTitle = ui.label(wallet.freeQuestionsRemaining > 0
                ? Strings.text(host.language, "free_left") : Strings.text(host.language, "free_used"), 14, host.textColor, true);
        quota.addView(quotaTitle);
        TextView quotaInfo = ui.label(Strings.text(host.language, "credit_rule"), 12, host.muted, false);
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qp.setMargins(0, ui.dp(6), 0, 0);
        quota.addView(quotaInfo, qp);
        LinearLayout.LayoutParams qbox = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qbox.setMargins(0, ui.dp(24), 0, 0);
        hero.addView(quota, qbox);
        scroll.addView(hero, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pageView.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout composer = ui.column();
        composer.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12));
        composer.setBackground(ui.round(host.surface, 18, 1, host.border));
        host.askTopicPanel = ui.column();
        host.askTopicPanel.setVisibility(host.questionDraft.trim().isEmpty() ? View.GONE : View.VISIBLE);
        composer.addView(host.askTopicPanel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        updateAskTopicPanel();

        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.BOTTOM);
        host.askInput = new EditText(host);
        host.askInput.setTextColor(host.textColor);
        host.askInput.setHintTextColor(host.muted);
        host.askInput.setTextSize(15);
        host.askInput.setHint(Strings.text(host.language, "q_placeholder"));
        host.askInput.setGravity(Gravity.TOP | Gravity.START);
        host.askInput.setMinLines(1);
        host.askInput.setMaxLines(5);
        host.askInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(ForumCore.QUESTION_LIMIT)});
        host.askInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        host.askInput.setPadding(ui.dp(13), ui.dp(12), ui.dp(13), ui.dp(10));
        host.askInput.setBackground(ui.round(host.surface2, 14, 1, host.border));
        host.askInput.setText(host.questionDraft);
        row.addView(host.askInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView send = ui.label("↑", 24, Color.WHITE, true);
        send.setGravity(Gravity.CENTER);
        send.setBackground(ui.round(host.accent, 14, 0, Color.TRANSPARENT));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(ui.dp(46), ui.dp(46));
        sendParams.setMargins(ui.dp(8), 0, 0, 0);
        row.addView(send, sendParams);
        composer.addView(row);
        pageView.addView(composer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        host.askInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                host.questionDraft = s.toString();
                if (send != null) send.setAlpha(host.questionDraft.trim().isEmpty() ? 0.45f : 1f);
                if (host.askTopicPanel != null) {
                    host.askTopicPanel.setVisibility(host.questionDraft.trim().isEmpty() ? View.GONE : View.VISIBLE);
                    updateAskTopicPanel();
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        send.setAlpha(host.questionDraft.trim().isEmpty() ? 0.45f : 1f);
        send.setOnClickListener(v -> host.sendQuestion());
        return pageView;
    }

    private void updateAskTopicPanel() {
        if (host.askTopicPanel == null) return;
        host.askTopicPanel.removeAllViews();
        if (host.questionDraft.trim().isEmpty()) return;
        host.askTopicPanel.addView(ui.topicChips(host.askTopic, false, topic -> {
            host.askTopic = topic;
            host.askSubtopic = "";
            updateAskTopicPanel();
        }));
        Strings.Topic chosen = Strings.topic(host.askTopic);
        if (!chosen.subtopics.isEmpty()) {
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subParams.setMargins(0, ui.dp(5), 0, ui.dp(7));
            host.askTopicPanel.addView(ui.subtopicChips(chosen, host.askSubtopic, sub -> {
                host.askSubtopic = host.askSubtopic.equals(sub) ? "" : sub;
                updateAskTopicPanel();
            }), subParams);
        }
    }
}
