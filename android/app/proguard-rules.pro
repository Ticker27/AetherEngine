# AetherEngine: เก็บ JNI class + native method ไว้จาก obfuscation (parity กับเป้าหมาย hook ของ libaether)
-keepclasseswithmembernames class com.aether.helper.Native { native <methods>; }
-keep class com.aether.helper.Native { static *; }
