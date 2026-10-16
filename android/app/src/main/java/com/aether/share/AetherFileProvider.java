package com.aether.share;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * AetherFileProvider — provider ขนาดเล็กสำหรับแชร์ไฟล์ออก (แทน FileProvider ทั่วไป)
 * นโยบาย: ให้อ่านได้เฉพาะไฟล์ใน cacheDir ของแอป (ชื่อไฟล์ใน URI — ห้าม path traversal)
 */
public class AetherFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.aether.fileprovider";

    public static Uri uriFor(String name) {
        return Uri.parse("content://" + AUTHORITY + "/files/" + Uri.encode(name));
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(uri);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) return "application/octet-stream";
        if (name.endsWith(".zip")) return "application/zip";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new FileNotFoundException("invalid name");
        }
        File f = new File(getContext().getCacheDir(), name);
        if (!f.exists() || !f.isFile()) throw new FileNotFoundException(name);
        return f;
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
