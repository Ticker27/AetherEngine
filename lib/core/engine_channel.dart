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
  static Future<String?> installApk(String uri) => dispatch('H', uri);
}
