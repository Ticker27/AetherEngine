package com.aether.container;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * ContentCache — คลังไฟล์แบบ content-addressed: files/<sha256>
 *
 * parity: ต้นแบบแคช asset/ไอคอนของ C2 ไว้ที่ files/<sha256> (ดัมป์จริง: 90 ไฟล์ — 3 png, 3 jpg, อื่น ๆ binary)
 * ที่นี่: ไม่มี C2 — แคชไอคอน/target asset ของโปรเจกต์เอง + เพิ่ม index.json (ส่วนเพิ่มของเรา ไม่มีในต้นแบบ)
 */
public final class ContentCache {

    private ContentCache() {}

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "files");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File get(Context c, String sha) {
        return new File(dir(c), sha);
    }

    /** เก็บไฟล์ → คืน sha256 (ชื่อไฟล์ในแคช) */
    public static String put(Context c, File src) {
        try {
            String sha = sha256(src);
            File dst = get(c, sha);
            if (!dst.exists()) {
                try (InputStream in = new java.io.FileInputStream(src);
                     FileOutputStream out = new FileOutputStream(dst)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
            }
            indexPut(c, sha, src.getName(), src.length());
            return sha;
        } catch (Exception e) {
            android.util.Log.w("AetherEngine", "cache put: " + e.getMessage());
            return null;
        }
    }

    /** เก็บ byte[] (เช่น ไอคอนจาก assets) → คืน sha256 */
    public static String putBytes(Context c, byte[] data, String name) {
        try {
            String sha = sha256(data);
            File dst = get(c, sha);
            if (!dst.exists()) {
                try (FileOutputStream out = new FileOutputStream(dst)) {
                    out.write(data);
                }
            }
            indexPut(c, sha, name, data.length);
            return sha;
        } catch (Exception e) {
            android.util.Log.w("AetherEngine", "cache putBytes(" + name + "): " + e);
            return null;
        }
    }

    public static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return hex(md.digest());
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(data);
        return hex(md.digest());
    }

    private static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static synchronized void indexPut(Context c, String sha, String name, long size) {
        try {
            File f = new File(dir(c), "index.json");
            JSONArray arr = f.exists() ? new JSONArray(SandboxStore.readText(f)) : new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                if (sha.equals(arr.getJSONObject(i).optString("sha256"))) return;
            }
            JSONObject o = new JSONObject();
            o.put("sha256", sha);
            o.put("name", name);
            o.put("size", size);
            o.put("at", System.currentTimeMillis());
            arr.put(o);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(arr.toString(2).getBytes("UTF-8"));
            }
        } catch (Exception ignored) {}
    }
}
