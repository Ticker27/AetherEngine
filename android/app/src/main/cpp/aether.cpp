// AetherEngine — libaether.so
// โครงสร้างพลิกจากต้นแบบ native engine ของแบบจำลองอ้างอิง:
//   1) ไม่มี export Java_com_* — ผูก method ผ่าน JNI_OnLoad → RegisterNatives (parity เดิม)
//   2) ilil(int)   = string decryptor แบบ 6-branch dispatcher
//      (ต้นแบบ: dispatcher เทียบคีย์ 6 สาขาตามโครง)
//   3) djp(int)    = blob decryptor จาก .rodata table (แบบจำลอง: blob table จำนวนมากใน .rodata)
//   4) chl(byte[]) = verifier (ต้นแบบ: checksum/verify)
//   5) ic(Context) = engine init hook (ต้นแบบ: init AMS/PMS hooks — ที่นี่: init โครง + persist config)
//
// ตัดออกจากต้นแบบตาม scope ลดความซับซ้อน:
//   - mmap RWX + JIT syscall trampoline (ต้นแบบซ่อน svc) → decode ลง std::string ปกติ
//   - anti-root/anti-frida string pool → ไม่มี
//   - license/token payload ใน blob → ไม่มี (blob เป็น engine config ทั่วไป)

#include <jni.h>
#include <cstdlib>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AetherEngine", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, "AetherEngine", __VA_ARGS__)

// ── decoder: XOR-stream แบบ stateful (mirror กลไก "object decoder" ของต้นแบบ ฉบับตรวจสอบซ้ำได้) ──
// stream(i) = (K[(i + seed) & 3] + 31*i) & 0xFF ; plain[i] = enc[i] ^ stream(i)
static const uint8_t K[4] = {0x5A, 0xC3, 0x11, 0x9E};

static inline uint8_t streamByte(size_t i, uint8_t seed) {
    return (uint8_t)((K[(i + seed) & 3] + 31u * (uint32_t)i) & 0xFF);
}

static std::vector<uint8_t> decodeHex(const char *hex, size_t hexLen, uint8_t seed) {
    std::vector<uint8_t> out;
    out.reserve(hexLen / 2);
    auto nib = [](char c) -> uint8_t {
        if (c >= '0' && c <= '9') return (uint8_t)(c - '0');
        if (c >= 'A' && c <= 'F') return (uint8_t)(c - 'A' + 10);
        return 0;
    };
    for (size_t i = 0; i < hexLen / 2; i++) {
        uint8_t b = (uint8_t)((nib(hex[2 * i]) << 4) | nib(hex[2 * i + 1]));
        out.push_back((uint8_t)(b ^ streamByte(i, seed)));
    }
    return out;
}

// ── ilil string table (เข้ารหัสใน .rodata — decode ตอน runtime ตามกลไกต้นแบบ) ──
// แบบจำลอง: 6 สาขา — แต่ละคีย์เลือก decode string ของสาขาตัวเอง (คีย์: A1/A11/A2/A3 ตามไฟล์นี้):
static const char STR_A11301[] = "FF1C75067F875D8FF816900877CD0787EF0D88";            // 19 B plaintext
static const char STR_A11501[] = "A05FB2C35EC53656C908B9C053C1";                      // 14 B
static const char STR_A11520[] = "A855B9C712CD345ACD4DF4CB56C13D44DD00ADC45BF53E46";  // 24 B
static const char STR_A11521[] = "AF59BFD251DF3D1EDD5AB1CA17C62542DF44";              // 18 B
static const char STR_A21501[] = "AD5FFCDB56CF3D5DC84DF4DD52D52542C145A8";            // 19 B
static const char STR_A31501[] = "A65EBBDE51C976458A06B6C358C67E48DC4EAACE48";        // 21 B

static jstring ililImpl(JNIEnv *env, jclass, jint key) {
    const char *hex = nullptr;
    uint8_t seed = 0;
    switch (key) {                                   // ต้นแบบ: cmp w2, imm → b.eq หกเงื่อนไข
        case 0xA11301: hex = STR_A11301; seed = 0x13; break;
        case 0xA11501: hex = STR_A11501; seed = 0x15; break;
        case 0xA11520: hex = STR_A11520; seed = 0x15; break;
        case 0xA11521: hex = STR_A11521; seed = 0x15; break;
        case 0xA21501: hex = STR_A21501; seed = 0x21; break;
        case 0xA31501: hex = STR_A31501; seed = 0x31; break;
        default: return nullptr;                     // แบบจำลอง: คีย์ไม่รู้จัก → null
    }
    std::vector<uint8_t> plain = decodeHex(hex, strlen(hex), seed);
    plain.push_back(0);                              // NUL-terminate เพื่อ NewStringUTF
    return env->NewStringUTF((const char *)plain.data());
}

// ── djp blob table (แบบจำลอง: blob entries เข้ารหัสใน .rodata — 4 entries: config/profile/device/proxies) ──
static const char BLOB_0[] = "21C02A95B137A51270E06592AB22AB0A38F013C9B02BC9142BA559C1846482710AEC1FF99A1CDB255DDC4EBFCB14996D49DD71BFC747F522508773A6F349B142"; // 64 B
static const char BLOB_1[] = "A05FB2C35EC53656C905B0CA5ACB7D48DC4EAACE48B13E12";                      // 24 B
static const char BLOB_2[] = "6A9FFC45FB5077F92B8FB27DE9572EB823DDFA7F1B4068E9DB9FA26B1A4F88E598F31D72407F811085B75CDA087E881192AF52AB7F79D44EF5A710D9772DFE14E35F74C23DCAF601B4"; // device/profile JSON
static const char BLOB_3[] = "E55B711C758D148FE553C33D308C1696E71F982A73C741F8F5049B206BBE00F0527B913C95A334AE1323FB6AD0A73AA01D2CA34883A628983B6DEB4290DC2BC12E56B35AF88F1FC83F5FDD43A08F48"; // proxy descriptors JSON

static jbyteArray djpImpl(JNIEnv *env, jclass, jint id) {
    const char *hex;
    uint8_t seed;
    switch (id) {
        case 0: hex = BLOB_0; seed = 0x00; break;
        case 1: hex = BLOB_1; seed = 0x01; break;
        case 2: hex = BLOB_2; seed = 0x02; break;
        case 3: hex = BLOB_3; seed = 0x03; break;
        default: return nullptr;                     // ต้นแบบ: id ไม่อยู่ในตาราง → null
    }
    std::vector<uint8_t> dec = decodeHex(hex, strlen(hex), seed);
    jbyteArray arr = env->NewByteArray((jsize)dec.size());
    if (!arr) return nullptr;
    env->SetByteArrayRegion(arr, 0, (jsize)dec.size(), (const jbyte *)dec.data());
    return arr;
}

// ── chl: CRC32 (bitwise) เทียบ 4 ไบต์ท้ายแบบ big-endian (ต้นแบบ: chl(byte[]) verify) ──
static uint32_t crc32buf(const uint8_t *p, size_t n) {
    uint32_t crc = 0xFFFFFFFFu;
    for (size_t i = 0; i < n; i++) {
        crc ^= p[i];
        for (int b = 0; b < 8; b++)
            crc = (crc >> 1) ^ (0xEDB88320u & (0u - (crc & 1u)));
    }
    return ~crc;
}

static jboolean chlImpl(JNIEnv *env, jclass, jbyteArray data) {
    if (!data) return JNI_FALSE;
    jsize len = env->GetArrayLength(data);
    if (len < 4) return JNI_FALSE;
    jbyte *p = env->GetByteArrayElements(data, nullptr);
    if (!p) return JNI_FALSE;
    uint32_t calc = crc32buf((const uint8_t *)p, (size_t)(len - 4));
    uint32_t stored = ((uint32_t)(uint8_t)p[len - 4] << 24) | ((uint32_t)(uint8_t)p[len - 3] << 16)
                    | ((uint32_t)(uint8_t)p[len - 2] << 8)  |  (uint32_t)(uint8_t)p[len - 1];
    env->ReleaseByteArrayElements(data, p, JNI_ABORT);
    return calc == stored ? JNI_TRUE : JNI_FALSE;
}

// ── ic(Context): engine init (ต้นแบบ: init hook AMS/PMS — ที่นี่: อ่าน package + persist config) ──
static void icImpl(JNIEnv *env, jclass, jobject ctx) {
    if (!ctx) { LOGW("ic(null context)"); return; }
    jclass ctxCls = env->GetObjectClass(ctx);
    jmethodID getPkg = env->GetMethodID(ctxCls, "getPackageName", "()Ljava/lang/String;");
    jstring pkg = (jstring)(getPkg ? env->CallObjectMethod(ctx, getPkg) : nullptr);
    if (pkg) {
        const char *pkgC = env->GetStringUTFChars(pkg, nullptr);
        LOGI("ic: engine init for package=%s", pkgC ? pkgC : "?");
        if (pkgC) env->ReleaseStringUTFChars(pkg, pkgC);
        env->DeleteLocalRef(pkg);
    }
    // parity: ต้นแบบ init เสร็จแล้วแจ้ง core ผ่าน callback — ที่นี่เรียก AetherCore.persistEngineVersion()
    jclass core = env->FindClass("com/aether/container/AetherCore");
    if (core) {
        jmethodID persist = env->GetStaticMethodID(core, "persistEngineVersion", "()V");
        if (persist) env->CallStaticVoidMethod(core, persist);
        env->DeleteLocalRef(core);
    }
    std::vector<uint8_t> ready = decodeHex(STR_A11301, strlen(STR_A11301), 0x13);
    ready.push_back(0);
    LOGI("ic: %s", (const char *)ready.data());
}

// ── ตระกูล awl/aior/ac/eio (ต้นแบบเป็น trampoline/Binder hook callback — ที่นี่: log sink) ──
static void eioImpl(JNIEnv *, jclass) { LOGI("eio: native buffer flushed"); }

static void awlImpl(JNIEnv *env, jclass, jstring s) {
    const char *c = s ? env->GetStringUTFChars(s, nullptr) : "(null)";
    LOGI("awl: %s", c ? c : "(null)");
    if (s && c) env->ReleaseStringUTFChars(s, c);
}

static void aiorImpl(JNIEnv *env, jclass, jstring a, jstring b) {
    const char *ca = a ? env->GetStringUTFChars(a, nullptr) : "(null)";
    const char *cb = b ? env->GetStringUTFChars(b, nullptr) : "(null)";
    LOGI("aior: %s / %s", ca ? ca : "(null)", cb ? cb : "(null)");
    if (a && ca) env->ReleaseStringUTFChars(a, ca);
    if (b && cb) env->ReleaseStringUTFChars(b, cb);
}

static void acImpl(JNIEnv *, jclass, jobject x, jobject y) {
    LOGI("ac: sink(a=%s, b=%s)", x ? "obj" : "null", y ? "obj" : "null");
}

// ── i(int): engine int config (live table sig (I)V) ──
static int gConfig = 0;

static void iImpl(JNIEnv *, jclass, jint v) {
    gConfig = (int)v;
    LOGI("i: engine config = %d", gConfig);
}

// ── pjowqpxe(Object,Object,Object): 3-arg sink (live table sig (LOO)V) ──
static void pjowqpxeImpl(JNIEnv *, jclass, jobject a, jobject b, jobject c) {
    LOGI("pjowqpxe: sink(a=%s, b=%s, c=%s)",
         a ? "obj" : "null", b ? "obj" : "null", c ? "obj" : "null");
}

// ── setEnv: ตั้ง environment variable ของ process (ใช้ตั้ง HOME ให้ guest loader) ──
static void setEnvImpl(JNIEnv *env, jclass, jstring k, jstring v) {
    const char *ck = k ? env->GetStringUTFChars(k, nullptr) : nullptr;
    const char *cv = v ? env->GetStringUTFChars(v, nullptr) : nullptr;
    if (ck && cv) {
        int r = setenv(ck, cv, 1);
        LOGI("setenv %s=%s -> %d", ck, cv, r);
    }
    if (ck) env->ReleaseStringUTFChars(k, ck);
    if (cv) env->ReleaseStringUTFChars(v, cv);
}

// ── JNI_OnLoad: RegisterNatives (parity: ต้นแบบไม่มี Java_* export — ผูกตอน load เท่านั้น) ──
static JNINativeMethod gMethods[] = {
    {(char *)"ic",   (char *)"(Landroid/content/Context;)V",            (void *)icImpl},
    {(char *)"ilil", (char *)"(I)Ljava/lang/String;",                   (void *)ililImpl},
    {(char *)"djp",  (char *)"(I)[B",                                   (void *)djpImpl},
    {(char *)"chl",  (char *)"([B)Z",                                   (void *)chlImpl},
    {(char *)"eio",  (char *)"()V",                                     (void *)eioImpl},
    {(char *)"awl",  (char *)"(Ljava/lang/String;)V",                   (void *)awlImpl},
    {(char *)"aior", (char *)"(Ljava/lang/String;Ljava/lang/String;)V", (void *)aiorImpl},
    {(char *)"ac",   (char *)"(Ljava/lang/Object;Ljava/lang/Object;)V", (void *)acImpl},
    {(char *)"i",    (char *)"(I)V",                                    (void *)iImpl},
    {(char *)"pjowqpxe", (char *)"(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V", (void *)pjowqpxeImpl},
    {(char *)"setEnvNative", (char *)"(Ljava/lang/String;Ljava/lang/String;)V",            (void *)setEnvImpl},
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass cls = env->FindClass("com/aether/helper/Native");
    if (!cls) { LOGW("class com/aether/helper/Native not found"); return JNI_ERR; }
    if (env->RegisterNatives(cls, gMethods,
                             sizeof(gMethods) / sizeof(gMethods[0])) != JNI_OK) {
        LOGW("RegisterNatives failed");
        return JNI_ERR;
    }
    LOGI("JNI_OnLoad: registered %d native methods (AetherEngine structural model)",
         (int)(sizeof(gMethods) / sizeof(gMethods[0])));
    return JNI_VERSION_1_6;
}
