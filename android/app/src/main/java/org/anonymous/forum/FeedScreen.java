package org.anonymous.forum;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

/** Feed screen: UI rendering and interaction wiring. */
final class FeedScreen {
    private final MainActivity host;
    private final UiComponents ui;

    FeedScreen(MainActivity host, UiComponents ui) { this.host = host; this.ui = ui; }

    LinearLayout build() {
        LinearLayout container = ui.column();
        container.setBackgroundColor(host.surface);
        LinearLayout header = ui.column();
        header.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(8));
        TextView title = ui.label(Strings.text(host.language, "answer"), 21, host.textColor, true);
        header.addView(title);
        TextView subtitle = ui.label((host.online ? "●  " : "○  ") + host.networkStatus, 12, host.muted, false);
        LinearLayout.LayoutParams subTitleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subTitleParams.setMargins(0, ui.dp(4), 0, ui.dp(8));
        header.addView(subtitle, subTitleParams);
        EditText search = new EditText(host);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setTextColor(host.textColor);
        search.setHintTextColor(host.muted);
        search.setHint(Strings.text(host.language, "search"));
        search.setPadding(ui.dp(12), ui.dp(9), ui.dp(12), ui.dp(9));
        search.setBackground(ui.round(host.surface2, 12, 1, host.border));
        search.setText(host.searchText);
        header.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(44)));
        HorizontalScrollView topicsScroll = ui.horizontalScroll();
        LinearLayout topicsRow = new LinearLayout(host);
        topicsRow.setGravity(Gravity.CENTER_VERTICAL);
        for (Strings.Topic topic : Strings.topics()) {
            TextView chip = ui.chip(topic.icon + " " + topic.name(host.language), topic.id.equals(host.filterTopic), false);
            chip.setOnClickListener(v -> {
                host.filterTopic = topic.id;
                host.filterSubtopic = "";
                host.render();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(36));
            cp.setMargins(0, 0, ui.dp(7), 0);
            topicsRow.addView(chip, cp);
        }
        topicsScroll.addView(topicsRow);
        LinearLayout.LayoutParams topicParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(44));
        topicParams.setMargins(0, ui.dp(8), 0, 0);
        header.addView(topicsScroll, topicParams);
        if (!"all".equals(host.filterTopic) && !Strings.topic(host.filterTopic).subtopics.isEmpty()) {
            HorizontalScrollView subScroll = ui.horizontalScroll();
            LinearLayout subRow = new LinearLayout(host);
            for (Strings.Subtopic sub : Strings.topic(host.filterTopic).subtopics) {
                TextView chip = ui.chip(sub.name(host.language), sub.id.equals(host.filterSubtopic), true);
                chip.setOnClickListener(v -> {
                    host.filterSubtopic = host.filterSubtopic.equals(sub.id) ? "" : sub.id;
                    host.render();
                });
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(32));
                cp.setMargins(0, 0, ui.dp(6), 0);
                subRow.addView(chip, cp);
            }
            subScroll.addView(subRow);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(40));
            sp.setMargins(0, ui.dp(4), 0, 0);
            header.addView(subScroll, sp);
        }
        container.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        host.feedList = ui.column();
        host.feedList.setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(12));
        ScrollView listScroll = new ScrollView(host);
        listScroll.setFillViewport(true);
        listScroll.addView(host.feedList);
        container.addView(listScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                host.searchText = s.toString();
                renderFeedList();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        renderFeedList();
        return container;
    }

    private void renderFeedList() {
        if (host.feedList == null) return;
        host.feedList.removeAllViews();
        String localId = host.forum.localId();
        String query = host.searchText.trim().toLowerCase(Locale.forLanguageTag(host.language));
        ArrayList<ForumCore.Question> filtered = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ForumCore.Question q : host.forum.questions()) {
            if (localId.equals(q.authorId)) continue;
            if (q.answerCount == 0 && q.expiresAt <= now) continue;
            if (!"all".equals(host.filterTopic) && !host.filterTopic.equals(q.topic)) continue;
            if (!host.filterSubtopic.isEmpty() && !host.filterSubtopic.equals(q.subtopic)) continue;
            if (!query.isEmpty() && !q.text.toLowerCase(Locale.forLanguageTag(host.language)).contains(query)) continue;
            filtered.add(q);
        }
        if (filtered.isEmpty()) {
            LinearLayout empty = ui.cardColumn();
            TextView icon = ui.label("✦", 28, host.accent, true); icon.setGravity(Gravity.CENTER);
            empty.addView(icon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(44)));
            TextView title = ui.label(query.isEmpty() ? Strings.text(host.language, "empty_title") : Strings.text(host.language, "no_matches"), 17, host.textColor, true);
            title.setGravity(Gravity.CENTER);
            empty.addView(title);
            TextView hint = ui.label(query.isEmpty() ? Strings.text(host.language, "empty_feed") : Strings.text(host.language, "network_hint"), 13, host.muted, false);
            hint.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.setMargins(0, ui.dp(6), 0, 0);
            empty.addView(hint, hp);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ep.setMargins(ui.dp(2), ui.dp(26), ui.dp(2), 0);
            host.feedList.addView(empty, ep);
            return;
        }
        for (ForumCore.Question q : filtered) host.feedList.addView(questionCard(q), ui.cardParams());
        TextView ttl = ui.label(Strings.text(host.language, "unanswered_ttl"), 11, host.muted, false);
        ttl.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.setMargins(0, ui.dp(8), 0, ui.dp(8));
        host.feedList.addView(ttl, tp);
    }

    private View questionCard(ForumCore.Question q) {
        LinearLayout card = ui.cardColumn();
        card.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        TextView title = ui.label(q.text, 15, host.textColor, false);
        title.setLineSpacing(ui.dp(2), 1f);
        card.addView(title);
        LinearLayout meta = new LinearLayout(host);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(0, ui.dp(9), 0, 0);
        TextView topic = ui.chip(ui.topicTag(q), false, true);
        topic.setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3));
        meta.addView(topic, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(25)));
        TextView stats = ui.label("  " + ui.answerCountLabel(q.answerCount) + "   ·   ◉ " + q.views + "   ·   " + ForumCore.timeAgo(q.createdAt, host.language), 11, host.muted, false);
        stats.setSingleLine(true);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        statsParams.setMargins(ui.dp(4), 0, 0, 0);
        meta.addView(stats, statsParams);
        card.addView(meta);
        card.setOnClickListener(v -> host.openQuestion(q.id));
        card.setClickable(true);
        return card;
    }
}
