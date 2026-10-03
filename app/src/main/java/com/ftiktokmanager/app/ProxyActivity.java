package com.ftiktokmanager.app;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;

/** 🌐 PROXY page: add SOCKS5 proxies, pick the one all clones use, edit / delete. */
public class ProxyActivity extends AppCompatActivity {
    private LinearLayout listBox;
    private TextView txtEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg_main));
        root.setFitsSystemWindows(true);

        // header: back + title
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(getColor(R.color.icon_primary));
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        TextView title = new TextView(this);
        title.setText("\uD83C\uDF10  PROXY");
        title.setTextSize(22);
        title.setTextColor(getColor(R.color.text_primary));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = Ui.dp(this, 12);
        header.addView(title, tlp);
        root.addView(header);

        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        content.setPadding(pad, Ui.dp(this, 4), pad, pad);
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView info = new TextView(this);
        info.setText("SOCKS5 proxy only. Add proxies here, then tap one to use it for ALL clones. "
                + "A clone that has its own proxy (long-press the clone \u2192 SOCKS5 Proxy) keeps using its own."
                + "\n\nWith a proxy active, clones never fall back to the phone's mobile connection.");
        info.setTextSize(13);
        info.setTextColor(getColor(R.color.text_secondary));
        content.addView(info);

        MaterialButton add = new MaterialButton(this);
        add.setText("\u2795  Add proxy");
        add.setAllCaps(false);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = Ui.dp(this, 16);
        add.setOnClickListener(v -> editProxy(null));
        content.addView(add, alp);

        TextView section = new TextView(this);
        section.setText("SAVED PROXIES");
        section.setTextSize(12);
        section.setLetterSpacing(0.15f);
        section.setTextColor(getColor(R.color.primary));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(this, 24);
        slp.bottomMargin = Ui.dp(this, 8);
        content.addView(section, slp);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(listBox);

        txtEmpty = new TextView(this);
        txtEmpty.setText("No proxy saved yet. Tap \"Add proxy\".");
        txtEmpty.setTextColor(getColor(R.color.text_secondary));
        content.addView(txtEmpty);

        setContentView(root);
        refresh();
    }

    private void refresh() {
        listBox.removeAllViews();
        final List<ProxyStore.Item> items = ProxyStore.load(this);
        final int active = ProxyStore.activeId(this);
        txtEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        for (final ProxyStore.Item it : items) {
            final boolean isActive = it.id == active;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            int p = Ui.dp(this, 14);
            card.setPadding(p, p, p, Ui.dp(this, 6));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(getColor(R.color.card));
            bg.setCornerRadius(Ui.dp(this, 16));
            bg.setStroke(Ui.dp(this, isActive ? 2 : 1), isActive ? getColor(R.color.success) : getColor(R.color.card_stroke));
            card.setBackground(bg);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.bottomMargin = Ui.dp(this, 10);

            TextView name = new TextView(this);
            name.setText((isActive ? "\uD83D\uDFE2  " : "\u26AA  ") + it.label());
            name.setTextSize(17);
            name.setTextColor(getColor(R.color.text_primary));
            card.addView(name);

            TextView sub = new TextView(this);
            sub.setText((it.user.isEmpty() ? "No username" : "User: " + it.user)
                    + (isActive ? "  \u2022  ACTIVE for all clones" : "  \u2022  tap to use for all clones"));
            sub.setTextSize(13);
            sub.setTextColor(isActive ? getColor(R.color.success) : getColor(R.color.text_secondary));
            card.addView(sub);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.END);
            MaterialButton edit = textBtn("\u270F\uFE0F Edit");
            edit.setOnClickListener(v -> editProxy(it));
            MaterialButton del = textBtn("\uD83D\uDDD1 Delete");
            del.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                    .setTitle("Delete " + it.label() + "?")
                    .setPositiveButton("Delete", (d, w) -> {
                        ProxyStore.delete(this, it.id);
                        refresh();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
            actions.addView(edit);
            actions.addView(del);
            card.addView(actions);

            card.setOnClickListener(v -> {
                if (isActive) {
                    ProxyStore.setActive(this, 0);
                    Toast.makeText(this, "\uD83C\uDF10 Proxy OFF - clones use their own proxy or no proxy", Toast.LENGTH_SHORT).show();
                } else {
                    ProxyStore.setActive(this, it.id);
                    Toast.makeText(this, "\uD83C\uDF10 " + it.label() + " is now used for all clones", Toast.LENGTH_SHORT).show();
                }
                refresh();
            });
            listBox.addView(card, clp);
        }
    }

    private MaterialButton textBtn(String t) {
        MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(t);
        b.setAllCaps(false);
        return b;
    }

    private void editProxy(final ProxyStore.Item it) {
        ProxyDialog.show(this, it == null ? "Add SOCKS5 proxy" : "Edit SOCKS5 proxy",
                "SOCKS5 proxy only. Tap Test to check it before saving.",
                it == null ? "" : it.host, it == null ? 8080 : it.port,
                it == null ? "" : it.user, it == null ? "" : it.pass, false, false, null,
                (h, p, u, pw, on) -> {
                    if (h.isEmpty()) {
                        Toast.makeText(this, "Pehle proxy ka Host likho", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (it == null) ProxyStore.add(this, h, p, u, pw);
                    else ProxyStore.update(this, it.id, h, p, u, pw);
                    Toast.makeText(this, "\uD83C\uDF10 Proxy saved", Toast.LENGTH_SHORT).show();
                    refresh();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (listBox != null) refresh();
    }
}
