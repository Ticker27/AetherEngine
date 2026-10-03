import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_app/channels/aether_channel.dart';

void main() {
  const channel = MethodChannel('aether/runtime');

  TestWidgetsFlutterBinding.ensureInitialized();

  group('AetherChannel', () {
    late List<MethodCall> log;

    setUp(() {
      log = [];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (MethodCall call) async {
        log.add(call);
        switch (call.method) {
          case 'version':
            return 'aether-dev-0.2.0';
          case 'state':
            return 'initialized';
          case 'initialize':
            return true;
          case 'shutdown':
            return null;
          case 'ping':
            return 'pong:initialized';
          default:
            return null;
        }
      });
    });

    tearDown(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
    });

    test('version returns non-null string', () async {
      final v = await AetherChannel.version();
      expect(v, isNotEmpty);
      expect(log.last.method, 'version');
    });

    test('state returns valid state', () async {
      final s = await AetherChannel.state();
      expect(s, isIn(['new', 'initialized', 'running', 'stopping', 'stopped']));
    });

    test('initialize returns bool', () async {
      final ok = await AetherChannel.initialize();
      expect(ok, isA<bool>());
    });

    test('ping returns pong', () async {
      final pong = await AetherChannel.ping();
      expect(pong, contains('pong'));
    });
  });
}
