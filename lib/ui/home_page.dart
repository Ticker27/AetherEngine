import 'dart:convert';

import 'package:flutter/material.dart';

import '../core/engine_channel.dart';
import '../i18n/l10n.dart';

/// HomePage — engine console + Supported Games (pin 56.31.0 · sandbox · device)
class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final List<String> _log = <String>[];
  List<Map<String, dynamic>> _targets = <Map<String, dynamic>>[];

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
    if (label.contains('"K"')) _refresh();
  }

  Future<void> _refresh() async {
    try {
      final String? raw = await EngineChannel.targets();
      final List<dynamic> arr = (raw == null || raw.isEmpty) ? <dynamic>[] : jsonDecode(raw) as List<dynamic>;
      setState(() => _targets = arr.cast<Map<String, dynamic>>());
    } catch (e) {
      setState(() => _log.insert(0, 'targets → ERR: $e'));
    }
  }

  Color _statusColor(String? status, ColorScheme cs) {
    switch (status) {
      case 'match':
        return Colors.green;
      case 'mismatch':
        return Colors.orange;
      default:
        return cs.outline;
    }
  }

  String _statusLabel(L10n l, String? status) {
    switch (status) {
      case 'match':
        return l.get('statusMatch');
      case 'mismatch':
        return l.get('statusMismatch');
      default:
        return l.get('statusNotInstalled');
    }
  }

  @override
  Widget build(BuildContext context) {
    final L10n l = L10n.current();
    final ColorScheme cs = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: Text(l.get('supportedGames'))),
      body: ListView(
        padding: const EdgeInsets.all(12),
        children: <Widget>[
          ..._targets.map((Map<String, dynamic> t) {
            final String? status = t['status']?.toString();
            return Card(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Row(
                      children: <Widget>[
                        ClipRRect(
                          borderRadius: BorderRadius.circular(8),
                          child: Image.asset(
                            'assets/targets/${t['icon']}',
                            width: 36,
                            height: 36,
                            errorBuilder: (_, __, ___) => const Icon(Icons.sports_esports),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text('${t['name'] ?? '?'}',
                              style: Theme.of(context).textTheme.titleMedium),
                        ),
                        Icon(Icons.circle, size: 10, color: _statusColor(status, cs)),
                        const SizedBox(width: 4),
                        Text(_statusLabel(l, status)),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'pin ${t['pin']} · sandbox ${t['sandboxVersion'] ?? '—'} · device ${t['deviceVersion'] ?? '—'}',
                      style: const TextStyle(fontFamily: 'monospace', fontSize: 11),
                    ),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: <Widget>[
                        TextButton(
                          onPressed: () => _run('"K" pick apk', () => EngineChannel.pickApk()),
                          child: Text(l.get('installApk')),
                        ),
                        FilledButton.tonal(
                          onPressed: () => _run('"M" launch ${t['pkg']}',
                              () => EngineChannel.launchContainer('${t['pkg']}')).then((_) => _refresh()),
                          child: Text(l.get('launch')),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            );
          }),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Row(
                    children: <Widget>[
                      Expanded(child: Text(l.get('engineConsole'), style: Theme.of(context).textTheme.titleMedium)),
                      TextButton(onPressed: _refresh, child: Text(l.get('refresh'))),
                    ],
                  ),
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
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(_log.take(8).join('\n'),
                      style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
