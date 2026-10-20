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

        showMessage("AetherEngine container\n\npreparing " + target + " …\n(คิว: install → plugin → boot)");

        // KOS-parity: งานหนักทั้งหมดอยู่บน worker (ดาวน์โหลด/คัดลอก/extract/dex) — main คืนค่าทันที ไม่มี ANR
        final String targetF = target;
        new Thread(() -> {
            if (getIntent() != null && getIntent().getBooleanExtra("install_device", false)) {
                String r = AetherCore.installFromDevice(this, targetF);
                android.util.Log.i("AetherEngine", "VStub install_device → " + r);
            }

            String status = GuestLauncher.launchInto(this, targetF);
            android.util.Log.i("AetherEngine", "VStub(" + targetF + ") → " + status);

            if (getIntent() != null && getIntent().getBooleanExtra("export_logs", false)) {
                String r = com.aether.util.LogDump.export(this);
                android.util.Log.i("AetherEngine", "VStub export_logs → " + r);
            }
            com.aether.util.LogDump.endSession(GuestLauncher.lastSession(), "final=" + status);

            final String uiStatus = status;
            runOnUiThread(() -> {
                if (!uiStatus.startsWith("container:launched") && !uiStatus.startsWith("container:booted-no-ui")) {
                    showMessage("AetherEngine container\n\n"
                            + "target: " + targetF + "\n\n" + uiStatus + "\n\n"
                            + "(ไม่เปิดแอปที่ติดตั้งภายนอก — รันจาก sandbox ของ container เท่านั้น)");
                }
            });
        }, "aether-vsstub-boot").start();
    }

    private void showMessage(String text) {
        TextView tv = new TextView(this);
        tv.setGravity(Gravity.CENTER);
        tv.setText(text);
        setContentView(tv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }
}
