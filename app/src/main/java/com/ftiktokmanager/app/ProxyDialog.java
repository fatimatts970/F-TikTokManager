package com.ftiktokmanager.app;

import android.app.Activity;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** SOCKS5 proxy editor with a "Test Connection" button (used for default + per-account proxy). */
final class ProxyDialog {
    interface Callback {
        void onSave(String host, int port, String user, String pass, boolean on);
    }

    private ProxyDialog() {}

    private static EditText field(Activity a, String hint, String value, int inputType) {
        EditText e = new EditText(a);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(inputType);
        return e;
    }

    static void show(final Activity a, String title, String help, String host, int port, String user,
                     String pass, boolean showUse, boolean useOn, String useLabel, final Callback cb) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 24);
        box.setPadding(pad, Ui.dp(a, 8), pad, 0);

        TextView tv = new TextView(a);
        tv.setText(help);
        tv.setTextSize(13);
        box.addView(tv);

        final EditText eHost = field(a, "Host (e.g. 1.2.3.4)", host, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText ePort = field(a, "Port (8080 if empty)", port > 0 ? String.valueOf(port) : "", InputType.TYPE_CLASS_NUMBER);
        final EditText eUser = field(a, "Username (optional)", user, InputType.TYPE_CLASS_TEXT);
        final EditText ePass = field(a, "Password (optional)", pass, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        box.addView(eHost, lp);
        box.addView(ePort, lp);
        box.addView(eUser, lp);
        box.addView(ePass, lp);

        final CheckBox use = new CheckBox(a);
        if (showUse) {
            use.setText(useLabel);
            use.setChecked(useOn);
            box.addView(use);
        }
        final TextView result = new TextView(a);
        result.setPadding(0, Ui.dp(a, 10), 0, 0);
        box.addView(result);

        ScrollView sv = new ScrollView(a);
        sv.addView(box);

        final AlertDialog dlg = new MaterialAlertDialogBuilder(a)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("Save", null)
                .setNeutralButton("🧪 Test", null)
                .setNegativeButton("Cancel", null)
                .create();
        dlg.show();

        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String h = eHost.getText().toString().trim();
            int p = parsePort(ePort.getText().toString());
            if (p < 0) {
                Toast.makeText(a, "Port ghalat hai", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean on = showUse && use.isChecked();
            if (on && h.isEmpty()) {
                Toast.makeText(a, "Pehle proxy ka Host likho", Toast.LENGTH_SHORT).show();
                return;
            }
            cb.onSave(h, p, eUser.getText().toString().trim(), ePass.getText().toString(), on);
            dlg.dismiss();
        });

        dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            final String h = eHost.getText().toString().trim();
            final int p = parsePort(ePort.getText().toString());
            if (h.isEmpty()) {
                result.setText("Pehle proxy ka Host likho");
                return;
            }
            if (p < 0) {
                result.setText("Port ghalat hai");
                return;
            }
            final String u = eUser.getText().toString().trim();
            final String pw = ePass.getText().toString();
            v.setEnabled(false);
            result.setText("Testing…");
            App.io(() -> {
                String r;
                try {
                    r = IpTool.test(h, p, u, pw);
                } catch (Exception e) {
                    r = "❌ Failed: " + e.getMessage();
                }
                final String text = r;
                App.ui(() -> {
                    if (a.isFinishing() || a.isDestroyed()) return;
                    result.setText(text);
                    v.setEnabled(true);
                });
            });
        });
    }

    /** empty -> 8080, invalid -> -1 */
    private static int parsePort(String s) {
        s = s.trim();
        if (s.isEmpty()) return 8080;
        try {
            int p = Integer.parseInt(s);
            return p > 0 && p <= 65535 ? p : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
