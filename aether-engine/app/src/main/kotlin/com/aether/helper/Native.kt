package com.aether.helper

class Native {
    external fun ac(o1: Any?, o2: Any?)
    external fun aior(s1: String?, s2: String?)
    external fun awl(s: String?)
    external fun chl(b: ByteArray?): Boolean
    external fun djp(i: Int): ByteArray
    external fun eio()
    external fun i(i: Int)
    external fun ic(ctx: android.content.Context?)
    external fun ilil(i: Int): String?
    external fun jpo(o1: Any?, o2: Any?, o3: Any?)   // rename จาก pjowqpxe
    external fun update(o: Any?, m: java.lang.reflect.Method?)

    companion object {
        init { System.loadLibrary("aether") }
    }
}
