package com.aether.proxy;

/**
 * ProxyPendingActivity — stub สำหรับ launch แบบรอผล ( pending )
 * โครงเดียวกับแบบจำลอง: ใช้กับ startActivityForResult ของแอปใน container
 */
public class ProxyPendingActivity extends ProxyActivity {

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        renderStub("ProxyPendingActivity");
    }
}
