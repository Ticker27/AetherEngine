package com.aether.fake.delegate;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;

import com.aether.client.ContextFixer;
import com.aether.client.GuestIdentity;
import com.aether.client.GuestLoaderBridge;
import com.aether.client.GuestResources;
import com.aether.remote.StubActivityRecord;

/**
 * AppInstrumentation — Instrumentation ตัวทดแทน (parity: KOS AppInstrumentation / VA InstrumentationDelegate)
 *
 * กลไก: สลับ ActivityThread.mInstrumentation → ที่นี่
 *   1) newActivity(loader, className, intent)
 *      - ถ้าเกสต์ใน process นี้ยังไม่ถูกเตรียม → เตรียมเลย (จุดแรกที่รู้ชื่อ package จากซอง)
 *      - โหลดคลาสจาก guest loader (คลาสของเกม) แทน loader ของ host
 *   2) callActivityOnCreate
 *      - ติดตั้ง Resources ของเกม (base+split) ก่อน แล้วค่อย fix Context
 *      - เหตุผล: onCreate ของเกมเรียก setContentView ด้วย resource id ของเกม
 *        ถ้า Resources ยังเป็นของ host → Resources$NotFoundException
 *   3) method อื่นทั้งหมด delegate ไป base (ActivityThread เรียกจุดเดียวสำหรับ lifecycle)
 */
public class AppInstrumentation extends Instrumentation {

    private static final String TAG = "AetherEngine";

    private final Instrumentation base;
    private final Context hostCtx;

    public AppInstrumentation(Instrumentation base) {
        this(base, null);
    }

    public AppInstrumentation(Instrumentation base, Context ctx) {
        this.base = base;
        this.hostCtx = ctx;
    }

    /** คงไว้เพื่อความเข้ากันได้กับ call site เดิม */
    public void setGuest(ClassLoader loader, String pkg) {
        // ไม่ต้องใช้แล้ว — GuestIdentity ถือ loader/pkg ของ process นี้
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        // 1) เตรียมเกสต์ถ้ายังว่าง (process เพิ่งเกิดจาก proxy component)
        if (!GuestIdentity.isPrepared() && intent != null) {
            StubActivityRecord r = new StubActivityRecord(intent);
            if (r.info != null && r.info.packageName != null) {
                Context ctx = hostCtx != null ? hostCtx : null;
                if (ctx != null) {
                    String err = GuestLoaderBridge.prepare(ctx, r.info.packageName, false);
                    android.util.Log.i(TAG, "fake/instr: lazy prepare(" + r.info.packageName
                            + ") → " + (err == null ? "ok" : err));
                }
            }
        }
        // 2) โหลดคลาสของเกม
        if (GuestIdentity.isPrepared()) {
            String real = className;
            if (className != null && className.contains("ProxyActivity") && intent != null) {
                StubActivityRecord r = new StubActivityRecord(intent);
                if (r.info != null && r.info.name != null) real = r.info.name;
            }
            if (real != null && !real.contains("ProxyActivity")) {
                try {
                    Activity a = (Activity) GuestIdentity.guestLoader()
                            .loadClass(real).getDeclaredConstructor().newInstance();
                    android.util.Log.i(TAG, "fake/instr: newActivity(guest) " + real);
                    return a;
                } catch (Throwable t) {
                    android.util.Log.w(TAG, "fake/instr: guest newActivity failed " + real + ": " + t);
                }
            }
        }
        return base.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        if (GuestIdentity.isPrepared() && isGuestActivity(activity)) {
            GuestResources.applyTo(activity, hostCtx, GuestIdentity.guestPkg());
            ContextFixer.fixContext(activity, GuestIdentity.hostPkg());
            com.aether.client.ActivityFixer.fixActivity(activity, hostCtx);
        }
        base.callActivityOnCreate(activity, icicle);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle,
                                     PersistableBundle persistentState) {
        if (GuestIdentity.isPrepared() && isGuestActivity(activity)) {
            GuestResources.applyTo(activity, hostCtx, GuestIdentity.guestPkg());
            ContextFixer.fixContext(activity, GuestIdentity.hostPkg());
            com.aether.client.ActivityFixer.fixActivity(activity, hostCtx);
        }
        base.callActivityOnCreate(activity, icicle, persistentState);
    }

    @Override
    public void callActivityOnResume(Activity activity) {
        if (GuestIdentity.isPrepared() && isGuestActivity(activity)) {
            ContextFixer.fixContext(activity, GuestIdentity.hostPkg());
        }
        base.callActivityOnResume(activity);
    }

    // ── delegate ที่เหลือ ────────────────────────────────────────
    @Override
    public Application newApplication(ClassLoader cl, String className, Context context)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return base.newApplication(cl, className, context);
    }
    @Override public void callActivityOnStart(Activity a) { base.callActivityOnStart(a); }
    @Override public void callActivityOnRestart(Activity a) { base.callActivityOnRestart(a); }
    @Override public void callActivityOnPause(Activity a) { base.callActivityOnPause(a); }
    @Override public void callActivityOnStop(Activity a) { base.callActivityOnStop(a); }
    @Override public void callActivityOnDestroy(Activity a) { base.callActivityOnDestroy(a); }
    @Override public void callActivityOnSaveInstanceState(Activity a, Bundle b) {
        base.callActivityOnSaveInstanceState(a, b);
    }
    @Override public void callActivityOnRestoreInstanceState(Activity a, Bundle b) {
        base.callActivityOnRestoreInstanceState(a, b);
    }
    @Override public void callActivityOnNewIntent(Activity a, Intent i) {
        base.callActivityOnNewIntent(a, i);
    }
    @Override public void callApplicationOnCreate(Application app) {
        base.callApplicationOnCreate(app);
    }
    @Override public Context getContext() { return base.getContext(); }
    @Override public void setInTouchMode(boolean v) { base.setInTouchMode(v); }
    @Override public void waitForIdleSync() { base.waitForIdleSync(); }
    @Override public void runOnMainSync(Runnable r) { base.runOnMainSync(r); }

    private boolean isGuestActivity(Activity a) {
        return a != null && GuestIdentity.guestLoader() != null
                && a.getClass().getClassLoader() == GuestIdentity.guestLoader();
    }

    /** ติดตั้ง: แทน ActivityThread.mInstrumentation */
    public static boolean install(Context ctx) {
        try {
            Class<?> atCls = Class.forName("android.app.ActivityThread");
            Object at = atCls.getMethod("currentActivityThread").invoke(null);
            java.lang.reflect.Field f = atCls.getDeclaredField("mInstrumentation");
            f.setAccessible(true);
            Object cur = f.get(at);
            if (cur instanceof AppInstrumentation) return true;
            if (!(cur instanceof Instrumentation)) {
                android.util.Log.w(TAG, "fake/instr: mInstrumentation = " + cur);
                return false;
            }
            f.set(at, new AppInstrumentation((Instrumentation) cur, ctx));
            android.util.Log.i(TAG, "fake/instr: mInstrumentation → AppInstrumentation (base="
                    + cur.getClass().getSimpleName() + ")");
            return true;
        } catch (Throwable t) {
            android.util.Log.w(TAG, "fake/instr: install failed: " + t);
            return false;
        }
    }
}
