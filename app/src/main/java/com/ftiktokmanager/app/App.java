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
