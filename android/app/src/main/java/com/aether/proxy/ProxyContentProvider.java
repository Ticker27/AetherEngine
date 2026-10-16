package com.aether.proxy;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * ProxyContentProvider — stub provider ของ container (P0–P3 ต่อ process)
 * โครงเดียวกับแบบจำลอง: call() delegate ไป core; ที่นี่ตอบ Bundle ระบุตัว stub
 */
public class ProxyContentProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        android.util.Log.i("AetherEngine", "ProxyContentProvider.onCreate " + getClass().getSimpleName());
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle out = new Bundle();
        out.putString("proxy", getClass().getSimpleName());
        out.putString("method", method);
        out.putString("arg", arg);
        return out;
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }

    public static class P0 extends ProxyContentProvider {}
    public static class P1 extends ProxyContentProvider {}
    public static class P2 extends ProxyContentProvider {}
    public static class P3 extends ProxyContentProvider {}
}
