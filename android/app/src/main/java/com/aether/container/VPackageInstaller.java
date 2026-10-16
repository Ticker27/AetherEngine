package com.aether.container;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.aether.share.AetherFileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

/**
 * VPackageInstaller — ไปป์ไลน์ติดตั้งเป้าหมายเข้า sandbox (โครงเดียวกับแบบจำลอง §1)
 *
 * ขั้นตอน (parity: install gate → copy → PackageSetting → uid):
 *   1. copy APK เข้า temp → getPackageArchiveInfo (pkg/version)
 *   2. copy เข้า sandbox: root/data/app/<pkg>/base.apk
 *   3. คำนวณ sha256 (parity: entry "versionName@sha256" — self-record เพราะไม่มี C2)
 *   4. เทียบ pin กับ TargetRegistry (8BP = 56.31.0) → pin=match|mismatch(want=…)
 *   5. เขียน root/data/system/package.conf (PackageSetting + states) + uid.conf (alloc uid)
 *   6. เตรียม root/data/user/0/<pkg>/lib (ที่วาง module .so)
 *   7. ส่งต่อ system installer ด้วย content:// (แก้ case file:// FileUriExposedException)
 */
final class VPackageInstaller {

    private VPackageInstaller() {}

    static String install(Context ctx, Uri apkUri) {
        File tmp = null;
        try {
            SandboxStore.ensureBaseLayout(ctx);

            // 1) temp copy เพื่อ parse
            tmp = new File(ctx.getCacheDir(), "incoming_" + System.currentTimeMillis() + ".apk");
            copy(ctx.getContentResolver().openInputStream(apkUri), tmp);

            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(tmp.getAbsolutePath(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
            if (pi == null) return "ERR:not-an-apk";

            String pkg = pi.packageName;
            String version = pi.versionName == null ? "?" : pi.versionName;
            long vCode = pi.getLongVersionCode();
            String sha = sha256(tmp);

            // 2) เข้า sandbox root
            File dst = SandboxStore.guestApk(ctx, pkg);
            File dir = dst.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            try (InputStream in = new FileInputStream(tmp);
                 OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            markReadOnly(dst);   // Android ≥14: dex ที่เขียนได้โหลดไม่ได้

            // 3+4) pin check
            TargetRegistry.Target t = TargetRegistry.byPkg(pkg);
            String pin;
            if (t == null) {
                pin = "n/a";
            } else {
                pin = version.equals(t.pinVersion)
                        ? "match"
                        : "mismatch(want=" + t.pinVersion + ")";
            }

            // 5) conf files
            JSONObject setting = new JSONObject();
            setting.put("apkPath", dst.getAbsolutePath());
            setting.put("versionName", version);
            setting.put("versionCode", vCode);
            setting.put("sha256", sha);
            setting.put("installedAt", System.currentTimeMillis());
            setting.put("pin", pin);
            JSONObject states = new JSONObject();
            states.put("installed", true);
            states.put("stopped", false);
            states.put("hidden", false);
            states.put("launched", false);
            setting.put("states", states);
            SandboxStore.writePackageSetting(ctx, pkg, setting);
            int uid = SandboxStore.allocUid(ctx, pkg);
            SandboxStore.guestLibDir(ctx, pkg);

            // 6) handoff → system installer (content:// เท่านั้น)
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(AetherFileProvider.uriForGuest(pkg), "application/vnd.android.package-archive");
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(it);
            } catch (Exception e) {
                android.util.Log.w("AetherEngine", "installer handoff: " + e.getMessage());
            }

            android.util.Log.i("AetherEngine", "install: " + pkg + "@" + version + " pin=" + pin
                    + " uid=" + uid + " sandbox=" + dst.getAbsolutePath());
            return "installed:" + pkg + "@" + version + " pin=" + pin + " uid=" + uid
                    + " sha256=" + sha.substring(0, 12) + "…";
        } catch (Exception e) {
            return "ERR:" + e.getMessage();
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    /** Android ≥14: PathClassLoader ปฏิเสธ dex ที่เขียนได้ — ต้อง mark read-only ก่อนโหลด */
    static void markReadOnly(File f) {
        try {
            if (!f.setReadOnly()) {
                // สำรอง: ตัด write bit ตรง ๆ
                //noinspection ResultOfMethodCallIgnored
                f.setWritable(false, false);
            }
            android.util.Log.i("AetherEngine", "apk read-only: " + f.getName() + " writable=" + f.canWrite());
        } catch (Exception e) {
            android.util.Log.w("AetherEngine", "setReadOnly: " + e.getMessage());
        }
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

    static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
