package com.aether.fake.delegate;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;

/**
 * AppInstrumentation — Instrumentation ตัวทดแทน (โครงเดียวกับ
 * com.kos.engine.fake.delegate.AppInstrumentation ของ KOS)
 *
 * กลไก: สลับ ActivityThread.mInstrumentation → ที่นี่
 *   - newActivity(ClassLoader, String, Intent): ถ้าโฮสต์โหลดคลาสไม่ได้ → โหลดจาก guest loader
 *     (= ประตูที่ทำให้ Activity ของเกสต์ถูกสร้างผ่าน Instrumentation ไม่ใช่ startActivity ตรง ๆ)
 *   - lifecycle callbacks: delegate ไป base เพื่อไม่ทำลายพฤติกรรมเดิม
 */
public class AppInstrumentation extends Instrumentation {

    private static final String TAG = "AetherEngine";

    private Instrumentation base;
    private ClassLoader guestLoader;
    private String targetPkg;

    public AppInstrumentation(Instrumentation base) {
        this.base = base;
    }

    public void setGuest(ClassLoader loader, String pkg) {
        this.guestLoader = loader;
        this.targetPkg = pkg;
    }

    @Override
    public Application newApplication(ClassLoader cl, String className, Context context)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return base.newApplication(cl, className, context);
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        try {
            return base.newActivity(cl, className, intent);
        } catch (ClassNotFoundException e) {
            if (guestLoader != null) {
                try {
                    Class<?> c = guestLoader.loadClass(className);
                    Activity a = (Activity) c.getDeclaredConstructor().newInstance();
                    a.setIntent(intent);
                    android.util.Log.i(TAG, "fake/instr: newActivity(guest) " + className);
                    return a;
                } catch (Throwable t) {
                    android.util.Log.w(TAG, "fake/instr: guest newActivity failed " + className + ": " + t);
                }
            }
            throw e;
        }
    }

    @Override
    public void callApplicationOnCreate(Application app) {
        base.callApplicationOnCreate(app);
    }

    @Override
    public Activity newActivity(Class<?> clazz, Context context, IBinder token,
                                Application application, Intent intent, ActivityInfo info,
                                CharSequence title, Activity parent, String id,
                                Object lastNonConfigurationInstance)
            throws IllegalAccessException, InstantiationException {
        return base.newActivity(clazz, context, token, application, intent, info, title,
                parent, id, lastNonConfigurationInstance);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        base.callActivityOnCreate(activity, icicle);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle, PersistableBundle persistentState) {
        base.callActivityOnCreate(activity, icicle, persistentState);
    }

    @Override
    public void callActivityOnNewIntent(Activity activity, Intent intent) {
        base.callActivityOnNewIntent(activity, intent);
    }

    @Override
    public void callActivityOnStart(Activity activity) {
        base.callActivityOnStart(activity);
    }

    @Override
    public void callActivityOnRestart(Activity activity) {
        base.callActivityOnRestart(activity);
    }

    @Override
    public void callActivityOnResume(Activity activity) {
        base.callActivityOnResume(activity);
    }

    @Override
    public void callActivityOnPause(Activity activity) {
        base.callActivityOnPause(activity);
    }

    @Override
    public void callActivityOnStop(Activity activity) {
        base.callActivityOnStop(activity);
    }

    @Override
    public void callActivityOnDestroy(Activity activity) {
        base.callActivityOnDestroy(activity);
    }

    @Override
    public void callActivityOnSaveInstanceState(Activity activity, Bundle outState) {
        base.callActivityOnSaveInstanceState(activity, outState);
    }

    @Override
    public void callActivityOnSaveInstanceState(Activity activity, Bundle outState,
                                                PersistableBundle outPersistentState) {
        base.callActivityOnSaveInstanceState(activity, outState, outPersistentState);
    }

    @Override
    public void callActivityOnRestoreInstanceState(Activity activity, Bundle savedInstanceState) {
        base.callActivityOnRestoreInstanceState(activity, savedInstanceState);
    }

    @Override
    public void callActivityOnRestoreInstanceState(Activity activity, Bundle savedInstanceState,
                                                   PersistableBundle persistentState) {
        base.callActivityOnRestoreInstanceState(activity, savedInstanceState, persistentState);
    }

    @Override
    public void callActivityOnUserLeaving(Activity activity) {
        base.callActivityOnUserLeaving(activity);
    }
}
