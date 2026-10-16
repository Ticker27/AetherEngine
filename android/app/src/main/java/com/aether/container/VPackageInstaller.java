package com.aether.container;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * parity: container installer ของแบบจำลอง — install APK เข้า container
 *
 * ต้นแบบ: คัดลอก APK ของแอปเป้าหมายมาใส่ virtual storage แล้ว register ใน virtual package manager
 *   โดย disable launcher ของแอปเดิม (clone ล่องหน)
 * AetherEngine: คัดลอก APK เข้า filesDir/containers/ + จด registry — และแจ้งผู้ใช้ผ่าน ACTION_VIEW
 *   ทาง PackageInstaller ของระบบ (มี REQUEST_INSTALL_PACKAGES ไว้ตาม parity) — แต่ไม่ลบ/hide แอปเดิม
 */
final class VPackageInstaller {

    private VPackageInstaller() {}

    static void install(Context ctx, Uri apkUri) {
        try {
            File dir = new File(ctx.getFilesDir(), "containers");
            if (!dir.exists()) dir.mkdirs();

            String name = queryName(ctx, apkUri);
            if (name == null || name.isEmpty()) name = "container_" + System.currentTimeMillis() + ".apk";
            File out = new File(dir, name);
            copy(ctx.getContentResolver().openInputStream(apkUri), out);

            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(out.getAbsolutePath(), 0);
            if (pi == null) {
                android.util.Log.w("AetherEngine", "install: ไม่ใช่ APK ที่อ่านได้");
                return;
            }

            // register เข้า container registry (parity: virtual package manager)
            JSONObject entry = new JSONObject();
            entry.put("pkg", pi.packageName);
            entry.put("version", pi.versionName);
            entry.put("apk", out.getAbsolutePath());
            entry.put("installedAt", System.currentTimeMillis());
            AetherCore.addToRegistry(ctx, entry);

            // parity: ต้นแบบใช้ s2 (VirtualInstaller) ติดตั้งใน virtual space — ที่นี่ส่งต่อ system installer
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(Uri.fromFile(out), "application/vnd.android.package-archive");
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(it);
            } catch (Exception e) {
                android.util.Log.i("AetherEngine", "install: system installer not shown (" + e.getMessage()
                        + ") — APK เก็บไว้ที่ " + out.getAbsolutePath());
            }
        } catch (Exception e) {
            android.util.Log.w("AetherEngine", "install failed: " + e.getMessage());
        }
    }

    private static String queryName(Context ctx, Uri uri) {
        try (android.database.Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void copy(InputStream in, File dst) throws Exception {
        if (in == null) throw new IllegalStateException("no input stream");
        try (OutputStream os = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        } finally {
            try { in.close(); } catch (Exception ignored) {}
        }
    }
}
