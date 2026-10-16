package com.aether.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * InternalWebBrowser — browser ในแอป (WebView)
 * โครงเดียวกับแบบจำลอง: JS on · mixed-content always · safe-browsing off · cookie manager · in-app navigation
 * ที่นี่เปิดผ่าน com.aether.ui.InternalWebBrowser (exported=false — จุดต่างเชิงความปลอดภัย)
 */
public class InternalWebBrowser extends Activity {

    public static final String EXTRA_URL = "url";

    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setSafeBrowsingEnabled(false);

        CookieManager.getInstance().setAcceptCookie(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return false; // คงการนำทางใน in-app browser
            }
        });
        web.setWebChromeClient(new WebChromeClient());

        String url = getIntent() != null ? getIntent().getStringExtra(EXTRA_URL) : null;
        web.loadUrl(url != null ? url : "about:blank");
        setContentView(web);
        android.util.Log.i("AetherEngine", "InternalWebBrowser load " + url);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
