import 'dart:convert';

import 'package:flutter/material.dart';

import '../core/engine_channel.dart';
import '../i18n/l10n.dart';

/// HomePage — console ทดสอบ code ทุกตัว + รายการแอปใน container
class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final List<String> _log = <String>[];
  List<Map<String, dynamic>> _containers = <Map<String, dynamic>>[];

  @override
  void initState() {
    super.initState();
    EngineChannel.onImageResult((String payload) {
      if (mounted) setState(() => _log.insert(0, 'imageResult → $payload'));
    });
    _refresh();
  }

  Future<void> _run(String label, Future<String?> Function() op) async {
    String out;
    try {
      out = (await op()) ?? 'null';
    } catch (e) {
      out = 'ERR: $e';
    }
    setState(() => _log.insert(0, '$label → $out'));
  }

  Future<void> _refresh() async {
    try {
      final String? raw = await EngineChannel.listContainers();
      final List<dynamic> arr = (raw == null || raw.isEmpty) ? <dynamic>[] : jsonDecode(raw) as List<dynamic>;
      setState(() => _containers = arr.cast<Map<String, dynamic>>());
    } catch (e) {
      setState(() => _log.insert(0, 'containers → ERR: $e'));
    }
  }

  @override
  Widget build(BuildContext context) {
    final L10n l = L10n.current();
    return Scaffold(
      appBar: AppBar(title: Text(l.get('containers'))),
      body: ListView(
        padding: const EdgeInsets.all(12),
        children: <Widget>[
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(l.get('engineConsole'), style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: <Widget>[
                      FilledButton(onPressed: () => _run('"A" toast', () => EngineChannel.toast('hello from Dart')), child: const Text('A · toast')),
                      FilledButton(onPressed: () => _run('"0" djp(0)', () => EngineChannel.blob(0)), child: const Text('0 · blob')),
                      FilledButton(onPressed: () => _run('"B" put', () => EngineChannel.prefsPut('demo_key', 'ok')), child: const Text('B · prefs put')),
                      FilledButton(onPressed: () => _run('"D" get', () => EngineChannel.prefsGet('demo_key')), child: const Text('D · prefs get')),
                      FilledButton(onPressed: () => _run('"F" cached', () => EngineChannel.cached()), child: const Text('F · cached')),
                      FilledButton(onPressed: () => _run('"L" device', () => EngineChannel.deviceInfo()), child: const Text('L · device')),
                      FilledButton(onPressed: () => _run('"I" pick image', () => EngineChannel.pickImage()), child: Text(l.get('pickImage'))),
                      FilledButton(onPressed: () => _run('"J" zip share', () => EngineChannel.zipShare()), child: Text(l.get('zipShare'))),
                      FilledButton(onPressed: () => _run('"K" pick apk', () => EngineChannel.pickApk()), child: Text(l.get('pickApk'))),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(_log.take(6).join('\n'),
                      style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
                ],
              ),
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: <Widget>[
              Expanded(child: Text(l.get('containers'), style: Theme.of(context).textTheme.titleMedium)),
              TextButton(onPressed: _refresh, child: Text(l.get('refresh'))),
            ],
          ),
          if (_containers.isEmpty)
            Padding(
              padding: const EdgeInsets.all(8),
              child: Text(l.get('noContainers')),
            )
          else
            ..._containers.map((Map<String, dynamic> app) => Card(
                  child: ListTile(
                    leading: const Icon(Icons.apps),
                    title: Text('${app['pkg'] ?? '?'}'),
                    subtitle: Text('v${app['version'] ?? '?'}'),
                    trailing: FilledButton.tonal(
                      onPressed: () => _run('"M" launch ${app['pkg']}',
                          () => EngineChannel.launchContainer('${app['pkg']}')),
                      child: Text(l.get('launch')),
                    ),
                  ),
                )),
        ],
      ),
    );
  }
}
