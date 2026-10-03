import 'package:flutter/material.dart';
import 'app.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const AetherApp());
}

/// Entry point for add-to-app embedding
@pragma('vm:entry-point')
void mainAether() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const AetherApp());
}
