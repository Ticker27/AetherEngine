package com.aether.container;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * SandboxStore — โครง sandbox root + conf files
 *
 * โครงเดียวกับแบบจำลอง (หลักฐาน: path mapping + conf 6 ไฟล์):
 *   root/data/system/{package.conf, uid.conf, user.conf, shared-user.conf, accounts.conf, fake-location.conf}
 *   root/data/app/<pkg>/base.apk
 *   root/data/user/0/<pkg>/lib                     (ที่วาง module .so ของเกสต์)
 *
 * ต่างจากต้นแบบ: เป็น private storage ของแอปเอง (ไม่ดัดแปลงระบบ), ไม่มี keydata/token
 */
public final class SandboxStore {

    private SandboxStore() {}

    public static File root(Context c) {
        return new File(c.getFilesDir(), "root");
    }

    public static File systemDir(Context c) {
        return new File(new File(root(c), "data"), "system");
    }

    public static File guestDir(Context c, String pkg) {
        return new File(new File(new File(root(c), "data/app")), pkg);
    }

    public static File guestApk(Context c, String pkg) {
        return new File(guestDir(c, pkg), "base.apk");
    }

    public static File guestLibDir(Context c, String pkg) {
        File d = new File(new File(new File(new File(root(c), "data/user"), "0"), pkg), "lib");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File moduleFile(Context c, String pkg) {
        return new File(guestLibDir(c, pkg), "libgame-module.so");
    }

    /** เตรียมโครง root + conf ว่าง 6 ไฟล์ (ถ้ายังไม่มี) */
    public static void ensureBaseLayout(Context c) {
        File sys = systemDir(c);
        if (!sys.exists()) sys.mkdirs();
        for (String name : new String[]{"package.conf", "uid.conf", "user.conf",
                "shared-user.conf", "accounts.conf", "fake-location.conf"}) {
            File f = new File(sys, name);
            if (!f.exists()) writeText(f, name.equals("user.conf") ? "{\"users\":[{\"id\":0}]}" : "{}");
        }
    }

    /** package.conf — PackageSetting ต่อแพ็กเกจ (parity: BPackageUserState fields) */
    public static synchronized void writePackageSetting(Context c, String pkg, JSONObject setting) {
        ensureBaseLayout(c);
        File f = new File(systemDir(c), "package.conf");
        try {
            JSONObject all = new JSONObject(readText(f));
            all.put(pkg, setting);
            writeText(f, all.toString(2));
        } catch (Exception e) {
            android.util.Log.w("AetherEngine", "package.conf write: " + e.getMessage());
        }
    }

    public static JSONObject readPackageSetting(Context c, String pkg) {
        try {
            JSONObject all = new JSONObject(readText(new File(systemDir(c), "package.conf")));
            return all.optJSONObject(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    /** uid.conf — จอง uid ปลอมเริ่มที่ 10000 (parity: alloc fake uid) */
    public static synchronized int allocUid(Context c, String pkg) {
        ensureBaseLayout(c);
        File f = new File(systemDir(c), "uid.conf");
        try {
            JSONObject all = new JSONObject(readText(f));
            if (all.has(pkg)) return all.getInt(pkg);
            int next = 10000;
            for (java.util.Iterator<String> it = all.keys(); it.hasNext(); ) {
                next = Math.max(next, all.getInt(it.next()) + 1);
            }
            all.put(pkg, next);
            writeText(f, all.toString(2));
            return next;
        } catch (Exception e) {
            return 10000;
        }
    }

    /** บันทึกสถานะ launched (parity: BPackageUserState.launched) */
    public static void markLaunched(Context c, String pkg, long atMs) {
        JSONObject st = readPackageSetting(c, pkg);
        try {
            JSONObject o = st != null ? st : new JSONObject();
            JSONObject states = o.optJSONObject("states");
            if (states == null) states = new JSONObject();
            states.put("installed", true);
            states.put("launched", true);
            states.put("lastLaunchedAt", atMs);
            o.put("states", states);
            writePackageSetting(c, pkg, o);
        } catch (Exception ignored) {}
    }

    private static void writeText(File f, String s) {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            android.util.Log.w("AetherEngine", "write " + f.getName() + ": " + e.getMessage());
        }
    }

    private static String readText(File f) {
        try {
            byte[] buf = new byte[(int) f.length()];
            try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                int n = fis.read(buf);
                return new String(buf, 0, Math.max(n, 0), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return "{}";
        }
    }
}
