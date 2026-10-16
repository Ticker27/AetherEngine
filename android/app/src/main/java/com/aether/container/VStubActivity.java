package com.aether.container;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * VStubActivity — ประตูรัน guest "ใน container" (parity: ProxyActivity)
 *
 * กฎเหล็ก: ห้ามเปิดแอปที่ติดตั้งภายนอก (ไม่มี getLaunchIntentForPackage/startActivity ไปข้างนอก)
 * เปิดจาก sandbox APK (root/data/app/<pkg>/base.apk) ผ่าน GuestLauncher เท่านั้น
 */
public class VStubActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        String target = getIntent() != null ? getIntent().getStringExtra("target") : null;
        if (target == null) {
            showMessage("container:no-target");
            return;
        }

        showMessage("container: launching " + target + " …  (จาก sandbox)");
        String status = GuestLauncher.launchInto(this, target);
        android.util.Log.i("AetherEngine", "VStub(" + target + ") → " + status);

        // launched/booted-no-ui = guest ยก UI เข้าจอเองแล้ว (ผ่าน host.setContentView ภายใน GuestLauncher)
        if (!status.startsWith("container:launched") && !status.startsWith("container:booted-no-ui")) {
            showMessage("AetherEngine container\n\n"
                    + "target: " + target + "\n\n"
                    + status + "\n\n"
                    + "(ไม่เปิดแอปที่ติดตั้งภายนอก — รันจาก sandbox ของ container เท่านั้น)");
        }
    }

    private void showMessage(String text) {
        TextView tv = new TextView(this);
        tv.setGravity(Gravity.CENTER);
        tv.setText(text);
        setContentView(tv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }
}
