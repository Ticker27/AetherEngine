import 'package:flutter/material.dart';

import '../core/engine_channel.dart';
import '../i18n/l10n.dart';

/// SettingsPage — Language · LOG VIEW · Information (จัดกลุ่มแบบ KOS: ไม่รก)
class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  String _device = '…';
  bool _exporting = false;

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

  Future<void> _export() async {
    setState(() => _exporting = true);
    String r;
    try {
      r = await EngineChannel.exportLogs() ?? 'null';
    } catch (e) {
      r = 'ERR: $e';
    }
    if (!mounted) return;
    setState(() => _exporting = false);
    ScaffoldMessenger.maybeOf(context)?.hideCurrentSnackBar();
    ScaffoldMessenger.maybeOf(context)?.showSnackBar(
      SnackBar(content: Text(r, maxLines: 2, overflow: TextOverflow.ellipsis)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final L10n l = L10n.current();
    return Scaffold(
      appBar: AppBar(title: Text(l.get('navSettings'))),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 6),
        children: <Widget>[
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 4),
            child: Text('PREFERENCES',
                style: TextStyle(fontSize: 12, letterSpacing: 1.1,
                    color: Theme.of(context).colorScheme.primary)),
          ),
          ListTile(
            leading: const Icon(Icons.translate),
            title: Text(l.get('language')),
            subtitle: Text(supportedLanguages[appLocale.value.languageCode] ?? 'System default'),
          ),
          ...supportedLanguages.entries.map((MapEntry<String, String> e) => RadioListTile<String>(
                dense: true,
                value: e.key,
                groupValue: appLocale.value.languageCode,
                title: Text(e.value),
                onChanged: (String? v) {
                  if (v != null) appLocale.value = Locale(v);
                },
              )),
          const Divider(),

          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 4),
            child: Text('LOG VIEW',
                style: TextStyle(fontSize: 12, letterSpacing: 1.1,
                    color: Theme.of(context).colorScheme.primary)),
          ),
          ListTile(
            leading: _exporting
                ? const SizedBox(width: 22, height: 22,
                    child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.article_outlined),
            title: Text(l.get('exportLogs')),
            subtitle: Text(l.get('exportLogsSub'), maxLines: 2, overflow: TextOverflow.ellipsis),
            onTap: _exporting ? null : _export,
          ),
          const Divider(),

          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 4),
            child: Text('INFORMATION',
                style: TextStyle(fontSize: 12, letterSpacing: 1.1,
                    color: Theme.of(context).colorScheme.primary)),
          ),
          ListTile(
            leading: const Icon(Icons.phone_android),
            title: Text(l.get('device')),
            subtitle: Text(_device, maxLines: 2, overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
            onTap: _loadDevice,
          ),
          ListTile(
            leading: const Icon(Icons.notifications_active_outlined),
            title: Text(l.get('keepAlive'), maxLines: 1, overflow: TextOverflow.ellipsis),
          ),
          const SizedBox(height: 8),
        ],
      ),
    );
  }
}
