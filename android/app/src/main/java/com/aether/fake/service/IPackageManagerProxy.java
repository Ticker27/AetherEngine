package com.aether.fake.service;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.text.TextUtils;

import com.aether.client.ComponentResolver;
import com.aether.client.GuestIdentity;
import com.aether.container.SandboxStore;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * IPackageManagerProxy — ActivityThread.sPackageManager → dynamic proxy
 * (พอร์ตชุด method ที่ VirtualApp `client/hook/proxies/pm/MethodProxies.java` forge)
 *
 * เหตุผล: package ของเกสต์ไม่มีใน PMS ของเครื่อง — framework ถาม PMS ทุกครั้งที่
 *    สร้าง activity/service/provider, ตรวจ permission, หา uid
 * ถ้าไม่ตอบจาก sandbox → NameNotFoundException/SecurityException ทันทีที่เกมเริ่มทำงาน
 *
 * method ที่ครอบ (คัดจาก VA: GetPackageInfo / GetActivityInfo / GetServiceInfo /
 * ResolveContentProvider / ResolveService / ResolveIntent / QueryIntentActivities /
 * QueryIntentServices / IsPackageAvailable / GetPackageUid / GetInstallerPackageName /
 * CheckPermission)
 *
 * หลักการเดียวกับ VA: ถ้า argument ไม่ใช่ของเกสต์ → passthrough ไปของจริง
 */
public final class IPackageManagerProxy implements InvocationHandler {

    private static final String TAG = "AetherEngine";

    private static final String FAKE_INSTALLER = "com.android.vending";

    private final Object mBase;
    private final android.content.Context mHost;

    public IPackageManagerProxy(Object base, android.content.Context host) {
        mBase = base;
        mHost = host;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if (GuestIdentity.isPrepared()) {
            String pkg = GuestIdentity.guestPkg();
            Object forged = tryForge(name, method, args, pkg);
            if (forged != NOT_FORGED) return forged;
        }
        try {
            return method.invoke(mBase, args);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            throw ite.getCause();
        }
    }

    private static final Object NOT_FORGED = new Object();

    private Object tryForge(String name, Method method, Object[] args, String pkg) {
        try {
            switch (name) {
                case "getPackageInfo":
                case "getPackageInfoVersioned": {
                    String asked = firstString(args);
                    if (isGuest(asked, pkg)) {
                        PackageInfo pi = packageInfo(pkg);
                        if (pi != null) return pi;
                    }
                    return NOT_FORGED;
                }
                case "getApplicationInfo": {
                    String asked = firstString(args);
                    if (isGuest(asked, pkg)) {
                        return ComponentResolver.guestApplicationInfo(mHost, pkg, packageInfo(pkg));
                    }
                    return NOT_FORGED;
                }
                case "getActivityInfo": {
                    ComponentName cn = firstComponent(args);
                    if (cn != null && isGuest(cn.getPackageName(), pkg)) {
                        ActivityInfo ai = ComponentResolver.resolveActivityInfo(
                                mHost, pkg, cn.getClassName());
                        if (ai != null) return ai;
                    }
                    return NOT_FORGED;
                }
                case "getServiceInfo": {
                    ComponentName cn = firstComponent(args);
                    if (cn != null && isGuest(cn.getPackageName(), pkg)) {
                        ServiceInfo si = ComponentResolver.resolveServiceInfo(
                                mHost, pkg, cn.getClassName());
                        if (si != null) return si;
                    }
                    return NOT_FORGED;
                }
                case "getProviderInfo": {
                    ComponentName cn = firstComponent(args);
                    if (cn != null && isGuest(cn.getPackageName(), pkg)) {
                        ProviderInfo pi = ComponentResolver.resolveProviderInfo(mHost, pkg, null);
                        if (pi != null) return pi;
                    }
                    return NOT_FORGED;
                }
                case "resolveContentProvider": {
                    String authority = firstString(args);
                    ProviderInfo pi = ComponentResolver.resolveProviderInfo(mHost, pkg, authority);
                    if (pi != null) return pi;
                    return NOT_FORGED;
                }
                case "resolveIntent":
                case "resolveService": {
                    String asked = firstString(args);
                    if (isGuest(asked, pkg)) {
                        android.util.Log.i(TAG, "fake/pms: " + name + "(" + asked + ") → empty ResolveInfo");
                        return new ResolveInfo();
                    }
                    return NOT_FORGED;
                }
                case "queryIntentActivities":
                case "queryIntentServices": {
                    String asked = firstString(args);
                    if (isGuest(asked, pkg)) {
                        return new ArrayList<ResolveInfo>();
                    }
                    return NOT_FORGED;
                }
                case "isPackageAvailable": {
                    String asked = firstString(args);
                    if (isGuest(asked, pkg)) return Boolean.TRUE;
                    return NOT_FORGED;
                }
                case "getPackageUid": {
                    String asked = firstString(args);
                    if (isGuest(asked, pkg)) {
                        return android.os.Process.myUid();
                    }
                    return NOT_FORGED;
                }
                case "getInstallerPackageName": {
                    String asked = firstString(args);
                    if (isGuest(asked, pkg)) return FAKE_INSTALLER;
                    return NOT_FORGED;
                }
                case "checkPermission":
                    return PackageManager.PERMISSION_GRANTED;
                default:
                    return NOT_FORGED;
            }
        } catch (Throwable t) {
            android.util.Log.w(TAG, "fake/pms: forge " + name + " failed: " + t);
            return NOT_FORGED;
        }
    }

    private PackageInfo packageInfo(String pkg) {
        try {
            String apk = SandboxStore.guestApk(mHost, pkg).getAbsolutePath();
            PackageInfo pi = mHost.getPackageManager().getPackageArchiveInfo(apk,
                    PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                            | PackageManager.GET_PROVIDERS | PackageManager.GET_RECEIVERS
                            | PackageManager.GET_SIGNING_CERTIFICATES);
            if (pi == null) return null;
            pi.packageName = pkg;
            pi.applicationInfo = ComponentResolver.guestApplicationInfo(mHost, pkg, pi);
            return pi;
        } catch (Throwable t) {
            android.util.Log.w(TAG, "fake/pms: packageInfo(" + pkg + ") → " + t);
            return null;
        }
    }

    private static boolean isGuest(String asked, String pkg) {
        return asked != null && TextUtils.equals(asked, pkg);
    }

    private static String firstString(Object[] args) {
        if (args == null) return null;
        for (Object o : args) if (o instanceof String) return (String) o;
        return null;
    }

    private static ComponentName firstComponent(Object[] args) {
        if (args == null) return null;
        for (Object o : args) if (o instanceof ComponentName) return (ComponentName) o;
        return null;
    }

    /** ติดตั้ง: แทน ActivityThread.sPackageManager */
    public static boolean install(android.content.Context host) {
        try {
            Class<?> atCls = Class.forName("android.app.ActivityThread");
            Field f = atCls.getDeclaredField("sPackageManager");
            f.setAccessible(true);
            Object base = f.get(null);
            if (base == null) {
                android.util.Log.w(TAG, "fake/pms: sPackageManager = null");
                return false;
            }
            if (java.lang.reflect.Proxy.isProxyClass(base.getClass())) return true;
            Class<?> iface = Class.forName("android.content.pm.IPackageManager");
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(iface.getClassLoader(),
                    new Class<?>[]{iface}, new IPackageManagerProxy(base, host));
            f.set(null, proxy);
            android.util.Log.i(TAG, "fake/pms: sPackageManager proxied");
            return true;
        } catch (Throwable t) {
            android.util.Log.w(TAG, "fake/pms: install failed: " + t);
            return false;
        }
    }
}
