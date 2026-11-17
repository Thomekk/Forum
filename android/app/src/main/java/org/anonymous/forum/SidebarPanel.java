package org.anonymous.forum;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

/** Navigation drawer: UI rendering and interaction wiring. */
final class SidebarPanel {
    private final MainActivity host;
    private final UiComponents ui;

    SidebarPanel(MainActivity host, UiComponents ui) { this.host = host; this.ui = ui; }

    LinearLayout build() {
        LinearLayout panel = ui.column();
        panel.setBackgroundColor(host.surface);
        panel.setElevation(ui.dp(12));
        panel.setPadding(ui.dp(14), ui.dp(16), ui.dp(14), ui.dp(12));

        LinearLayout header = new LinearLayout(host);
        header.setGravity(Gravity.CENTER_VERTICAL);
        View logo = new View(host);
        logo.setBackground(ui.round(host.accent, 10, 0, Color.TRANSPARENT));
        header.addView(logo, new LinearLayout.LayoutParams(ui.dp(10), ui.dp(10)));
        TextView name = ui.label(Strings.text(host.language, "app"), 15, host.textColor, true);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        np.setMargins(ui.dp(8), 0, 0, 0);
        header.addView(name, np);
        TextView close = ui.iconButton("×", 22);
        close.setOnClickListener(v -> host.closeDrawer());
        header.addView(close, new LinearLayout.LayoutParams(ui.dp(38), ui.dp(38)));
        panel.addView(header);

        TextView section = ui.label("Q&A", 11, host.muted, true);
        section.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sectionParams.setMargins(ui.dp(6), ui.dp(28), ui.dp(6), ui.dp(8));
        panel.addView(section, sectionParams);
        panel.addView(drawerItem("❓", Strings.text(host.language, "ask"), MainActivity.PAGE_ASK), ui.drawerItemParams());
        panel.addView(drawerItem("✦", Strings.text(host.language, "answer"), MainActivity.PAGE_FEED), ui.drawerItemParams());
        panel.addView(drawerItem("⚙", Strings.text(host.language, "settings"), MainActivity.PAGE_SETTINGS), ui.drawerItemParams());

        View divider = new View(host);
        divider.setBackgroundColor(host.border);
        LinearLayout.LayoutParams divp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1));
        divp.setMargins(ui.dp(4), ui.dp(12), ui.dp(4), ui.dp(12));
        panel.addView(divider, divp);
        TextView foot = ui.label(Strings.text(host.language, "unanswered_ttl"), 11, host.muted, false);
        foot.setPadding(ui.dp(6), ui.dp(2), ui.dp(6), ui.dp(4));
        panel.addView(foot);
        Space spacer = new Space(host);
        panel.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        View bottomDivider = new View(host);
        bottomDivider.setBackgroundColor(host.border);
        LinearLayout.LayoutParams bottomDivParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1));
        bottomDivParams.setMargins(ui.dp(4), ui.dp(10), ui.dp(4), ui.dp(8));
        panel.addView(bottomDivider, bottomDivParams);
        panel.addView(themeDrawerItem()); // deliberately the last row in the sidebar
        return panel;
    }

    private View drawerItem(String icon, String title, String destination) {
        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        boolean active = (MainActivity.PAGE_ASK.equals(destination) && MainActivity.PAGE_ASK.equals(host.page))
                || (MainActivity.PAGE_FEED.equals(destination) && (MainActivity.PAGE_FEED.equals(host.page) || MainActivity.PAGE_DETAIL.equals(host.page)))
                || (MainActivity.PAGE_SETTINGS.equals(destination) && MainActivity.PAGE_SETTINGS.equals(host.page));
        row.setBackground(ui.round(active ? host.accentSoft : host.surface, 11, 0, Color.TRANSPARENT));
        TextView ico = ui.label(icon, 16, active ? host.accent : host.muted, false);
        ico.setGravity(Gravity.CENTER);
        row.addView(ico, new LinearLayout.LayoutParams(ui.dp(28), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView label = ui.label(title, 14, active ? host.accent : host.textColor, active);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        textParams.setMargins(ui.dp(8), 0, 0, 0);
        row.addView(label, textParams);
        row.setOnClickListener(v -> { host.page = destination; host.closeDrawer(); host.render(); });
        return row;
    }

    private View themeDrawerItem() {
        SharedPreferences prefs = host.getSharedPreferences(MainActivity.SETTINGS_PREFS, Context.MODE_PRIVATE);
        String mode = prefs.getString("theme", "system");
        String current = "system".equals(mode) ? Strings.text(host.language, "theme_system")
                : ("dark".equals(mode) ? Strings.text(host.language, "theme_dark") : Strings.text(host.language, "theme_light"));
        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        row.setBackground(ui.round(host.surface2, 11, 1, host.border));
        TextView icon = ui.label("◐", 19, host.accent, true);
        row.addView(icon, new LinearLayout.LayoutParams(ui.dp(30), ui.dp(40)));
        LinearLayout labels = ui.column();
        labels.addView(ui.label(Strings.text(host.language, "appearance"), 12, host.muted, false));
        TextView value = ui.label(current, 14, host.textColor, true);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.setMargins(0, ui.dp(2), 0, 0);
        labels.addView(value, vp);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setOnClickListener(v -> host.cycleTheme());
        row.setOnLongClickListener(v -> { host.setThemeMode("system"); return true; });
        return row;
    }
}
