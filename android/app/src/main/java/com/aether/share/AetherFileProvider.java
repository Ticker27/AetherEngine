package com.aether.share;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * AetherFileProvider — provider แชร์ไฟล์ออกแบบจำกัดขอบเขต (แทน file:// ที่ถูกห้ามบน API 24+)
 *
 * แผนที่ URI (allow-list เท่านั้น — ห้าม path traversal):
 *   files/<name>      → cacheDir/<name>                          (แชร์ zip/รูปจาก cache)
 *   containers/<name> → filesDir/containers/<name>               (APK ที่คัดลอกเข้า container)
 *   guest/<pkg>       → filesDir/root/data/app/<pkg>/base.apk    (APK เป้าหมายใน sandbox root)
 */
public class AetherFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.aether.fileprovider";

    public static Uri uriFor(String name) {
        return Uri.parse("content://" + AUTHORITY + "/files/" + Uri.encode(name));
    }

    public static Uri uriForContainerApk(String name) {
        return Uri.parse("content://" + AUTHORITY + "/containers/" + Uri.encode(name));
    }

    public static Uri uriForGuest(String pkg) {
        return Uri.parse("content://" + AUTHORITY + "/guest/" + Uri.encode(pkg));
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
        if (name.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (name.endsWith(".zip")) return "application/zip";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    /** แก้ URI → ไฟล์ ตาม allow-list */
    private File resolve(Uri uri) throws FileNotFoundException {
        var segs = uri.getPathSegments();
        if (segs.size() < 2) throw new FileNotFoundException("bad uri");
        String kind = segs.get(0);
        String name = segs.get(1);
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new FileNotFoundException("invalid segment");
        }
        File base;
        if ("files".equals(kind)) {
            base = getContext().getCacheDir();
        } else if ("containers".equals(kind)) {
            base = new File(getContext().getFilesDir(), "containers");
        } else if ("guest".equals(kind)) {
            // guest/<pkg> → root/data/app/<pkg>/base.apk
            File guest = new File(new File(new File(getContext().getFilesDir(), "root"), "data/app"), name);
            File apk = new File(guest, "base.apk");
            if (!apk.exists() || !apk.isFile()) throw new FileNotFoundException("guest apk missing: " + name);
            return apk;
        } else {
            throw new FileNotFoundException("unknown kind");
        }
        File f = new File(base, name);
        if (!f.exists() || !f.isFile()) throw new FileNotFoundException(name);
        return f;
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
