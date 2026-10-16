import 'package:flutter/material.dart';

import 'i18n/l10n.dart';
import 'ui/about_page.dart';
import 'ui/home_page.dart';
import 'ui/settings_page.dart';

void main() {
  runApp(const AetherApp());
}

/// AetherEngine — Flutter host
/// Dart คุยกับ Java ผ่าน MethodChannel "com.aether/engine" (code table "0".."M")
/// ดู lib/core/engine_channel.dart และ docs/DESIGN.md
class AetherApp extends StatelessWidget {
  const AetherApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<Locale>(
      valueListenable: appLocale,
      builder: (BuildContext context, Locale locale, Widget? _) {
        return MaterialApp(
          title: 'AetherEngine',
          debugShowCheckedModeBanner: false,
          theme: ThemeData(colorSchemeSeed: const Color(0xFF2E7D32), useMaterial3: true),
          home: const RootShell(),
        );
      },
    );
  }
}

/// RootShell — 3 หน้า: Home (console + containers) · Settings · About
class RootShell extends StatefulWidget {
  const RootShell({super.key});

  @override
  State<RootShell> createState() => _RootShellState();
}

class _RootShellState extends State<RootShell> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final L10n l = L10n.current();
    return Scaffold(
      body: IndexedStack(
        index: _index,
        children: const <Widget>[HomePage(), SettingsPage(), AboutPage()],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (int i) => setState(() => _index = i),
        destinations: <NavigationDestination>[
          NavigationDestination(icon: const Icon(Icons.home_outlined), label: l.get('navHome')),
          NavigationDestination(icon: const Icon(Icons.settings_outlined), label: l.get('navSettings')),
          NavigationDestination(icon: const Icon(Icons.info_outline), label: l.get('navAbout')),
        ],
      ),
    );
  }
}
