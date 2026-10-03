package com.ftiktokmanager.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.util.List;

/** Virtual camera picker + image adjust dialogs (built in code, no extra screens). */
final class VcamUi {
    private VcamUi() {}

    private static MaterialButton btn(Activity a, String text) {
        MaterialButton b = new MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 8);
        b.setLayoutParams(lp);
        return b;
    }

    /** Main dialog: mode, gallery images (+ add), switch physical/virtual, adjust. */
    static void show(final Activity a, final int accId, final Runnable addNew, final Runnable changed) {
        final SharedPreferences sp = a.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        final boolean virtual = sp.getBoolean("cam_virtual", false);
        final List<File> files = VcamImg.list(a, accId);
        final File sel = VcamImg.selected(sp, accId);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 20);
        root.setPadding(pad, Ui.dp(a, 8), pad, 0);

        TextView info = new TextView(a);
        info.setText("Camera mode applies to the whole app:\n\uD83D\uDCF7 Physical = real camera  \u2022  \uD83C\uDFAD Virtual = gallery image"
                + "\n\nTap: select image (turns Virtual ON)  \u2022  Long-press: delete");
        info.setTextColor(a.getColor(R.color.text_secondary));
        info.setTextSize(13);
        root.addView(info);

        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 4));
        hs.addView(row);
        root.addView(hs);

        final int tile = Ui.dp(a, 84);

        // "+" tile
        TextView plus = new TextView(a);
        plus.setText("+");
        plus.setGravity(Gravity.CENTER);
        plus.setTextSize(34);
        plus.setTextColor(a.getColor(R.color.primary));
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(a.getColor(R.color.chip_bg));
        pbg.setCornerRadius(Ui.dp(a, 12));
        plus.setBackground(pbg);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(tile, tile);
        plp.rightMargin = Ui.dp(a, 8);
        row.addView(plus, plp);

        final AlertDialog[] dlg = new AlertDialog[1];

        plus.setOnClickListener(v -> {
            dlg[0].dismiss();
            addNew.run();
        });

        for (final File f : files) {
            FrameLayout cell = new FrameLayout(a);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Ui.dp(a, 12));
            boolean isSel = sel != null && sel.getName().equals(f.getName());
            bg.setColor(isSel ? a.getColor(R.color.primary) : Color.TRANSPARENT);
            cell.setBackground(bg);
            int p = Ui.dp(a, 3);
            cell.setPadding(p, p, p, p);
            final ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(Color.parseColor("#DDDDDD"));
            cell.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(tile, tile);
            clp.rightMargin = Ui.dp(a, 8);
            row.addView(cell, clp);

            App.io(() -> {
                final Bitmap th = VcamImg.load(f, 220);
                App.ui(() -> {
                    if (th != null && !a.isFinishing()) iv.setImageBitmap(th);
                });
            });

            cell.setOnClickListener(v -> {
                VcamImg.select(sp, accId, f);
                sp.edit().putBoolean("cam_virtual", true).apply();
                Toast.makeText(a, "\uD83C\uDFAD Image selected - Virtual Camera ON", Toast.LENGTH_SHORT).show();
                dlg[0].dismiss();
                changed.run();
                show(a, accId, addNew, changed);
            });
            cell.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(a)
                        .setTitle("Delete this image?")
                        .setPositiveButton("Delete", (d, w) -> {
                            if (sel != null && sel.getName().equals(f.getName())) {
                                sp.edit().remove("active_vcam_uri_" + accId).apply();
                            }
                            VcamImg.delete(sp, f);
                            dlg[0].dismiss();
                            changed.run();
                            show(a, accId, addNew, changed);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });
        }

        MaterialButton bSwitch = btn(a, virtual ? "Switch to \uD83D\uDCF7 Physical camera (whole app)"
                : "Turn \uD83C\uDFAD Virtual camera ON (whole app)");
        bSwitch.setOnClickListener(v -> {
            boolean now = !virtual;
            sp.edit().putBoolean("cam_virtual", now).apply();
            Toast.makeText(a, now
                    ? (sel == null ? "\uD83C\uDFAD Virtual ON - pehle ek image add karo" : "\uD83C\uDFAD Virtual camera ON")
                    : "\uD83D\uDCF7 Physical camera ON", Toast.LENGTH_SHORT).show();
            dlg[0].dismiss();
            changed.run();
        });
        root.addView(bSwitch);

        MaterialButton bAdjust = btn(a, "\uD83C\uDFA8 Adjust image (rotate / zoom / set)");
        bAdjust.setOnClickListener(v -> {
            if (sel == null) {
                Toast.makeText(a, "Pehle ek image select karo", Toast.LENGTH_SHORT).show();
                return;
            }
            dlg[0].dismiss();
            adjust(a, sp, sel, () -> show(a, accId, addNew, changed));
        });
        root.addView(bAdjust);

        ScrollView sv = new ScrollView(a);
        sv.addView(root);

        dlg[0] = new MaterialAlertDialogBuilder(a)
                .setTitle("\uD83C\uDFAD Virtual Camera (" + (virtual ? "VIRTUAL \uD83C\uDFAD" : "PHYSICAL \uD83D\uDCF7") + ")")
                .setView(sv)
                .setNegativeButton("Close", null)
                .create();
        dlg[0].show();
    }

    // ------------------------------------------------------------ adjust

    static void adjust(final Activity a, final SharedPreferences sp, final File f, final Runnable done) {
        final float[] adj = VcamImg.getAdj(sp, f);
        final AdjustView view = new AdjustView(a, adj);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), 0);

        TextView hint = new TextView(a);
        hint.setText("Drag = move  \u2022  Pinch = zoom");
        hint.setTextColor(a.getColor(R.color.text_secondary));
        hint.setTextSize(13);
        root.addView(hint);

        root.addView(view, new LinearLayout.LayoutParams(Ui.dp(a, 225), Ui.dp(a, 300)));
        ((LinearLayout.LayoutParams) view.getLayoutParams()).topMargin = Ui.dp(a, 8);

        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        String[] labels = {"\u27F2", "\u27F3", "\u2212", "\uFF0B", "Reset"};
        for (final String l : labels) {
            MaterialButton b = new MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setText(l);
            b.setAllCaps(false);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 10), 0);
            b.setOnClickListener(v -> {
                if (l.equals("\u27F2")) adj[0] -= 90;
                else if (l.equals("\u27F3")) adj[0] += 90;
                else if (l.equals("\u2212")) adj[1] = Math.max(0.5f, adj[1] / 1.1f);
                else if (l.equals("\uFF0B")) adj[1] = Math.min(6f, adj[1] * 1.1f);
                else { adj[0] = 0; adj[1] = 1; adj[2] = 0; adj[3] = 0; }
                view.invalidate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Ui.dp(a, 3);
            lp.rightMargin = Ui.dp(a, 3);
            bar.addView(b, lp);
        }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(a, 10);
        root.addView(bar, blp);

        ScrollView sv = new ScrollView(a);
        sv.addView(root);

        App.io(() -> {
            final Bitmap bmp = VcamImg.load(f, 1200);
            App.ui(() -> {
                if (a.isFinishing()) return;
                if (bmp == null) {
                    Toast.makeText(a, "Image open nahi hui, dobara select karo", Toast.LENGTH_LONG).show();
                } else {
                    view.setBitmap(bmp);
                }
            });
        });

        new MaterialAlertDialogBuilder(a)
                .setTitle("\uD83C\uDF9A\uFE0F Adjust Image")
                .setView(sv)
                .setPositiveButton("\u2714 Save", (d, w) -> {
                    VcamImg.saveAdj(sp, f, adj);
                    Toast.makeText(a, "Saved", Toast.LENGTH_SHORT).show();
                    done.run();
                })
                .setNegativeButton("Cancel", (d, w) -> done.run())
                .setOnCancelListener(d -> done.run())
                .show();
    }

    /** Live preview with drag (move) and pinch (zoom). */
    static final class AdjustView extends View {
        private Bitmap bmp;
        private final float[] adj;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint();
        private final ScaleGestureDetector sgd;
        private float lx, ly;
        private boolean dragging;

        AdjustView(Context c, float[] adj) {
            super(c);
            this.adj = adj;
            border.setStyle(Paint.Style.STROKE);
            border.setColor(Color.WHITE);
            border.setStrokeWidth(3f);
            sgd = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                    AdjustView.this.adj[1] = Math.max(0.5f, Math.min(6f, AdjustView.this.adj[1] * d.getScaleFactor()));
                    invalidate();
                    return true;
                }
            });
        }

        void setBitmap(Bitmap b) {
            bmp = b;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawColor(Color.BLACK);
            if (bmp != null) {
                Matrix m = VcamImg.matrix(bmp.getWidth(), bmp.getHeight(), adj[0], adj[1], adj[2], adj[3],
                        getWidth(), getHeight());
                c.save();
                c.clipRect(0, 0, getWidth(), getHeight());
                c.drawBitmap(bmp, m, paint);
                c.restore();
            }
            c.drawRect(1, 1, getWidth() - 1, getHeight() - 1, border);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            getParent().requestDisallowInterceptTouchEvent(true);
            sgd.onTouchEvent(e);
            if (e.getPointerCount() > 1 || sgd.isInProgress()) {
                dragging = false;
                return true;
            }
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lx = e.getX();
                    ly = e.getY();
                    dragging = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        adj[2] += (e.getX() - lx) / getWidth();
                        adj[3] += (e.getY() - ly) / getHeight();
                        invalidate();
                    }
                    lx = e.getX();
                    ly = e.getY();
                    dragging = true;
                    break;
                default:
                    dragging = false;
            }
            return true;
        }
    }
}
