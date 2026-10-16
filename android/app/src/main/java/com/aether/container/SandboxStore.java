package com.aether.container;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * SandboxStore — โครง sandbox root (ปรับให้ตรง layout จริงที่พบในดัมป์ runtime ของแบบจำลอง)
 *
 * หลักฐานจากดัมป์จริง (root/ ของ sandbox ที่ติดตั้ง 8BP อยู่):
 *   root/cache/
 *   root/data/app/<pkg>/package.conf      ← PackageSetting อยู่ที่ app dir (ไม่ใช่ data/system)
 *   root/data/app/<pkg>/lib/              ← ที่วาง native lib/module ของ guest
 *   root/data/user/0/<pkg>/  ·  root/data/user_de/0/<pkg>/
 *   root/proc/0/cmdline                   ← fake /proc (cmdline ของ process ปลอม)
 *   root/system/{uid.conf, user.conf, shared-user.conf}
 *
 * ต่างจากต้นแบบ: private storage ของแอปเราเอง, ไม่มี keydata/token
 */
public final class SandboxStore {

    private SandboxStore() {}

    public static File root(Context c) {
        return new File(c.getFilesDir(), "root");
    }

    // ── root/system ──────────────────────────────────────────────
    public static File systemDir(Context c) {
        return new File(root(c), "system");
    }

    private static File systemConf(Context c, String name) {
        return new File(systemDir(c), name);
    }

    // ── root/data/app/<pkg> ──────────────────────────────────────
    public static File guestDir(Context c, String pkg) {
        return new File(new File(root(c), "data/app"), pkg);
    }

    public static File guestApk(Context c, String pkg) {
        return new File(guestDir(c, pkg), "base.apk");
    }

    public static File guestPackageConf(Context c, String pkg) {
        return new File(guestDir(c, pkg), "package.conf");
    }

    /** ที่วาง lib/module ของ guest (หลักฐานจริง: root/data/app/<pkg>/lib) */
    public static File guestLibDir(Context c, String pkg) {
        File d = new File(guestDir(c, pkg), "lib");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File moduleFile(Context c, String pkg) {
        return new File(guestLibDir(c, pkg), "libgame-module.so");
    }

    // ── root/data/user[_de]/0/<pkg> ─────────────────────────────
    public static File guestUserDataDir(Context c, String pkg) {
        File d = new File(new File(new File(root(c), "data/user/0"), pkg), "");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static void ensureGuestLayout(Context c, String pkg) {
        mkdirs(guestDir(c, pkg));
        mkdirs(guestLibDir(c, pkg));
        mkdirs(new File(new File(new File(new File(root(c), "data/user"), "0"), pkg), ""));
        mkdirs(new File(new File(new File(new File(root(c), "data/user_de"), "0"), pkg), ""));
    }

    /** fake /proc: root/proc/0/cmdline (หลักฐานจริงมีไฟล์นี้ — ใส่ชื่อ process ปลอม) */
    public static void writeProcCmdline(Context c, String pkg) {
        File dir = new File(new File(root(c), "proc"), "0");
        if (!dir.exists()) dir.mkdirs();
        writeText(new File(dir, "cmdline"), pkg);
    }

    /** เตรียมโครง root + conf ตาม layout จริง */
    public static void ensureBaseLayout(Context c) {
        mkdirs(systemDir(c));
        mkdirs(new File(root(c), "cache"));
        for (String name : new String[]{"uid.conf", "shared-user.conf"}) {
            File f = systemConf(c, name);
            if (!f.exists()) writeText(f, "{}");
        }
        File uf = systemConf(c, "user.conf");
        if (!uf.exists()) writeText(uf, "{\"users\":[{\"id\":0}]}");
    }

    // ── package.conf (per-guest ที่ app dir) ────────────────────
    public static synchronized void writePackageSetting(Context c, String pkg, JSONObject setting) {
        ensureGuestLayout(c, pkg);
        File f = guestPackageConf(c, pkg);
        JSONObject all = new JSONObject();
        try {
            if (f.exists()) all = new JSONObject(readText(f));
        } catch (Exception ignored) {}
        try {
            all.put("pkg", pkg);
            all.put("setting", setting);
            writeText(f, all.toString(2));
        } catch (Exception e) {
            android.util.Log.w("AetherEngine", "package.conf write: " + e.getMessage());
        }
    }

    public static JSONObject readPackageSetting(Context c, String pkg) {
        try {
            JSONObject all = new JSONObject(readText(guestPackageConf(c, pkg)));
            return all.optJSONObject("setting");
        } catch (Exception e) {
            return null;
        }
    }

    /** uid.conf — จอง uid ปลอมเริ่มที่ 10000 (parity: alloc fake uid) */
    public static synchronized int allocUid(Context c, String pkg) {
        ensureBaseLayout(c);
        File f = systemConf(c, "uid.conf");
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

    // ── utils ────────────────────────────────────────────────────
    private static void mkdirs(File d) {
        if (!d.exists()) d.mkdirs();
    }

    private static void writeText(File f, String s) {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            android.util.Log.w("AetherEngine", "write " + f.getName() + ": " + e.getMessage());
        }
    }

    static String readText(File f) {
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
