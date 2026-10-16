import 'package:flutter/material.dart';

import '../core/engine_channel.dart';

/// HomePage — หน้าแรกของ AetherEngine (phase A1: ปุ่มทดสอบทุก code ในตาราง)
/// UI เต็ม (container list / settings / about + i18n) จะเติมในเฟสถัดไป
class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final List<String> _log = <String>[];

  Future<void> _run(String label, Future<String?> Function() op) async {
    String out;
    try {
      out = (await op()) ?? 'null';
    } catch (e) {
      out = 'ERR: $e';
    }
    setState(() => _log.insert(0, '$label → $out'));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('AetherEngine')),
      body: Column(
        children: <Widget>[
          Padding(
            padding: const EdgeInsets.all(12),
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: <Widget>[
                FilledButton(onPressed: () => _run('"A" toast', () => EngineChannel.toast('hello from Dart')), child: const Text('A · toast')),
                FilledButton(onPressed: () => _run('"0" djp(0)', () => EngineChannel.blob(0)), child: const Text('0 · blob')),
                FilledButton(onPressed: () => _run('"B" put', () => EngineChannel.prefsPut('demo_key', 'ok')), child: const Text('B · prefs put')),
                FilledButton(onPressed: () => _run('"D" get', () => EngineChannel.prefsGet('demo_key')), child: const Text('D · prefs get')),
                FilledButton(onPressed: () => _run('"F" cached', () => EngineChannel.cached()), child: const Text('F · cached')),
                FilledButton(onPressed: () => _run('"G" containers', () => EngineChannel.listContainers()), child: const Text('G · containers')),
              ],
            ),
          ),
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              itemCount: _log.length,
              itemBuilder: (_, int i) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 2),
                child: Text(_log[i], style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
