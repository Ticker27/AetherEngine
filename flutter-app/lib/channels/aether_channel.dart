import 'package:flutter/services.dart';

/// Dart -> Kotlin -> JNI -> C++ bridge
/// Channel name matches AetherRuntimeChannel.CHANNEL_NAME in the Android host.
class AetherChannel {
  static const MethodChannel _channel = MethodChannel('aether/runtime');

  static Future<String> version() async {
    try {
      final result = await _channel.invokeMethod<String>('version');
      if (result == null) {
        throw StateError('Native version is null');
      }
      return result;
    } on PlatformException catch (e) {
      throw StateError('Failed to get version: ${e.code} ${e.message}');
    }
  }

  static Future<String> state() async {
    try {
      final result = await _channel.invokeMethod<String>('state');
      if (result == null) {
        throw StateError('Native state is null');
      }
      return result;
    } on PlatformException catch (e) {
      throw StateError('Failed to get state: ${e.code} ${e.message}');
    }
  }

  static Future<bool> initialize() async {
    try {
      final result = await _channel.invokeMethod<bool>('initialize');
      return result ?? false;
    } on PlatformException catch (e) {
      throw StateError('Initialize failed: ${e.code} ${e.message}');
    }
  }

  static Future<void> shutdown() async {
    try {
      await _channel.invokeMethod('shutdown');
    } on PlatformException catch (e) {
      throw StateError('Shutdown failed: ${e.code} ${e.message}');
    }
  }

  static Future<String> ping() async {
    try {
      final result = await _channel.invokeMethod<String>('ping');
      return result ?? 'no-pong';
    } on PlatformException catch (e) {
      throw StateError('Ping failed: ${e.code} ${e.message}');
    }
  }
}
