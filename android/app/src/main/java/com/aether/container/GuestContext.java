package com.aether.container;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;

import java.io.File;

/**
 * GuestContext — Context ปลอมของ guest ที่ชี้ทุก path เข้า sandbox
 *
 * ลำดับชั้น: guest Application.attachBaseContext(GuestContext) → packer init ของเกม
 * จะเห็น: packageName/applicationInfo/classLoader/resources/filesDir ของตัวเองใน sandbox
 * (parity: path mapping ของแบบจำลอง — guest เห็น /data/user/0/<pkg> แต่เก็บจริงใต้ root/)
 */
public class GuestContext extends ContextWrapper {

    private final String pkg;
    private final ApplicationInfo ai;
    private final ClassLoader loader;
    private final Resources res;
    private final File dataRoot;   // root/data/user/0/<pkg>

    public GuestContext(Context base, String pkg, ApplicationInfo ai, ClassLoader loader,
                        Resources res, File dataRoot) {
        super(base);
        this.pkg = pkg;
        this.ai = ai;
        this.loader = loader;
        this.res = res;
        this.dataRoot = dataRoot;
    }

    private static File ensure(File f) {
        if (!f.exists()) f.mkdirs();
        return f;
    }

    @Override public String getPackageName() { return pkg; }
    @Override public String getOpPackageName() { return pkg; }
    @Override public ApplicationInfo getApplicationInfo() { return ai != null ? ai : super.getApplicationInfo(); }
    @Override public ClassLoader getClassLoader() { return loader != null ? loader : super.getClassLoader(); }
    @Override public Resources getResources() { return res != null ? res : super.getResources(); }
    @Override public AssetManager getAssets() { return res != null ? res.getAssets() : super.getAssets(); }

    @Override public File getFilesDir() { return ensure(new File(dataRoot, "files")); }
    @Override public File getCacheDir() { return ensure(new File(dataRoot, "cache")); }
    @Override public File getNoBackupFilesDir() { return ensure(new File(dataRoot, "no_backup")); }
    @Override public File getCodeCacheDir() { return ensure(new File(dataRoot, "code_cache")); }
    @Override public File getDatabasePath(String name) {
        return new File(ensure(new File(dataRoot, "databases")), name);
    }
    @Override public File getDir(String name, int mode) { return ensure(new File(dataRoot, "app_" + name)); }
    @Override public File getExternalFilesDir(String type) {
        return ensure(new File(dataRoot, "external" + (type == null ? "" : File.separator + type)));
    }
    @Override public File getExternalCacheDir() { return ensure(new File(dataRoot, "external_cache")); }
}
