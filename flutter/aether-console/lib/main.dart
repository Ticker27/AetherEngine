import 'package:flutter/material.dart';

void main() {
  runApp(const AetherConsole());
}

class AetherConsole extends StatelessWidget {
  const AetherConsole({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      home: Scaffold(
        body: Center(
          child: Text('AetherEngine'),
        ),
      ),
    );
  }
}
