package com.ftiktokmanager.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends Application {
    private static App instance;
    private DbHelper db;
    private final ExecutorService ioPool = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        db = new DbHelper(this);

        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                String text = android.os.Build.VERSION.RELEASE + " / SDK " + android.os.Build.VERSION.SDK_INT
                        + "\n" + sw;
                java.io.FileOutputStream fos = new java.io.FileOutputStream(new java.io.File(getFilesDir(), "last_crash.txt"));
                fos.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                fos.close();
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    /** Returns the last crash text (once) and deletes the file, or null. */
    public static String readCrash() {
        try {
            java.io.File f = new java.io.File(instance.getFilesDir(), "last_crash.txt");
            if (!f.exists()) return null;
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            }
            f.delete();
            return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    /** One shared database helper for the whole process. */
    public static DbHelper db() {
        return instance.db;
    }

    /** Run work off the main thread (database, files, network). */
    public static void io(Runnable r) {
        instance.ioPool.execute(r);
    }

    /** Post work back to the main thread. */
    public static void ui(Runnable r) {
        instance.mainHandler.post(r);
    }
}
