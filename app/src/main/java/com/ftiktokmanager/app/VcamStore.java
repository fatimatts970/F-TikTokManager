package com.ftiktokmanager.app;

import android.content.Context;
import java.io.File;

/** Keeps each account's VCAM image inside app-private storage (survives restarts). */
final class VcamStore {
    private VcamStore() {}

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "vcam");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static void deleteFor(Context c, int accountId) {
        File[] old = dir(c).listFiles((d, n) -> n.startsWith("acc_" + accountId + "."));
        if (old != null) for (File f : old) f.delete();
    }
}
