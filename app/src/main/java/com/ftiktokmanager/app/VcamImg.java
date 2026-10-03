package com.ftiktokmanager.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Gallery images of a clone (several per account) + rotate/zoom/pan adjustment. */
final class VcamImg {
    static final int OUT_W = 540;
    static final int OUT_H = 720;

    private VcamImg() {}

    static List<File> list(Context c, int accId) {
        File[] fs = VcamStore.dir(c).listFiles((d, n) -> n.startsWith("acc_" + accId + "."));
        List<File> out = new ArrayList<>();
        if (fs != null) {
            Arrays.sort(fs);
            out.addAll(Arrays.asList(fs));
        }
        return out;
    }

    static File importUri(Context c, int accId, Uri uri) throws Exception {
        File f = new File(VcamStore.dir(c), "acc_" + accId + "." + System.currentTimeMillis() + ".img");
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(f)) {
            if (in == null) throw new Exception("Cannot open image");
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return f;
    }

    static void delete(SharedPreferences sp, File f) {
        sp.edit().remove("vadj_" + f.getName()).apply();
        f.delete();
    }

    static File selected(SharedPreferences sp, int accId) {
        String s = sp.getString("active_vcam_uri_" + accId, "");
        if (s.isEmpty()) return null;
        try {
            Uri u = Uri.parse(s);
            if ("file".equals(u.getScheme()) && u.getPath() != null) {
                File f = new File(u.getPath());
                if (f.exists()) return f;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static void select(SharedPreferences sp, int accId, File f) {
        sp.edit().putString("active_vcam_uri_" + accId, Uri.fromFile(f).toString()).apply();
    }

    // ------------------------------------------------------------ adjust values

    /** {rotation, zoom, dx, dy} */
    static float[] getAdj(SharedPreferences sp, File f) {
        float[] a = {0f, 1f, 0f, 0f};
        try {
            String[] p = sp.getString("vadj_" + f.getName(), "").split(",");
            if (p.length == 4) for (int i = 0; i < 4; i++) a[i] = Float.parseFloat(p[i]);
        } catch (Exception ignored) {
        }
        return a;
    }

    static void saveAdj(SharedPreferences sp, File f, float[] a) {
        sp.edit().putString("vadj_" + f.getName(), a[0] + "," + a[1] + "," + a[2] + "," + a[3]).apply();
    }

    // ------------------------------------------------------------ bitmap work

    static Bitmap load(File f, int maxSide) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            int longest = Math.max(o.outWidth, o.outHeight);
            while (longest / sample > maxSide) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
            if (b == null) return null;
            int deg = 0;
            try {
                int ori = new ExifInterface(f.getAbsolutePath())
                        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (ori == ExifInterface.ORIENTATION_ROTATE_90) deg = 90;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_180) deg = 180;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_270) deg = 270;
            } catch (Exception ignored) {
            }
            if (deg != 0) {
                Matrix m = new Matrix();
                m.postRotate(deg);
                Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (r != b) b.recycle();
                b = r;
            }
            return b;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** Same maths for the live preview and the final picture. */
    static Matrix matrix(int bw, int bh, float rot, float zoom, float dx, float dy, int ow, int oh) {
        int r = ((Math.round(rot) % 360) + 360) % 360;
        boolean swap = (r % 180) != 0;
        float rw = swap ? bh : bw;
        float rh = swap ? bw : bh;
        float s = Math.max(ow / rw, oh / rh) * zoom;
        Matrix m = new Matrix();
        m.postTranslate(-bw / 2f, -bh / 2f);
        m.postRotate(r);
        m.postScale(s, s);
        m.postTranslate(ow / 2f + dx * ow, oh / 2f + dy * oh);
        return m;
    }

    static Bitmap render(Bitmap src, float[] a, int ow, int oh) {
        Bitmap out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.BLACK);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        c.drawBitmap(src, matrix(src.getWidth(), src.getHeight(), a[0], a[1], a[2], a[3], ow, oh), p);
        return out;
    }

    /** JPEG bytes of the selected image with its adjustment applied, or null. */
    static byte[] selectedJpeg(Context c, SharedPreferences sp, int accId) {
        File f = selected(sp, accId);
        if (f == null) return null;
        Bitmap src = load(f, 1600);
        if (src == null) return null;
        Bitmap out = render(src, getAdj(sp, f), OUT_W, OUT_H);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, 92, bos);
        src.recycle();
        out.recycle();
        return bos.toByteArray();
    }

    /** Adjusted picture written to cache (used as the upload result of a file chooser). */
    static File adjustedFile(Context c, SharedPreferences sp, int accId) {
        try {
            byte[] jpg = selectedJpeg(c, sp, accId);
            if (jpg == null) return null;
            File[] old = c.getCacheDir().listFiles((d, n) -> n.startsWith("vcam_cap_"));
            if (old != null) for (File o : old) o.delete();
            File f = new File(c.getCacheDir(), "vcam_cap_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream fo = new FileOutputStream(f)) {
                fo.write(jpg);
            }
            return f;
        } catch (Exception e) {
            return null;
        }
    }
}
