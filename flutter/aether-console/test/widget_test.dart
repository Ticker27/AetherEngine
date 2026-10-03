import 'package:aether_console/main.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('renders AetherEngine text', (tester) async {
    await tester.pumpWidget(const AetherConsole());
    expect(find.text('AetherEngine'), findsOneWidget);
  });
}
