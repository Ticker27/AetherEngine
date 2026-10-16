import 'package:flutter/material.dart';

import '../core/engine_channel.dart';
import '../i18n/l10n.dart';

/// SettingsPage — ภาษา · ข้อมูลเครื่อง · ตรวจกลไก prefs/keep-alive
class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  String _device = '…';

  @override
  void initState() {
    super.initState();
    _loadDevice();
  }

  Future<void> _loadDevice() async {
    try {
      final String? d = await EngineChannel.deviceInfo();
      if (mounted) setState(() => _device = d ?? 'null');
    } catch (e) {
      if (mounted) setState(() => _device = 'ERR: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final L10n l = L10n.current();
    return Scaffold(
      appBar: AppBar(title: Text(l.get('navSettings'))),
      body: ListView(
        children: <Widget>[
          ListTile(
            leading: const Icon(Icons.language),
            title: Text(l.get('language')),
          ),
          ...supportedLanguages.entries.map((MapEntry<String, String> e) => RadioListTile<String>(
                value: e.key,
                groupValue: appLocale.value.languageCode,
                title: Text(e.value),
                onChanged: (String? v) {
                  if (v != null) appLocale.value = Locale(v);
                },
              )),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.phone_android),
            title: Text(l.get('device')),
            subtitle: Text(_device, style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
            onTap: _loadDevice,
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.notifications_active_outlined),
            title: Text(l.get('keepAlive')),
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.cloud_outlined),
            title: Text(l.get('netProxy')),
          ),
        ],
      ),
    );
  }
}
