import 'dart:io';

/// ApiClient — HTTP client แบบ proxy-aware (กลไกเดียวกับชั้น network ของแบบจำลอง)
///
/// อ่านค่าจาก environment: http_proxy / https_proxy / NO_PROXY
/// ที่นี่ **ไม่มี endpoint ใด ๆ ถูกเรียก** — และโดยเจตนาไม่มี endpoint ระบบ login key/ใบอนุญาต
/// ใช้เป็นโครงสำหรับต่อ API ของผู้ใช้เองในอนาคต
class ApiClient {
  ApiClient._();

  static HttpClient create() {
    final HttpClient client = HttpClient();
    client.connectionTimeout = const Duration(seconds: 20);

    final String? proxy = Platform.environment['https_proxy'] ?? Platform.environment['http_proxy'];
    final String? noProxy = Platform.environment['NO_PROXY'] ?? Platform.environment['no_proxy'];
    if (proxy != null && proxy.isNotEmpty) {
      try {
        final Uri u = Uri.parse(proxy);
        client.findProxy = (Uri target) {
          if (noProxy != null && noProxy.isNotEmpty && _inNoProxy(noProxy, target.host)) {
            return 'DIRECT';
          }
          return 'PROXY ${u.host}:${u.port == 0 ? 8080 : u.port}';
        };
      } catch (_) {
        // proxy ไม่ถูกต้อง — ใช้ DIRECT
      }
    }
    return client;
  }

  static bool _inNoProxy(String noProxy, String host) {
    for (final String entry in noProxy.split(',')) {
      final String e = entry.trim();
      if (e.isEmpty) continue;
      if (host == e || host.endsWith('.$e')) return true;
    }
    return false;
  }
}
