import 'dart:convert';

import 'package:flutter/material.dart';

import '../core/engine_channel.dart';
import '../i18n/l10n.dart';

/// HomePage — Supported Games (สะอาด): การ์ดเดียวต่อเกม + ปุ่ม Play หลัก + เมนู ⋮ สำหรับงานเสริม
class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  List<Map<String, dynamic>> _targets = <Map<String, dynamic>>[];
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<void> _run(String job, {required String pkg, required bool refreshAfter}) async {
    if (_busy) return;
    setState(() => _busy = true);
    String out;
    try {
      switch (job) {
        case 'M':
          out = await EngineChannel.launchContainer(pkg) ?? 'null';
          break;
        case 'N':
          out = await EngineChannel.installFromDevice(pkg) ?? 'null';
          break;
        case 'O':
          out = await EngineChannel.fetchPlugin(pkg) ?? 'null';
          break;
        case 'K':
          out = await EngineChannel.pickApk() ?? 'null';
          break;
        default:
          out = '';
      }
    } catch (e) {
      out = 'ERR: $e';
    }
    if (!mounted) return;
    setState(() => _busy = false);
    if (refreshAfter) _refresh();
    if (out.isNotEmpty && !out.startsWith('installed-started')) {
      ScaffoldMessenger.maybeOf(context)?.hideCurrentSnackBar();
      ScaffoldMessenger.maybeOf(context)?.showSnackBar(
        SnackBar(content: Text('$job → $out', maxLines: 2, overflow: TextOverflow.ellipsis)),
      );
    }
  }

  Future<void> _refresh() async {
    try {
      final String? raw = await EngineChannel.targets();
      final List<dynamic> arr =
          (raw == null || raw.isEmpty) ? <dynamic>[] : jsonDecode(raw) as List<dynamic>;
      if (mounted) setState(() => _targets = arr.cast<Map<String, dynamic>>());
    } catch (_) {}
  }

  Color _statusColor(String? status, ColorScheme cs) {
    switch (status) {
      case 'match':
        return Colors.green.shade600;
      case 'allowed':
        return Colors.blue.shade600;
      case 'mismatch':
        return Colors.orange.shade700;
      default:
        return cs.outline;
    }
  }

  String _statusLabel(L10n l, String? status) {
    switch (status) {
      case 'match':
        return l.get('statusMatch');
      case 'allowed':
        return l.get('statusAllowed');
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
      appBar: AppBar(
        title: Text(l.get('supportedGames'), overflow: TextOverflow.ellipsis),
        actions: <Widget>[
          IconButton(
            tooltip: 'Refresh',
            onPressed: _busy ? null : _refresh,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: ListView.builder(
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 16),
        itemCount: _targets.length,
        itemBuilder: (BuildContext context, int i) {
          final Map<String, dynamic> t = _targets[i];
          final String? status = t['status']?.toString();
          final String sandbox = t['sandboxVersion']?.toString() ?? '—';
          final String device = t['deviceVersion']?.toString() ?? '—';
          return Card(
            margin: const EdgeInsets.only(bottom: 10),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 10, 8, 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Row(
                    children: <Widget>[
                      ClipRRect(
                        borderRadius: BorderRadius.circular(10),
                        child: Image.asset(
                          'assets/targets/${t['icon']}',
                          width: 44,
                          height: 44,
                          fit: BoxFit.cover,
                          errorBuilder: (_, __, ___) =>
                              Container(width: 44, height: 44, color: cs.surfaceContainerHighest,
                                  child: Icon(Icons.sports_esports, color: cs.onSurfaceVariant)),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: <Widget>[
                            Text('${t['name'] ?? '?'}',
                                maxLines: 1, overflow: TextOverflow.ellipsis,
                                style: Theme.of(context).textTheme.titleMedium),
                            const SizedBox(height: 2),
                            Row(
                              children: <Widget>[
                                Icon(Icons.circle, size: 9, color: _statusColor(status, cs)),
                                const SizedBox(width: 4),
                                Flexible(
                                  child: Text(_statusLabel(l, status),
                                      maxLines: 1, overflow: TextOverflow.ellipsis,
                                      style: TextStyle(color: _statusColor(status, cs), fontSize: 12)),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                      PopupMenuButton<String>(
                        tooltip: 'More',
                        onSelected: (String v) =>
                            _run(v, pkg: '${t['pkg']}', refreshAfter: v != 'M'),
                        itemBuilder: (BuildContext bc) => <PopupMenuEntry<String>>[
                          PopupMenuItem<String>(
                              value: 'N', child: Text(l.get('installDevice'))),
                          PopupMenuItem<String>(
                              value: 'O', child: Text(l.get('downloadPlugin'))),
                          const PopupMenuDivider(),
                          PopupMenuItem<String>(
                              value: 'K', child: Text("${l.get('installApk')} (K)")),
                        ],
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: <Widget>[
                      Text('${t['pin'] ?? '—'}',
                          style: TextStyle(fontSize: 11, color: cs.outline)),
                      const SizedBox(width: 6),
                      Flexible(
                        child: Text('sb $sandbox · dev $device',
                            maxLines: 1, overflow: TextOverflow.ellipsis,
                            style: TextStyle(fontSize: 11, color: cs.outline)),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      onPressed: _busy ? null : () => _run('M', pkg: '${t['pkg']}', refreshAfter: false),
                      icon: const Icon(Icons.play_arrow_rounded),
                      label: Text(l.get('launch')),
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}
