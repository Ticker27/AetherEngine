package com.aether.proxy;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.aether.client.GuestIdentity;
import com.aether.remote.StubActivityRecord;

/**
 * ProxyActivity — stub activity ของ container (ฐานของ P0–P3 / P0L–P3L)
 *
 * เปลี่ยนจาก "จอปลายทาง" เป็น "ประตู" ตามกลไก VirtualApp:
 *   onCreate → super.onCreate(null) → finish()
 *   → ถอด StubActivityRecord (`_VA_|_intent_` / `_VA_|_info_`) ออกจาก intent
 *   → ถ้า process ตรงกับ info.processName → startActivity(อินเทนต์เกสต์) อีกครั้ง
 *     (รอบนี้ HCallback ที่ติดตั้งไว้แล้วจะสลับ activityInfo เป็นของเกสต์
 *      → AppInstrumentation สร้างคลาสของเกม → onCreate ของเกมทำงาน)
 *   → ถ้าไม่ตรง → เปิด stub ตัวที่ผูก process เป้าหมาย
 *
 * หมายเหตุ: ในเส้นทางปกติ (hook ติด) ProxyActivity จะไม่ถูกเรียกเลย เพราะ HCallback
 * สลับคลาสก่อน framework สร้าง — คลาสนี้จึงเป็น fallback + หลักประกันว่า process :pN ตื่น
 */
public class ProxyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);
        finish();

        StubActivityRecord r = new StubActivityRecord(getIntent());
        if (r.intent == null) {
            renderStub("ProxyActivity", "no-stashed-intent");
            return;
        }

        String here = GuestIdentity.currentProcessName(this);
        String target = r.info != null && r.info.processName != null
                ? r.info.processName : here;

        if (r.retry >= 2) {
            renderStub("ProxyActivity", "retry-limit — HCallback/Instrumentation ไม่ทำงาน");
            android.util.Log.e("AetherEngine", "proxy: retry limit; hook ไม่ติดใน " + here);
            return;
        }
        r.intent.putExtra(StubActivityRecord.KEY_RETRY, r.retry + 1);

        if (TextUtils.equals(target, here)) {
            r.intent.setExtrasClassLoader(getClassLoader());
            startActivity(r.intent);
        } else {
            GuestIdentity.requestCrossProcessLaunch(this, r);
        }
    }

    /** overload เดิมของ repo (ProxyPendingActivity / TransparentProxyActivity เรียกด้วย 1 arg) */
    protected void renderStub(String kind) {
        renderStub(kind, "");
    }

    protected void renderStub(String kind, String note) {
        TextView tv = new TextView(this);
        tv.setGravity(Gravity.CENTER);
        tv.setText(note.isEmpty() ? kind + "\n" + getLocalClassName()
                : kind + "\n" + getLocalClassName() + "\n" + note);
        setContentView(tv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    // ── :p0 ──
    public static class P0 extends ProxyActivity {}
    public static class P0L extends ProxyActivity {}

    // ── :p1 ──
    public static class P1 extends ProxyActivity {}
    public static class P1L extends ProxyActivity {}

    // ── :p2 ──
    public static class P2 extends ProxyActivity {}
    public static class P2L extends ProxyActivity {}

    // ── :p3 ──
    public static class P3 extends ProxyActivity {}
    public static class P3L extends ProxyActivity {}
}
