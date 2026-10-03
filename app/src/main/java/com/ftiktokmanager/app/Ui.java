package com.ftiktokmanager.app;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;
import java.util.function.Consumer;

/** Small UI helpers shared by several screens. */
final class Ui {
    private Ui() {}

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static void promptText(Activity a, String title, String initial, Consumer<String> onOk) {
        final EditText et = new EditText(a);
        et.setSingleLine(true);
        et.setText(initial);
        et.setSelection(initial.length());
        FrameLayout box = new FrameLayout(a);
        int p = dp(a, 24);
        box.setPadding(p, dp(a, 8), p, 0);
        box.addView(et, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(a)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (!n.isEmpty()) onOk.accept(n);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    interface TwoFieldResult {
        void onResult(String first, String second);
    }

    static void promptTwo(Activity a, String title, String hint1, String hint2, TwoFieldResult cb) {
        final EditText e1 = new EditText(a);
        e1.setSingleLine(true);
        e1.setHint(hint1);
        final EditText e2 = new EditText(a);
        e2.setSingleLine(true);
        e2.setHint(hint2);
        e2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 24);
        box.setPadding(p, dp(a, 8), p, 0);
        box.addView(e1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(e2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(a)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Save", (d, w) -> cb.onResult(e1.getText().toString().trim(),
                        e2.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Opens a URL inside an account; asks which account when there is more than one. */
    static void openUrlInAccount(final Activity a, final String url) {
        App.io(() -> {
            final List<CloneModel> list = App.db().getAllAccounts();
            App.ui(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                if (list.isEmpty()) {
                    android.widget.Toast.makeText(a, "Add an account first", android.widget.Toast.LENGTH_SHORT).show();
                } else if (list.size() == 1) {
                    launch(a, list.get(0), url);
                } else {
                    String[] names = new String[list.size()];
                    for (int i = 0; i < names.length; i++) names[i] = list.get(i).name;
                    new MaterialAlertDialogBuilder(a)
                            .setTitle("Open in which account?")
                            .setItems(names, (d, which) -> launch(a, list.get(which), url))
                            .show();
                }
            });
        });
    }

    static void launch(Activity a, CloneModel m, String url) {
        android.content.Intent i = new android.content.Intent(a, WebActivity.class);
        i.putExtra(WebActivity.EXTRA_ID, m.id);
        i.putExtra(WebActivity.EXTRA_NAME, m.name);
        if (url != null) i.putExtra(WebActivity.EXTRA_URL, url);
        a.startActivity(i);
    }
}
