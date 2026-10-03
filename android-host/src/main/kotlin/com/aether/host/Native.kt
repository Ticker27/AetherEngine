package com.aether.host

/**
 * Native contract declaration (Checkpoint 2)
 *
 * ประกาศ contract เท่านั้น — ยังไม่มี native implementation
 * - ห้าม System.loadLibrary() ในรอบนี้ (จะเพิ่มใน Checkpoint ถัดไปพร้อม libaether.so)
 * - ห้ามเรียก initialize() จาก Application/MainActivity — ยังไม่มี .so รองรับ
 *   (การเรียกจะ throw UnsatisfiedLinkError ตอน runtime)
 */
object Native {
    external fun initialize(): Boolean
}
