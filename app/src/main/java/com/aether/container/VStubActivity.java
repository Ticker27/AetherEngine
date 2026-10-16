package com.aether.container;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * parity: ProxyActivity$P0 / ProxyActivity (stub ของ container framework ของแบบจำลอง :p0)
 *
 * ต้นแบบ: stub activity เปล่า ๆ เป็น "หุ่น" ที่ container framework ของแบบจำลอง ใช้ปลอมตัวรัน Activity ของแอป clone
 *   (รับ extra แล้วไปเรียก activity จริงของ target ผ่าน hook ของ framework)
 *
 * AetherEngine: stub แบบ structural — อ่าน extra "target" แล้วลองเปิด launcher activity ของ target
 * ด้วย intent ปกติ (ไม่มี hook); แอปที่ไม่มี launcher จะแสดง placeholder บนหน้า stub เอง
 */
public class VStubActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        String target = getIntent().getStringExtra("target");
        TextView tv = new TextView(this);
        tv.setGravity(Gravity.CENTER);
        tv.setText("AetherEngine container stub\n\ntarget: " + (target == null ? "(none)" : target)
                + "\n(โครง stub เดียวกับ ProxyActivity$P0 — ต้นแบบใช้ framework hook รัน activity จริงของ target)");
        setContentView(tv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        if (target != null) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(target);
            if (launch != null) {
                // structural parity: ในต้นแบบ การ "เปิด" ถูก route ผ่าน VActivityManager/stub — ที่นี่เปิดตรง
                startActivity(launch);
            }
        }
    }
}
