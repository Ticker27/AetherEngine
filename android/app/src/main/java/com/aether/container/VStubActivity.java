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

        // KOS-parity: เกสต์ต้องมี Looper ตอนสร้าง Handler/Dialog — ใช้ HandlerThread คงยืนร่วมกับ lifecycle
        final String targetF = target;
        android.os.Handler h = BootThread.get();
        h.post(() -> {
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
        });
    }

    /** HandlerThread พื้นหลังมี Looper ของตัวเอง (guest สร้าง Handler ในนี้ได้) — คงยืนตลอด process */
    static final class BootThread {
        private static android.os.HandlerThread th = null;
        private static android.os.Handler handler = null;

        static synchronized android.os.Handler get() {
            if (handler == null) {
                th = new android.os.HandlerThread("aether-vsstub-boot",
                        android.os.Process.THREAD_PRIORITY_BACKGROUND);
                th.start();
                handler = new android.os.Handler(th.getLooper());
            }
            return handler;
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
