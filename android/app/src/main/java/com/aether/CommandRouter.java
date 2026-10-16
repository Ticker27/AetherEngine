package com.aether;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;

import com.aether.container.AetherCore;
import com.aether.helper.Native;

/**
 * parity: com.Entry.C(MethodCallHandler) — code-table dispatcher
 *
 * ต้นแบบรับ code จาก Dart method channel (01M String), AetherEngine รับ code จาก UI ตรง ๆ
 * โครง code table ชุดเดียวกับต้นแบบ:
 *
 *   "0"  → Native.djp(int)                    ถอด blob (ต้นแบบ: license/blob payload)
 *   "A"  → Toast.makeText(...).show()
 *   "B"  → SharedPreferences.putString(...).apply()
 *   "D"  → SharedPreferences.getString(key, "")
 *   "E"  → startActivity(ACTION_VIEW, Uri)    เปิด browser ภายนอก
 *   "F"  → return field i — cached string     (ต้นแบบ: deviceId/token cache → ที่นี่: cache ทั่วไป ไม่มี token)
 *   "H"  → install virtual app (AetherCore)
 *   "G"  → list/permission check (AetherCore)
 *
 * ตัดออก: code ที่ผูกกับระบบ login key/license ไม่มีในตาราง ไม่มี backend call ใด ๆ
 */
public final class CommandRouter {

    /** cache กลาง (parity: field i ของ Entry — ต้นแบบเก็บ token, ที่นี่เก็บ string ทั่วไป) */
    private static String cached = "";

    private CommandRouter() {}

    public static String dispatch(Context ctx, String code, Object arg) {
        switch (code) {
            case "0": {                                   // parity: "0" → Native.djp(int)
                byte[] blob = Native.djp(arg instanceof Integer ? (Integer) arg : 0);
                return blob == null ? "(null)" : "blob[" + blob.length + " B]";
            }
            case "A":                                     // parity: "A" → Toast
                Toast.makeText(ctx, String.valueOf(arg), Toast.LENGTH_SHORT).show();
                return "toast";
            case "B": {                                   // parity: "B" → batch prefs put
                @SuppressWarnings("unchecked")
                android.util.Pair<String, String> kv = (android.util.Pair<String, String>) arg;
                prefs(ctx).edit().putString(kv.first, kv.second).apply();
                return "put " + kv.first;
            }
            case "D":                                     // parity: "D" → prefs get
                return prefs(ctx).getString(String.valueOf(arg), "");
            case "E": {                                   // parity: "E" → ACTION_VIEW
                Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(String.valueOf(arg)));
                if (!(ctx instanceof Activity)) it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(it);
                return "view";
            }
            case "F":                                     // parity: "F" → cached field
                if (arg != null) cached = String.valueOf(arg);
                return cached;
            case "G":                                     // parity: "G" → AetherCore list/check
                return AetherCore.listInstalled(ctx).toString();
            case "H":                                     // parity: "H" → install (ต้นแบบ: core install routine)
                AetherCore.installFromUri(ctx, (Uri) arg);
                return "install-started";
            default:
                return null;                              // ต้นแบบ: unknown code → ไม่ทำอะไร
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("com.aether", Context.MODE_PRIVATE);
    }
}
