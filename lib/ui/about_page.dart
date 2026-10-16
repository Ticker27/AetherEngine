import 'package:flutter/material.dart';

import '../core/engine_channel.dart';
import '../i18n/l10n.dart';

/// AboutPage — เวอร์ชัน · คำอธิบาย · ลิงก์ชุมชน (เปิดผ่าน code "E" → browser ในแอป/system)
class AboutPage extends StatelessWidget {
  const AboutPage({super.key});

  static const List<(IconData, String, String)> _links = <(IconData, String, String)>[
    (Icons.discuss, 'Discord', 'https://discord.com/'),
    (Icons.send, 'Telegram', 'https://t.me/'),
    (Icons.facebook, 'Facebook', 'https://www.facebook.com/'),
    (Icons.chat_bubble_outline, 'WhatsApp', 'https://wa.me/'),
  ];

  @override
  Widget build(BuildContext context) {
    final L10n l = L10n.current();
    return Scaffold(
      appBar: AppBar(title: Text(l.get('navAbout'))),
      body: ListView(
        children: <Widget>[
          const SizedBox(height: 16),
          const Center(child: Icon(Icons.hub_outlined, size: 64)),
          const SizedBox(height: 8),
          Center(child: Text('AetherEngine', style: Theme.of(context).textTheme.headlineSmall)),
          Center(child: Text('${l.get('version')} 1.0.0')),
          const SizedBox(height: 12),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            child: Text(l.get('aboutBlurb'), textAlign: TextAlign.center),
          ),
          const SizedBox(height: 16),
          const Divider(),
          Padding(
            padding: const EdgeInsets.all(12),
            child: Text(l.get('links'), style: Theme.of(context).textTheme.titleMedium),
          ),
          ..._links.map(((IconData, String, String) item) => ListTile(
                leading: Icon(item.$1),
                title: Text(item.$2),
                trailing: const Icon(Icons.open_in_new, size: 18),
                onTap: () => EngineChannel.openUrl(item.$3),
              )),
        ],
      ),
    );
  }
}
