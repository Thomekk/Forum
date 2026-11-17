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

/** Detail screen: UI rendering and interaction wiring. */
final class DetailScreen {
    private final MainActivity host;
    private final UiComponents ui;

    DetailScreen(MainActivity host, UiComponents ui) { this.host = host; this.ui = ui; }

    LinearLayout build() {
        LinearLayout pageView = ui.column();
        pageView.setBackgroundColor(host.surface);
        ForumCore.Question q = host.forum.question(host.selectedQuestionId);
        if (q == null) {
            pageView.addView(backRow(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(48)));
            TextView missing = ui.label(Strings.text(host.language, "empty_title"), 17, host.muted, false);
            missing.setGravity(Gravity.CENTER);
            pageView.addView(missing, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return pageView;
        }
        ScrollView scroll = new ScrollView(host);
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(18));
        content.addView(backRow(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(44)));

        LinearLayout questionCard = ui.cardColumn();
        TextView topic = ui.chip(ui.topicTag(q), false, true);
        topic.setPadding(ui.dp(9), ui.dp(4), ui.dp(9), ui.dp(4));
        questionCard.addView(topic, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(28)));
        TextView questionText = ui.label(q.text, 19, host.textColor, true);
        questionText.setLineSpacing(ui.dp(3), 1f);
        LinearLayout.LayoutParams qtp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qtp.setMargins(0, ui.dp(12), 0, ui.dp(9));
        questionCard.addView(questionText, qtp);
        TextView metadata = ui.label(q.authorName + "  ·  " + ForumCore.timeAgo(q.createdAt, host.language)
                + "  ·  " + q.views + " " + Strings.text(host.language, "views"), 12, host.muted, false);
        questionCard.addView(metadata);
        LinearLayout.LayoutParams cardMargin = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardMargin.setMargins(0, ui.dp(4), 0, ui.dp(12));
        content.addView(questionCard, cardMargin);

        boolean own = host.forum.localId().equals(q.authorId);
        TextView count = ui.label(ui.answerCountLabel(q.answerCount), 15, host.textColor, true);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countParams.setMargins(ui.dp(2), ui.dp(4), 0, ui.dp(8));
        content.addView(count, countParams);
        TextView privacy = ui.label(Strings.text(host.language, "answer_private"), 12, host.muted, false);
        privacy.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        privacy.setBackground(ui.round(host.surface2, 12, 1, host.border));
        content.addView(privacy);

        if (own) {
            if (q.answers.isEmpty()) {
                TextView waiting = ui.label(Strings.text(host.language, "waiting"), 14, host.muted, false);
                waiting.setGravity(Gravity.CENTER);
                waiting.setPadding(ui.dp(12), ui.dp(28), ui.dp(12), ui.dp(28));
                content.addView(waiting);
            } else {
                for (ForumCore.Answer answer : q.answers) {
                    LinearLayout answerCard = ui.cardColumn();
                    TextView answerText = ui.label(answer.text, 15, host.textColor, false);
                    answerCard.addView(answerText);
                    TextView answerMeta = ui.label(answer.authorName + "  ·  " + ForumCore.timeAgo(answer.createdAt, host.language), 11, host.muted, false);
                    LinearLayout.LayoutParams amp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    amp.setMargins(0, ui.dp(8), 0, 0);
                    answerCard.addView(answerMeta, amp);
                    LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    ap.setMargins(0, ui.dp(8), 0, 0);
                    content.addView(answerCard, ap);
                }
                if (q.answerCount > q.answers.size()) {
                    TextView more = ui.label("+ " + (q.answerCount - q.answers.size()) + " " + Strings.text(host.language, "answer_private"), 12, host.muted, false);
                    more.setPadding(ui.dp(4), ui.dp(10), ui.dp(4), ui.dp(10));
                    content.addView(more);
                }
            }
        } else if (host.forum.hasAnswered(host.selectedQuestionId)) {
            TextView done = ui.label("✓  " + Strings.text(host.language, "your_answer") + "\n+1 " + Strings.text(host.language, "credits"), 14, host.accent, true);
            done.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14));
            done.setBackground(ui.round(host.accentSoft, 14, 0, Color.TRANSPARENT));
            LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            doneParams.setMargins(0, ui.dp(12), 0, 0);
            content.addView(done, doneParams);
        } else if (q.answerCount == 0 && q.expiresAt <= System.currentTimeMillis()) {
            TextView expired = ui.label(Strings.text(host.language, "expired"), 14, host.muted, false);
            expired.setPadding(0, ui.dp(18), 0, ui.dp(18));
            content.addView(expired);
        } else {
            TextView earn = ui.label(Strings.text(host.language, "earn_credit"), 12, host.accent, true);
            LinearLayout.LayoutParams earnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            earnParams.setMargins(0, ui.dp(14), 0, ui.dp(8));
            content.addView(earn, earnParams);
        }
        scroll.addView(content);
        pageView.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (!own && !host.forum.hasAnswered(q.id) && !(q.answerCount == 0 && q.expiresAt <= System.currentTimeMillis())) {
            host.answerComposer = ui.column();
            host.answerComposer.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12));
            host.answerComposer.setBackground(ui.round(host.surface, 18, 1, host.border));
            LinearLayout row = new LinearLayout(host);
            row.setGravity(Gravity.BOTTOM);
            host.answerInput = new EditText(host);
            host.answerInput.setTextColor(host.textColor); host.answerInput.setHintTextColor(host.muted);
            host.answerInput.setHint(Strings.text(host.language, "a_placeholder"));
            host.answerInput.setTextSize(15); host.answerInput.setMinLines(1); host.answerInput.setMaxLines(5);
            host.answerInput.setGravity(Gravity.TOP | Gravity.START);
            host.answerInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            host.answerInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(ForumCore.ANSWER_LIMIT)});
            host.answerInput.setPadding(ui.dp(13), ui.dp(12), ui.dp(13), ui.dp(10));
            host.answerInput.setBackground(ui.round(host.surface2, 14, 1, host.border));
            host.answerInput.setText(host.answerDraft);
            row.addView(host.answerInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView send = ui.label("↑", 24, Color.WHITE, true);
            send.setGravity(Gravity.CENTER);
            send.setBackground(ui.round(host.accent, 14, 0, Color.TRANSPARENT));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ui.dp(46), ui.dp(46));
            sp.setMargins(ui.dp(8), 0, 0, 0);
            row.addView(send, sp);
            host.answerComposer.addView(row);
            pageView.addView(host.answerComposer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            host.answerInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { host.answerDraft = s.toString(); send.setAlpha(host.answerDraft.trim().isEmpty() ? 0.45f : 1f); }
                @Override public void afterTextChanged(Editable s) { }
            });
            send.setAlpha(host.answerDraft.trim().isEmpty() ? 0.45f : 1f);
            send.setOnClickListener(v -> host.sendAnswer(q.id));
        } else {
            host.answerComposer = null; host.answerInput = null;
        }
        return pageView;
    }

    private View backRow() {
        TextView back = ui.label("‹  " + Strings.text(host.language, "back"), 14, host.accent, true);
        back.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        back.setOnClickListener(v -> { ui.hideKeyboard(); host.page = MainActivity.PAGE_FEED; host.answerDraft = ""; host.render(); });
        return back;
    }
}
