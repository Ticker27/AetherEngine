package com.aether.share;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ZipShare — zip ไฟล์ลง cache แล้วแชร์ผ่าน chooser
 * โครงเดียวกับแบบจำลอง: buffer 4 KB → cacheDir/<ชื่อ>.zip → ACTION_SEND application/zip → chooser
 */
public final class ZipShare {

    private static final int BUF = 4096;      // parity: 4 KB buffer
    private static final String CHOOSER = "Share zipped file"; // parity: chooser title เดิม

    private ZipShare() {}

    public static File zipToCache(Context ctx, File src) throws IOException {
        File out = new File(ctx.getCacheDir(), src.getName() + ".zip");
        byte[] buf = new byte[BUF];
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            zos.putNextEntry(new ZipEntry(src.getName()));
            try (InputStream in = new FileInputStream(src)) {
                int n;
                while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
            }
            zos.closeEntry();
        }
        return out;
    }

    public static void share(Context ctx, File src) throws IOException {
        File zip = zipToCache(ctx, src);
        Uri uri = AetherFileProvider.uriFor(zip.getName());
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(send, CHOOSER);
        if (!(ctx instanceof Activity)) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(chooser);
        android.util.Log.i("AetherEngine", "ZipShare " + zip.getAbsolutePath());
    }

    /** ใช้ในกรณีไม่มี path จาก caller — สร้างไฟล์สาธิตใน cacheDir */
    public static File demoFile(Context ctx) throws IOException {
        File f = new File(ctx.getCacheDir(), "aether_demo.txt");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write("aether-engine demo payload\n".getBytes("UTF-8"));
        }
        return f;
    }
}
