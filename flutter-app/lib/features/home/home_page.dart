import 'package:flutter/material.dart';
import '../../services/native_service.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final NativeService _nativeService = const NativeService();
  String _version = 'loading...';
  String _state = 'loading...';
  String _ping = 'loading...';
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadStatus();
  }

  Future<void> _loadStatus() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final status = await _nativeService.getStatus();
      setState(() {
        _version = status.version;
        _state = status.state;
        _ping = status.ping;
        _loading = false;
        _error = status.ok ? null : status.error;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  Future<void> _initialize() async {
    try {
      final ok = await _nativeService.initialize();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('initialize() -> $ok')),
      );
      await _loadStatus();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Init failed: $e')),
      );
    }
  }

  Future<void> _shutdown() async {
    try {
      await _nativeService.shutdown();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('shutdown() called')),
      );
      await _loadStatus();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Shutdown failed: $e')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('AetherEngine'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadStatus,
            tooltip: 'Refresh state',
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _buildCard('Version', _version, Icons.info_outline),
                  const SizedBox(height: 12),
                  _buildCard('Runtime State', _state, Icons.memory,
                      color: _stateColor(_state)),
                  const SizedBox(height: 12),
                  _buildCard('Ping', _ping, Icons.network_ping),
                  if (_error != null) ...[
                    const SizedBox(height: 12),
                    Card(
                      color: Colors.red.shade50,
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Text(
                          _error!,
                          style: TextStyle(color: Colors.red.shade900),
                        ),
                      ),
                    ),
                  ],
                  const Spacer(),
                  Row(
                    children: [
                      Expanded(
                        child: ElevatedButton.icon(
                          onPressed: _initialize,
                          icon: const Icon(Icons.play_arrow),
                          label: const Text('Initialize'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: _shutdown,
                          icon: const Icon(Icons.stop),
                          label: const Text('Shutdown'),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Dart → MethodChannel("aether/runtime") → Kotlin → JNI → libaether.so',
                    style: TextStyle(fontSize: 10, color: Colors.grey),
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
    );
  }

  Widget _buildCard(String title, String value, IconData icon, {Color? color}) {
    return Card(
      child: ListTile(
        leading: Icon(icon, color: color),
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Text(value, style: TextStyle(color: color)),
      ),
    );
  }

  Color? _stateColor(String state) {
    switch (state) {
      case 'new':
        return Colors.grey;
      case 'initialized':
        return Colors.orange;
      case 'running':
        return Colors.green;
      case 'stopping':
        return Colors.red.shade300;
      case 'stopped':
        return Colors.red;
      default:
        return null;
    }
  }
}
