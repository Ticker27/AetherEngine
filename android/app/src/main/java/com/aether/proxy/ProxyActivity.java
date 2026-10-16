package com.aether.proxy;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * ProxyActivity — stub activity ของ container (ฐานของ P0–P3 / P0L–P3L)
 *
 * โครงเดียวกับแบบจำลอง: process ละชุด (:p0–:p3) + landscape variant แยก
 * แบบจำลองเต็มจะ route เข้า activity จริงของแอปเป้าหมายผ่าน framework hook;
 * ที่นี่เป็นโครง stub ที่แสดงสถานะ + log เพื่อให้โครง process/component ครบ
 */
public class ProxyActivity extends Activity {

    protected void renderStub(String kind) {
        String target = getIntent() != null ? getIntent().getStringExtra("target") : null;
        String mode = getIntent() != null ? getIntent().getStringExtra("mode") : null;
        TextView tv = new TextView(this);
        tv.setGravity(Gravity.CENTER);
        tv.setText("AetherEngine " + kind
                + "\n" + getLocalClassName()
                + "\ntarget: " + (target == null ? "(none)" : target)
                + "\nmode: " + (mode == null ? "-" : mode));
        setContentView(tv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        android.util.Log.i("AetherEngine", kind + " " + getLocalClassName() + " target=" + target);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        renderStub("ProxyActivity");
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
