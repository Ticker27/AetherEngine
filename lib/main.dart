import 'package:flutter/material.dart';

import 'ui/home_page.dart';

void main() {
  runApp(const AetherApp());
}

/// AetherEngine — Flutter host
/// ชั้น Dart คุยกับ Java ผ่าน MethodChannel "com.aether/engine" ด้วย code table
/// เดียวกับฝั่ง host: "0","A","B","D","E","F","G","H" (ดู lib/core/engine_channel.dart)
class AetherApp extends StatelessWidget {
  const AetherApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AetherEngine',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(colorSchemeSeed: const Color(0xFF2E7D32), useMaterial3: true),
      home: const HomePage(),
    );
  }
}
