import '../channels/aether_channel.dart';

/// Service layer wrapping AetherChannel — testable, no direct MethodChannel usage outside.
class NativeService {
  const NativeService();

  Future<String> getVersion() => AetherChannel.version();

  Future<String> getRuntimeState() => AetherChannel.state();

  Future<bool> initialize() => AetherChannel.initialize();

  Future<void> shutdown() => AetherChannel.shutdown();

  Future<String> ping() => AetherChannel.ping();

  /// Returns structured status for UI
  Future<RuntimeStatus> getStatus() async {
    try {
      final results = await Future.wait([
        AetherChannel.version(),
        AetherChannel.state(),
        AetherChannel.ping(),
      ]);
      return RuntimeStatus(
        version: results[0] as String,
        state: results[1] as String,
        ping: results[2] as String,
        ok: true,
      );
    } catch (e) {
      return RuntimeStatus(
        version: 'error',
        state: 'error',
        ping: e.toString(),
        ok: false,
        error: e.toString(),
      );
    }
  }
}

class RuntimeStatus {
  final String version;
  final String state;
  final String ping;
  final bool ok;
  final String? error;

  const RuntimeStatus({
    required this.version,
    required this.state,
    required this.ping,
    required this.ok,
    this.error,
  });
}
