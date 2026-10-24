package com.aether.client;

import android.content.Context;

/**
 * GuestLoaderBridge — สะพานจากชั้น hook (fake/) ไปยัง container/GuestLauncher
 * โดยไม่ให้ fake ต้องรู้รายละเอียดของ sandbox/plugin pipeline
 */
public final class GuestLoaderBridge {

    private GuestLoaderBridge() {}

    /** @return null เมื่อสำเร็จ · ข้อความ error เมื่อล้ม */
    public static String prepare(Context ctx, String pkg, boolean withPlugin) {
        return com.aether.container.GuestLauncher.prepare(ctx, pkg, withPlugin);
    }
}
