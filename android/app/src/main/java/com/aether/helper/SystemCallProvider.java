package com.aether.helper;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

import com.aether.container.AetherCore;

/**
 * parity: SystemCallProvider ของแบบจำลอง (:helper process, exported)
 *
 * ต้นแบบ (ถอดจาก static analysis):
 *   call("VM", "_S_|_server_name_", Bundle)
 *     → core service lookup → IBinder        [core service lookup]
 *     → ฝัง Binder ข้าม process  
 *   ทำหน้าที่เป็น IPC bridge ส่ง Binder ของ container framework ของแบบจำลอง จาก :helper ไป main process
 *
 * AetherEngine: โครง call()-based bridge เดิม — แต่ AetherCore.inProcessEngine() = ไม่ต้อง fork framework
 * จึงส่ง IBinder = null ได้เลย (โครงสร้างเดิม, จุดต่อไว้ให้ต่อยอด) — ตัด obfuscated IPC internals (core IPC helpers)
 */
public class SystemCallProvider extends ContentProvider {

    /** parity: method "VM" (container framework ของแบบจำลอง server lookup) */
    private static final String METHOD_VM = "VM";
    /** parity: key "_S_|_server_" */
    private static final String KEY_SERVER = "_S_|_server_";

    @Override
    public boolean onCreate() {
        // แบบจำลอง: core init + provider-bridge init ของ container framework
        android.util.Log.i("AetherEngine", "SystemCallProvider.onCreate (:helper)");
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        android.util.Log.i("AetherEngine", "call(method=" + method + ", arg=" + arg + ")");
        Bundle out = new Bundle();
        if (METHOD_VM.equals(method)) {
            // parity: e9.d(bundle, KEY_SERVER, binder) — ส่ง server binder ข้าม process
            IBinder vm = AetherCore.mainProcessBinder();  // AetherEngine: engine อยู่ in-process คืน null หรือ binder ที่มีอยู่
            if (vm != null) out.putBinder(KEY_SERVER, vm);
            out.putString("_S_|_retval_", "ok");
        }
        return out;
    }

    // ── ที่เหลือเป็น implement ตาม ContentProvider (ไม่ใช้) ──────
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
