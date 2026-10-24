package com.aether.client;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

/**
 * ActivityFixer — พอร์ตจาก VirtualApp `client/fixer/ActivityFixer.java`
 *
 * สองอย่างที่ VA แก้ และถ้าไม่แก้จะเห็นเป็นอาการ "แอปขึ้นแต่หน้าจอเพี้ยน":
 *   1) windowShowWallpaper ของ theme ของเกม → ถ้า true ต้องตั้ง wallpaper เป็น background
 *      (ไม่งั้นพื้นหลังโปร่งใสเห็นหน้าจอ host ค้างอยู่)
 *   2) TaskDescription (label + icon) ของ task ที่เป็น root — ถ้าไม่ตั้ง
 *      recents จะโชว์ชื่อ container แทนชื่อเกม
 *
 * ที่นี่ใช้ android.R.attr แทน com.android.internal.R (ไม่ต้องพึ่ง hidden R)
 */
public final class ActivityFixer {

    private static final String TAG = "AetherEngine";

    private ActivityFixer() {}

    public static void fixActivity(Activity activity, Context baseContext) {
        if (activity == null) return;

        // 1) wallpaper background ตาม theme ของเกม
        try {
            TypedArray ta = activity.obtainStyledAttributes(
                    new int[]{android.R.attr.windowShowWallpaper});
            if (ta != null) {
                boolean showWallpaper = ta.getBoolean(0, false);
                ta.recycle();
                if (showWallpaper) {
                    activity.getWindow().setBackgroundDrawable(
                            WallpaperManager.getInstance(activity).getDrawable());
                }
            }
        } catch (Throwable t) {
            android.util.Log.w(TAG, "fixActivity: wallpaper → " + t);
        }

        // 2) TaskDescription: ให้ recents โชว์ชื่อ/ไอคอนของเกม
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                Intent intent = activity.getIntent();
                if (intent != null && activity.isTaskRoot() && baseContext != null) {
                    ApplicationInfo ai = baseContext.getApplicationInfo();
                    PackageManager pm = activity.getPackageManager();
                    String label = ai.loadLabel(pm) + "";
                    Bitmap icon = null;
                    Drawable d = ai.loadIcon(pm);
                    if (d instanceof BitmapDrawable) icon = ((BitmapDrawable) d).getBitmap();
                    activity.setTaskDescription(new ActivityManager.TaskDescription(label, icon));
                }
            } catch (Throwable t) {
                android.util.Log.w(TAG, "fixActivity: taskDescription → " + t);
            }
        }
    }
}
