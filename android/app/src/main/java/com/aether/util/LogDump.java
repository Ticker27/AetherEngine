package com.aether.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.aether.share.AetherFileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * LogDump — ระบบ log/dump ในตัว (parity KOS "LOG VIEW: Export Recent Logs")
 *
 * หลักฐานจากดัมป์จริง (com.kos.txt + com.miniclip.eightballpool-2026-10-20_12-55-32-014-0.txt):
 *   - 1 บรรทัด = "<epoch_ms> [KOS-DIRECT] process=<proc> pid=<pid> <ข้อความ>"
 *   - ไฟล์ host: com.kos.txt · ไฟล์ guest: <pkg>-YYYY-MM-DD_HH-mm-ss-SSS-0.txt (ต่อ 1 session ใน :p0)
 *   - เปิดด้วย "[LOGGER] capture started; file=… virtual=<pkg>/0; raw logcat is best-effort"
 *   - ปิดด้วย "game process ended; draining final logcat records"
 *   - เก็บล่าสุด 3 game logs + ไฟล์ engine แล้ว export ผ่าน share
 */
public final class LogDump {

    public static final String TAG = "AetherEngine";
    public static final String ENGINE_FILE = "com.aether.txt";
    private static final int MAX_GAME_LOGS = 3;

    private LogDump() {}

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "logs");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static String procName() {
        try {
            return android.app.Application.getProcessName();
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String line(String msg) {
        return System.currentTimeMillis() + " [AE-DIRECT] process=" + procName()
                + " pid=" + android.os.Process.myPid() + " " + msg + "\n";
    }

    private static synchronized void append(File f, String s) {
        try (FileOutputStream o = new FileOutputStream(f, true)) {
            o.write(s.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            android.util.Log.w(TAG, "log dump write: " + t);
        }
    }

    /** log ของ engine (host) → com.aether.txt */
    public static void engine(Context c, String msg) {
        append(new File(dir(c), ENGINE_FILE), line(msg));
    }

    /** เปิด session ของ guest (ต่อ 1 ครั้งที่ boot) — ตั้งชื่อตามดัมป์จริง */
    public static File startSession(Context c, String pkg) {
        String ts = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).format(new Date());
        File f = new File(dir(c), pkg + "-" + ts + "-0.txt");
        append(f, line("[LOGGER] capture started; file=" + f.getName()
                + " package=" + pkg + " virtual=" + pkg + "/0"));
        rotate(c);
        return f;
    }

    public static void stage(File session, String msg) {
        if (session != null) append(session, line(msg));
        android.util.Log.i(TAG, msg);
    }

    public static void endSession(File session, String reason) {
        if (session == null) return;
        append(session, line("game process ended; " + reason + "; draining final records"));
        android.util.Log.i(TAG, "log session ended: " + reason);
    }

    /** เก็บล่าสุด 3 game logs (ไม่แตะ com.aether.txt) */
    public static synchronized void rotate(Context c) {
        File[] all = dir(c).listFiles((d, n) -> n.endsWith(".txt") && !n.equals(ENGINE_FILE));
        if (all == null || all.length <= MAX_GAME_LOGS) return;
        Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = MAX_GAME_LOGS; i < all.length; i++) {
            //noinspection ResultOfMethodCallIgnored
            all[i].delete();
        }
    }

    public static List<File> recent(Context c) {
        List<File> out = new ArrayList<>();
        File engine = new File(dir(c), ENGINE_FILE);
        if (engine.exists()) out.add(engine);
        File[] games = dir(c).listFiles((d, n) -> n.endsWith(".txt") && !n.equals(ENGINE_FILE));
        if (games != null) {
            Arrays.sort(games, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (int i = 0; i < games.length && i < MAX_GAME_LOGS; i++) out.add(games[i]);
        }
        return out;
    }

    /** export ล่าสุดเป็น zip แล้วเปิด share chooser (parity ปุ่ม Export Recent Logs) */
    public static String export(Context c) {
        try {
            List<File> files = recent(c);
            File zip = new File(c.getCacheDir(), "export_logs.zip");
            try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip))) {
                for (File f : files) {
                    z.putNextEntry(new ZipEntry(f.getName()));
                    try (InputStream in = new java.io.FileInputStream(f)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) z.write(buf, 0, n);
                    }
                    z.closeEntry();
                }
            }
            Uri u = AetherFileProvider.uriFor(zip.getName());
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/zip");
            send.putExtra(Intent.EXTRA_STREAM, u);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            Intent chooser = Intent.createChooser(send, "Export Recent Logs")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(chooser);
            String r = "exported:" + files.size() + " file(s) → " + zip.getName()
                    + " (" + zip.length() + " B)";
            engine(c, r);
            return r;
        } catch (Throwable t) {
            return "ERR:" + t;
        }
    }
}
