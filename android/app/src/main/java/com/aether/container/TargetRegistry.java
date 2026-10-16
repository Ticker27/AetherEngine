package com.aether.container;

import java.util.ArrayList;
import java.util.List;

/**
 * TargetRegistry — ทะเบียนเป้าหมายเกมพร้อม version pin
 *
 * โครงเดียวกับทะเบียนของแบบจำลอง: entry = {name, icon, gameVersion(pin), moduleVersion, pkg}
 * (หลักฐาน: entry[0] Soccer Stars 37.1.1/36.5.2 · entry[1] Carrom Pool 19.5.2/18.5.1 ·
 *            entry[2] 8 Ball Pool 56.31.0/56.7.0)
 * ต่างจากต้นแบบ: ไม่มี hash/module จาก C2 — hash จะคำนวณและบันทึกเองตอนติดตั้ง (self-record)
 */
public final class TargetRegistry {

    public static final class Target {
        public final String id;
        public final String name;
        public final String pkg;
        public final String pinVersion;     // gameVersion ที่ pin ไว้ (56.31.0 สำหรับ 8BP)
        public final String moduleVersion;  // module ที่สอดคล้อง
        public final String icon;

        Target(String id, String name, String pkg, String pinVersion, String moduleVersion, String icon) {
            this.id = id;
            this.name = name;
            this.pkg = pkg;
            this.pinVersion = pinVersion;
            this.moduleVersion = moduleVersion;
            this.icon = icon;
        }
    }

    private static final List<Target> TARGETS = new ArrayList<>();

    static {
        TARGETS.add(new Target("eightballpool", "8 Ball Pool", "com.miniclip.eightballpool",
                "56.31.0", "56.7.0", "eightballpool.png"));
        TARGETS.add(new Target("carrom", "Carrom Pool", "com.miniclip.carrom",
                "19.5.2", "18.5.1", "carrom.png"));
        TARGETS.add(new Target("soccerstars", "Soccer Stars", "com.miniclip.soccerstars",
                "37.1.1", "36.5.2", "soccerstars.png"));
    }

    private TargetRegistry() {}

    public static List<Target> all() {
        return TARGETS;
    }

    public static Target byPkg(String pkg) {
        for (Target t : TARGETS) if (t.pkg.equals(pkg)) return t;
        return null;
    }
}
