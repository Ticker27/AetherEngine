package com.aether.proxy;

/**
 * TransparentProxyActivity — stub แบบโปร่งใส ( translucent )
 * โครงเดียวกับแบบจำลอง: ใช้เปิด activity แบบไม่ทิ้งร่องตอนสลับแอปใน container
 */
public class TransparentProxyActivity extends ProxyActivity {

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        renderStub("TransparentProxyActivity");
    }
}
