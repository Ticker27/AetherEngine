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
 *   "I"  → image picker (activity result 1000 flow)
 *   "J"  → zip + share (4 KB buffer → chooser)
 *   "K"  → APK picker → install เข้า container
 *   "L"  → device fingerprint (reflection, อ่านอย่างเดียว)
 *   "M"  → launch แอปใน container ผ่าน stub activity
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
            case "I": {                                   // image picker (parity: onActivityResult 1000 flow)
                if (ctx instanceof Activity) {
                    Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                    pick.setType("image/*");
                    ((Activity) ctx).startActivityForResult(pick, 1000);
                }
                return "image-picker";
            }
            case "J": {                                   // zip + share (parity: j0) — arg=path, null → demo file
                try {
                    String path = arg == null ? null : String.valueOf(arg);
                    java.io.File src = (path != null && !path.isEmpty())
                            ? new java.io.File(path)
                            : com.aether.share.ZipShare.demoFile(ctx);
                    com.aether.share.ZipShare.share(ctx, src);
                    return "share-started";
                } catch (Exception e) {
                    return "ERR:" + e.getMessage();
                }
            }
            case "K": {                                   // APK picker → ติดตั้งเข้า container
                if (ctx instanceof Activity) {
                    Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                    pick.setType("application/vnd.android.package-archive");
                    ((Activity) ctx).startActivityForResult(pick, 1001);
                }
                return "apk-picker";
            }
            case "L":                                     // device fingerprint (reflection — อ่านอย่างเดียว)
                return com.aether.util.DeviceRegistry.summary(ctx);
            case "M":                                     // launch แอปใน container (route ผ่าน stub)
                AetherCore.launchInContainer(ctx, String.valueOf(arg));
                return "launch";
            default:
                return null;                              // ต้นแบบ: unknown code → ไม่ทำอะไร
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("com.aether", Context.MODE_PRIVATE);
    }
}
