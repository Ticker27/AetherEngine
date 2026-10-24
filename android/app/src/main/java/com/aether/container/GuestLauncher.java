package com.aether.container;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;

import com.aether.client.ComponentResolver;
import com.aether.client.GuestIdentity;
import com.aether.client.GuestRuntime;
import com.aether.fake.hook.HookManager;
import com.aether.helper.Native;
import com.aether.remote.StubActivityRecord;
import com.aether.util.LogDump;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * GuestLauncher — เตรียม environment ของเกสต์ใน process นี้ แล้วเปิดผ่าน proxy component
 *
 * เปลี่ยนจากของเดิม (v1): ไม่สร้าง Activity เองด้วย `new + Activity.attach()` อีก
 * เพราะการข้าม ActivityThread ทำให้ไม่มี ActivityClientRecord/mToken →
 * WindowManager.BadTokenException และ lifecycle ไม่ถูก framework เรียก
 *
 * ลำดับใหม่ (parity VirtualApp: เตรียม env → สั่งเปิดผ่าน proxy component):
 *   1. sandbox apk มีจริง
 *   2. แตก native จาก base + split → sandbox lib
 *   3. DexClassLoader(base+split, oat, lib, hostLoader)
 *   4. plugin pipeline (เฉพาะเมื่อ withPlugin — กัน ANR เมื่อเรียกบน main thread)
 *   5. ApplicationInfo ชี้ sandbox + setenv HOME
 *   6. GuestIdentity.bind (1 process = 1 guest) + adoptProcessIdentity
 *   7. GuestRuntime.boot → ContextFixer → Application.attachBaseContext → providers → onCreate
 *   8. launch: resolve launcher ActivityInfo → ProxyActivity$P0 + ซอง `_VA_|_intent_` → startActivity
 */
public final class GuestLauncher {

    public static final String TAG = "AetherEngine";

    private static java.io.File sSession;

    private GuestLauncher() {}

    public static java.io.File lastSession() {
        return sSession != null ? sSession : GuestIdentity.session();
    }

    /** @return null เมื่อสำเร็จ · ข้อความ error เมื่อล้ม */
    public static String prepare(Context ctx, String pkg) {
        return prepare(ctx, pkg, true);
    }

    public static String prepare(Context ctx, String pkg, boolean withPlugin) {
        File apk = SandboxStore.guestApk(ctx, pkg);
        if (!apk.exists()) return "container:not-installed(sandbox apk missing)";
        if (GuestIdentity.isPrepared() && !pkg.equals(GuestIdentity.guestPkg())) {
            return "container:process-busy(" + GuestIdentity.guestPkg() + ")";
        }

        // package ของ container ต้องจำไว้ก่อน ContextFixer จะแก้ Context
        GuestIdentity.setHostPkg(ctx.getPackageName());

        sSession = LogDump.startSession(ctx, pkg);
        GuestIdentity.setSession(sSession);
        LogDump.stage(sSession, "[session] prepare " + pkg + " apk=" + apk.length() + "B");

        if (apk.canWrite()) VPackageInstaller.markReadOnly(apk);

        SandboxStore.ensureGuestLayout(ctx, pkg);
        File libDir = SandboxStore.guestLibDir(ctx, pkg);

        int n = extractNatives(apk, libDir);
        LogDump.stage(sSession, "[session] natives " + n + " libs → " + libDir);

        StringBuilder cp = new StringBuilder(apk.getAbsolutePath());
        File[] apks = SandboxStore.guestDir(ctx, pkg).listFiles((d, s) -> s.endsWith(".apk"));
        if (apks != null) {
            for (File f : apks) {
                if (f.getAbsolutePath().equals(apk.getAbsolutePath())) continue;
                cp.append(':').append(f.getAbsolutePath());
            }
        }
        File oat = new File(new File(SandboxStore.root(ctx), "cache/oat"), pkg);
        if (!oat.exists()) oat.mkdirs();

        ClassLoader guestLoader;
        try {
            guestLoader = new dalvik.system.DexClassLoader(cp.toString(), oat.getAbsolutePath(),
                    libDir.getAbsolutePath(), GuestLauncher.class.getClassLoader());
        } catch (Throwable t) {
            return "container:classloader-failed:" + t;
        }
        LogDump.stage(sSession, "[session] classloader ready (base+splits)");

        if (withPlugin) {
            try {
                String pl = PluginInstaller.ensurePlugin(ctx, pkg, 45000L);
                LogDump.stage(sSession, "[plugin] ensure → " + pl);
                PluginLoader.Result lr = PluginLoader.load(ctx, pkg, guestLoader);
                LogDump.stage(sSession, "[plugin] loader → " + lr);
            } catch (Throwable t) {
                LogDump.stage(sSession, "[plugin] error " + t);
            }
        } else {
            LogDump.stage(sSession, "[plugin] skipped (lazy prepare)");
        }

        android.content.pm.ApplicationInfo ai = ComponentResolver.guestApplicationInfo(ctx, pkg, null);
        if (ai == null) return "container:no-appinfo";

        try {
            File home = SandboxStore.guestUserDataDir(ctx, pkg);
            if (Native.isAvailable()) {
                com.aether.helper.Native.setEnv("HOME", home.getAbsolutePath());
            }
            File library = new File(home, "Library");
            if (!library.exists()) library.mkdirs();
        } catch (Throwable t) {
            android.util.Log.w(TAG, "HOME setup: " + t);
        }

        try {
            GuestIdentity.bind(pkg, apk.getAbsolutePath(), guestLoader, ai);
        } catch (IllegalStateException e) {
            return "container:process-busy:" + e.getMessage();
        }
        LogDump.stage(sSession, "[session] guest bound in " + GuestIdentity.currentProcessName(ctx));

        try {
            GuestIdentity.adoptProcessIdentity(ctx);
            LogDump.stage(sSession, "[session] process identity adopted → " + pkg);
        } catch (Throwable t) {
            LogDump.stage(sSession, "[session] adopt skipped: " + t);
        }

        // Application + ContentProvider ของเกสต์ (packer init ที่ชั้น provider)
        String rtErr = GuestRuntime.boot(ctx);
        if (rtErr != null) return rtErr;

        LogDump.stage(sSession, "[session] prepare done");
        return null;
    }

    /**
     * เปิด launcher activity ผ่าน proxy component — framework เป็นคนสร้าง Activity
     * ต้องเรียกจาก main process (ที่ติดตั้ง AMS proxy แล้ว) หรือจาก :pN เมื่อสั่งข้าม process
     */
    public static String launchViaStub(Context ctx, String pkg) {
        File apk = SandboxStore.guestApk(ctx, pkg);
        if (!apk.exists()) return "container:not-installed";

        ActivityInfo info = ComponentResolver.resolveLauncher(ctx, pkg);
        if (info == null) return "container:no-launcher-activity";

        Intent guest = new Intent(Intent.ACTION_MAIN)
                .setClassName(pkg, info.name)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        Intent stub = GuestIdentity.makeStubIntent(ctx, guest, info, 0);
        stub.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(stub);

        LogDump.engine(ctx, "launch → " + stub.getComponent().flattenToShortString()
                + " for " + info.name);
        return "container:launched(" + pkg + "/" + info.name + ")"
                + " via " + stub.getComponent().getClassName();
    }

    /**
     * คง signature เดิมของ repo (เรียกจาก VStubActivity) — ตอนนี้ทำ prepare + launch ผ่าน proxy
     * ไม่สร้าง Activity เองแล้ว
     */
    public static String launchInto(android.app.Activity host, String pkg) {
        String err = prepare(host, pkg, false);
        if (err != null) return err;
        return launchViaStub(host, pkg);
    }

    /** แตก lib/arm64-v8a/*.so จากทุก apk ใน guestDir → libDir (ทำครั้งเดียว) */
    private static int extractNatives(File baseApk, File libDir) {
        File[] have = libDir.listFiles((d, f) -> f.endsWith(".so"));
        if (have != null && have.length > 0) return have.length;

        List<File> apks = new ArrayList<>();
        File[] all = baseApk.getParentFile() != null
                ? baseApk.getParentFile().listFiles((d, f) -> f.endsWith(".apk")) : null;
        if (all != null) java.util.Collections.addAll(apks, all);
        if (apks.isEmpty()) apks.add(baseApk);

        int ok = 0;
        for (File apk : apks) {
            try (ZipFile zf = new ZipFile(apk)) {
                java.util.Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String name = e.getName();
                    if (!name.startsWith("lib/arm64-v8a/") || !name.endsWith(".so")) continue;
                    File out = new File(libDir, name.substring(name.lastIndexOf('/') + 1));
                    try (InputStream in = zf.getInputStream(e);
                         FileOutputStream os = new FileOutputStream(out)) {
                        byte[] buf = new byte[65536];
                        int r;
                        while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
                    }
                    out.setReadable(true, false);
                    out.setExecutable(true, false);
                    ok++;
                }
            } catch (Throwable t) {
                android.util.Log.w(TAG, "extract " + apk.getName() + ": " + t);
            }
        }
        return ok;
    }
}
