package com.aether.container;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * PluginInstaller — pipeline ปลั๊กอิน/โมดูล (parity KOS)
 *
 * หลักฐาน (2026-10-20):
 *   - กดเล่น → ดาวน์โหลด .bin เก็บใน /data/user/0/com.kos/cache/ (ชื่อตาม server id, เช่น ce1436bf…)
 *   - sha256 ของไฟล์ = acc706395e30… = ค่า loader_<pkg> ใน KOS_App_Settings.xml  (ตัวตน "loader")
 *   - VM config: pathToApk=cache/<id>  → กระจายเป็น apk เข้า guest data/app_plugin_workspace_p0
 * ที่นี่: cache/<sha256>.bin (self-record; KOS ใช้ server id — deviation ที่บันทึกไว้), verify sha ตัวเอง,
 * กระจายเป็น workspace/plugin.apk และบันทึกทุกอย่างลง package.conf (setting.plugin)
 */
public final class PluginInstaller {

    private static final String TAG = "AetherEngine";

    private PluginInstaller() {}

    /** ทำให้ปลั๊กอินพร้อม: cache → verify → distribute. คืนสถานะสั้น ๆ */
    public static String ensurePlugin(Context ctx, String pkg, long timeoutMs) {
        TargetRegistry.Target t = TargetRegistry.byPkg(pkg);
        if (t == null || t.pluginUrl == null) return "n/a";
        SandboxStore.ensureBaseLayout(ctx);
        SandboxStore.ensureGuestLayout(ctx, pkg);
        File cache = new File(SandboxStore.root(ctx), "cache");
        if (!cache.exists()) cache.mkdirs();

        JSONObject conf = SandboxStore.readPackageSetting(ctx, pkg);
        JSONObject pl = conf == null ? null : conf.optJSONObject("plugin");
        String recSha = pl == null ? null : pl.optString("sha256", null);
        if (recSha != null && recSha.length() == 64) {
            File cached = new File(cache, recSha + ".bin");
            if (cached.exists() && cached.length() > 0) {
                return "ready:" + recSha.substring(0, 12) + " (" + distribute(ctx, pkg, cached, conf) + ")";
            }
        }

        File tmp = new File(cache, "plugin.download.tmp");
        String sha = download(t.pluginUrl, tmp, timeoutMs);
        if (sha == null) return "download-failed";

        File dst = new File(cache, sha + ".bin");
        if (!tmp.renameTo(dst)) {
            try {
                copyFile(tmp, dst);
                tmp.delete();
            } catch (Exception e) {
                android.util.Log.w(TAG, "plugin cache: " + e);
                return "cache-write-failed";
            }
        }
        JSONObject setting = conf != null ? conf : new JSONObject();
        try {
            JSONObject p = new JSONObject();
            p.put("url", t.pluginUrl);
            p.put("path", dst.getAbsolutePath());
            p.put("sha256", sha);
            p.put("size", dst.length());
            p.put("downloadedAt", System.currentTimeMillis());
            p.put("id", urlId(t.pluginUrl));
            setting.put("plugin", p);
            SandboxStore.writePackageSetting(ctx, pkg, setting);
        } catch (Exception e) {
            android.util.Log.w(TAG, "plugin conf: " + e);
        }
        return "downloaded:" + sha.substring(0, 12) + " (" + distribute(ctx, pkg, dst, setting) + ")";
    }

    /** กระจายไฟล์เป็น APK เข้า guest app_plugin_workspace_p0 (parity kos_engine/data/user/0/<pkg>/…) */
    private static String distribute(Context ctx, String pkg, File bin, JSONObject conf) {
        try {
            File ws = new File(SandboxStore.guestUserDataDir(ctx, pkg), "app_plugin_workspace_p0");
            if (!ws.exists()) ws.mkdirs();
            File out = new File(ws, "plugin.apk");
            copyFile(bin, out);
            JSONObject setting = conf != null ? conf : new JSONObject();
            JSONObject p = setting.optJSONObject("plugin");
            if (p == null) p = new JSONObject();
            JSONObject dist = new JSONObject();
            dist.put("file", out.getAbsolutePath());
            dist.put("size", out.length());
            dist.put("at", System.currentTimeMillis());
            p.put("workspace", dist);
            setting.put("plugin", p);
            SandboxStore.writePackageSetting(ctx, pkg, setting);
            android.util.Log.i(TAG, "plugin distributed → " + out + " (" + out.length() + " B)");
            return "workspace-ok";
        } catch (Throwable t) {
            android.util.Log.w(TAG, "plugin distribute: " + t);
            return "workspace-fail";
        }
    }

    private static String download(String urlStr, File dst, long timeoutMs) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(urlStr);
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout((int) Math.max(20000L, timeoutMs));
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "AetherEngine/1.0");
            int code = c.getResponseCode();
            if (code != 200) {
                android.util.Log.w(TAG, "plugin download HTTP " + code);
                return null;
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (InputStream in = c.getInputStream();
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    md.update(buf, 0, n);
                    total += n;
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            android.util.Log.i(TAG, "plugin downloaded: " + total + " B sha=" + sb.substring(0, 12) + "…");
            return sb.toString();
        } catch (Throwable t) {
            android.util.Log.w(TAG, "plugin download: " + t);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static void copyFile(File src, File dst) throws Exception {
        File d = dst.getParentFile();
        if (d != null && !d.exists()) d.mkdirs();
        try (InputStream in = new java.io.FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static String urlId(String url) {
        int i = url.lastIndexOf('/');
        int j = url.lastIndexOf('.');
        return (i >= 0 && j > i) ? url.substring(i + 1, j) : "plugin";
    }
}
