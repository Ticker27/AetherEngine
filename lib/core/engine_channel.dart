import 'package:flutter/services.dart';

/// EngineChannel — สะพาน Dart ↔ Java host (MethodChannel)
///
/// กลไกเดียวกับโครงต้นแบบ: method name "คือ" code ในตาราง
///   "0" → native blob decrypt (djp)
///   "A" → toast
///   "B" → prefs put  [k, v]
///   "D" → prefs get  (key)
///   "E" → open url
///   "F" → cached string (get/set)
///   "G" → list container apps
///   "H" → install apk (uri string)
///   "I" → image picker (ผลย่อรูปส่งกลับทาง "imageResult")
///   "J" → zip + share
///   "K" → apk picker
///   "L" → device info
///   "M" → launch container app (pkg)
///   "N" → install จากแอปที่ติดตั้งบนเครื่อง
/// ทุกอย่างวิ่งผ่าน CommandRouter ฝั่ง Java → (เมื่อถึงคิว) → libaether.so
class EngineChannel {
  EngineChannel._();

  static const MethodChannel _channel = MethodChannel('com.aether/engine');

  static Future<String?> dispatch(String code, [Object? arg]) async {
    final Object? out = await _channel.invokeMethod<Object?>(code, arg);
    return out?.toString();
  }

  static Future<String?> blob(int id) => dispatch('0', id);
  static Future<String?> toast(String text) => dispatch('A', text);
  static Future<String?> prefsPut(String k, String v) => dispatch('B', <Object?>[k, v]);
  static Future<String?> prefsGet(String k) => dispatch('D', k);
  static Future<String?> openUrl(String url) => dispatch('E', url);
  static Future<String?> cached([String? v]) => dispatch('F', v);
  static Future<String?> listContainers() => dispatch('G');
  static Future<String?> targets() => dispatch('G');
  static Future<String?> installApk(String uri) => dispatch('H', uri);
  static Future<String?> pickImage() => dispatch('I');
  static Future<String?> zipShare([String? path]) => dispatch('J', path);
  static Future<String?> pickApk() => dispatch('K');
  static Future<String?> deviceInfo() => dispatch('L');
  static Future<String?> installFromDevice(String pkg) => dispatch('N', pkg);
  static Future<String?> fetchPlugin(String pkg) => dispatch('O', pkg);
  static Future<String?> exportLogs() => dispatch('P');
  static Future<String?> launchContainer(String pkg) => dispatch('M', pkg);

  /// รับผลจากฝั่ง host (เช่น ผลย่อรูปจาก code "I")
  static void onImageResult(void Function(String payload) cb) {
    _channel.setMethodCallHandler((MethodCall call) async {
      if (call.method == 'imageResult') {
        cb(call.arguments?.toString() ?? '');
      }
      return null;
    });
  }
}
