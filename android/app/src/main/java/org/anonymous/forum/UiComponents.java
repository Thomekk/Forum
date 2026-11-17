package org.anonymous.forum;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Shared, presentation-only widgets and styling helpers used by every screen. */
final class UiComponents {
    interface TopicListener { void onSelected(String value); }

    private final MainActivity host;

    UiComponents(MainActivity host) { this.host = host; }

    LinearLayout column() {
        LinearLayout result = new LinearLayout(host);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }

    TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(host);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(true);
        return view;
    }

    GradientDrawable round(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    HorizontalScrollView horizontalScroll() {
        HorizontalScrollView scroll = new HorizontalScrollView(host);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        return scroll;
    }

    LinearLayout cardColumn() {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(round(host.surface2, 14, 1, host.border));
        return card;
    }

    void addCard(LinearLayout parent, View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(10), 0, 0);
        parent.addView(card, params);
    }

    LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(7));
        return params;
    }

    TextView chip(String value, boolean active, boolean compact) {
        TextView view = label(value, compact ? 11 : 12,
                active ? Color.WHITE : (compact ? host.muted : host.textColor), active);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(compact ? 10 : 12), 0, dp(compact ? 10 : 12), 0);
        view.setSingleLine(true);
        view.setBackground(round(active ? host.accent : (compact ? host.surface : host.surface2),
                18, 1, active ? host.accent : host.border));
        return view;
    }

    TextView primaryButton(String value) {
        TextView button = label(value, 13, Color.WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(4), dp(12), dp(4));
        button.setBackground(round(host.accent, 12, 0, Color.TRANSPARENT));
        return button;
    }

    TextView secondaryButton(String value) {
        TextView button = label(value, 12, host.textColor, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), dp(4), dp(8), dp(4));
        button.setBackground(round(host.surface2, 11, 1, host.border));
        return button;
    }

    TextView iconButton(String value, int size) {
        TextView button = label(value, size, host.textColor, false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(round(host.surface, 10, 0, Color.TRANSPARENT));
        return button;
    }

    LinearLayout.LayoutParams drawerItemParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        params.setMargins(0, 0, 0, dp(4));
        return params;
    }

    View topicChips(String active, boolean compact, TopicListener listener) {
        HorizontalScrollView scroll = horizontalScroll();
        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (Strings.Topic topic : Strings.topics()) {
            TextView item = chip(topic.icon + " " + topic.name(host.language),
                    topic.id.equals(active), compact);
            item.setOnClickListener(v -> listener.onSelected(topic.id));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(compact ? 32 : 36));
            params.setMargins(0, 0, dp(6), 0);
            row.addView(item, params);
        }
        scroll.addView(row);
        return scroll;
    }

    View subtopicChips(Strings.Topic topic, String active, TopicListener listener) {
        HorizontalScrollView scroll = horizontalScroll();
        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (Strings.Subtopic sub : topic.subtopics) {
            TextView item = chip(sub.name(host.language), sub.id.equals(active), true);
            item.setOnClickListener(v -> listener.onSelected(sub.id));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(31));
            params.setMargins(0, 0, dp(6), 0);
            row.addView(item, params);
        }
        scroll.addView(row);
        return scroll;
    }

    String topicTag(ForumCore.Question q) {
        if (!q.subtopic.isEmpty()) return Strings.subtopicName(host.language, q.topic, q.subtopic);
        return Strings.topicName(host.language, q.topic);
    }

    String answerCountLabel(int count) {
        if ("ru".equals(host.language)) {
            int mod10 = count % 10, mod100 = count % 100;
            String word = mod10 == 1 && mod100 != 11 ? "ответ"
                    : (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)
                    ? "ответа" : "ответов");
            return count + " " + word;
        }
        return count + " " + (count == 1
                ? Strings.text(host.language, "answer_count_one")
                : Strings.text(host.language, "answers"));
    }

    int dp(int value) {
        return (int) (value * host.getResources().getDisplayMetrics().density + 0.5f);
    }

    void hideKeyboard() {
        View focus = host.getCurrentFocus();
        if (focus != null) {
            InputMethodManager manager = (InputMethodManager) host.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
    }

    void toast(String value) { Toast.makeText(host, value, Toast.LENGTH_SHORT).show(); }
}
