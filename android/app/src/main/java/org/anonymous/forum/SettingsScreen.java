package org.anonymous.forum;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/** Settings screen: UI rendering and interaction wiring. */
final class SettingsScreen {
    private final MainActivity host;
    private final UiComponents ui;

    SettingsScreen(MainActivity host, UiComponents ui) { this.host = host; this.ui = ui; }

    LinearLayout build() {
        LinearLayout outer = ui.column();
        outer.setBackgroundColor(host.surface);
        ScrollView scroll = new ScrollView(host);
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(24));
        TextView title = ui.label(Strings.text(host.language, "settings"), 22, host.textColor, true);
        content.addView(title);

        LinearLayout wallet = ui.cardColumn();
        LinearLayout walletHeader = new LinearLayout(host);
        walletHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView walletLabel = ui.label("◈  " + Strings.text(host.language, "credits"), 14, host.muted, true);
        walletHeader.addView(walletLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ForumCore.Wallet balance = host.forum.wallet();
        TextView balanceText = ui.label(String.valueOf(balance.credits), 25, host.accent, true);
        walletHeader.addView(balanceText);
        wallet.addView(walletHeader);
        TextView free = ui.label(balance.freeQuestionsRemaining > 0 ? Strings.text(host.language, "free_left") : Strings.text(host.language, "free_used"), 13, host.textColor, true);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.setMargins(0, ui.dp(8), 0, 0);
        wallet.addView(free, fp);
        TextView rule = ui.label(Strings.text(host.language, "credit_rule"), 12, host.muted, false);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, ui.dp(6), 0, 0);
        wallet.addView(rule, rp);
        ui.addCard(content, wallet);

        View langButton = settingRow("🌐", Strings.text(host.language, "language"), selectedLanguageName());
        langButton.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        langButton.setBackground(ui.round(host.surface2, 14, 1, host.border));
        langButton.setOnClickListener(v -> showLanguageDialog());
        ui.addCard(content, langButton);

        LinearLayout networkCard = ui.cardColumn();
        LinearLayout networkHeader = new LinearLayout(host);
        networkHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView networkTitle = ui.label("◉  " + Strings.text(host.language, "network"), 15, host.textColor, true);
        networkHeader.addView(networkTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView nodeState = ui.label((host.online ? "●  " : "○  ") + host.networkStatus, 12, host.online ? host.accent : host.muted, true);
        nodeState.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        networkHeader.addView(nodeState);
        networkCard.addView(networkHeader);
        ui.addCard(content, networkCard);

        LinearLayout shards = ui.cardColumn();
        TextView memoryTitle = ui.label("◈  " + Strings.text(host.language, "memory"), 15, host.textColor, true);
        shards.addView(memoryTitle);
        TextView memoryHint = ui.label(Strings.text(host.language, "memory_hint"), 12, host.muted, false);
        LinearLayout.LayoutParams mhp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mhp.setMargins(0, ui.dp(6), 0, ui.dp(10));
        shards.addView(memoryHint, mhp);
        JSONObject cfg = parseConfig(host.configJson);
        addConfigLine(shards, Strings.text(host.language, "shard_size"), cfg.optString("ShardSize", "default"));
        addConfigLine(shards, Strings.text(host.language, "redundancy"), cfg.optString("Redundancy", "default"));
        addConfigLine(shards, Strings.text(host.language, "fanout"), cfg.optString("Fanout", "default"));
        addConfigLine(shards, Strings.text(host.language, "vault"), cfg.optString("VaultMax", "default"));
        addConfigLine(shards, Strings.text(host.language, "ttl"), cfg.optString("TTLHours", "default") + " h");
        TextView purge = ui.secondaryButton(Strings.text(host.language, "purge"));
        LinearLayout.LayoutParams purgeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(42));
        purgeParams.setMargins(0, ui.dp(10), 0, 0);
        shards.addView(purge, purgeParams);
        purge.setOnClickListener(v -> confirmPurge());
        ui.addCard(content, shards);

        TextView privacy = ui.label(Strings.text(host.language, "privacy"), 12, host.muted, false);
        privacy.setPadding(ui.dp(4), ui.dp(6), ui.dp(4), ui.dp(12));
        content.addView(privacy);
        TextView creditsNote = ui.label(Strings.text(host.language, "credits_local"), 12, host.muted, false);
        creditsNote.setPadding(ui.dp(4), ui.dp(2), ui.dp(4), ui.dp(12));
        content.addView(creditsNote);
        scroll.addView(content);
        outer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return outer;
    }

    private void addConfigLine(LinearLayout parent, String title, String value) {
        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView left = ui.label(title, 12, host.muted, false);
        row.addView(left, new LinearLayout.LayoutParams(0, ui.dp(25), 1f));
        TextView right = ui.label(value, 12, host.textColor, true);
        row.addView(right);
        parent.addView(row);
    }

    private JSONObject parseConfig(String json) {
        try { return new JSONObject(json == null ? "{}" : json); }
        catch (JSONException ignored) { return new JSONObject(); }
    }

    private View settingRow(String icon, String title, String value) {
        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView ico = ui.label(icon, 18, host.accent, false);
        row.addView(ico, new LinearLayout.LayoutParams(ui.dp(30), ui.dp(36)));
        LinearLayout text = ui.column();
        text.addView(ui.label(title, 14, host.textColor, true));
        TextView detail = ui.label(value, 11, host.muted, false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.setMargins(0, ui.dp(3), 0, 0);
        text.addView(detail, detailParams);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = ui.label("›", 24, host.muted, false);
        row.addView(arrow);
        return row;
    }

    private String selectedLanguageName() {
        String selected = Strings.languagePreference(host);
        for (Strings.Language item : Strings.languages()) if (item.code.equals(selected)) {
            return Strings.AUTO.equals(selected) ? Strings.text(host.language, "language_auto") : item.name;
        }
        return Strings.text(host.language, "language_auto");
    }

    private void showLanguageDialog() {
        List<Strings.Language> languages = Strings.languages();
        String[] names = new String[languages.size()];
        String selected = Strings.languagePreference(host);
        int checked = 0;
        for (int i = 0; i < languages.size(); i++) {
            Strings.Language item = languages.get(i);
            names[i] = Strings.AUTO.equals(item.code) ? Strings.text(host.language, "language_auto") : item.name;
            if (item.code.equals(selected)) checked = i;
        }
        new AlertDialog.Builder(host).setTitle(Strings.text(host.language, "language"))
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    Strings.setLanguagePreference(host, languages.get(which).code);
                    host.language = Strings.effectiveLanguage(host);
                    YggNodeService.sync(host);
                    dialog.dismiss();
                    host.refreshDrawer();
                    host.render();
                }).setNegativeButton(Strings.text(host.language, "close"), null).show();
    }

    private void confirmPurge() {
        new AlertDialog.Builder(host).setTitle(Strings.text(host.language, "memory"))
                .setMessage(Strings.text(host.language, "purge_confirm"))
                .setNegativeButton(Strings.text(host.language, "close"), null)
                .setPositiveButton(Strings.text(host.language, "purge"), (dialog, which) -> YggNodeService.purgeShards(host)).show();
    }
}
