package com.aether.host.virtualization.web

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.aether.host.virtualization.flags.HostFeature
import com.aether.host.virtualization.flags.flagger

/** Minimal internal HTTPS viewer with script, file, and content access disabled by default. */
class InternalWebBrowser : Activity() {
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!flagger.isEnabled(HostFeature.INTERNAL_WEB_BROWSER)) {
            finish()
            return
        }

        val initialUri = intent?.data ?: intent?.getStringExtra(EXTRA_URL)?.let(Uri::parse)
        if (!isAllowedHttpsUri(initialUri)) {
            finish()
            return
        }

        val browser = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = false
                domStorageEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = !isAllowedHttpsUri(request.url)
            }
        }
        webView = browser
        setContentView(FrameLayout(this).apply {
            addView(browser, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))
        })
        browser.loadUrl(initialUri.toString())
    }

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun isAllowedHttpsUri(uri: Uri?): Boolean =
        uri != null && uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()

    companion object {
        const val EXTRA_URL = "com.aether.host.extra.INTERNAL_BROWSER_URL"
    }
}
