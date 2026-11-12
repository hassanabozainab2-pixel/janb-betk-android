package com.janbbait.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.content.Intent;
import android.net.Uri;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    private WebViewAssetLoader assetLoader;
    private WebView web;
    private static final String APP_URL = "https://appassets.androidplatform.net/assets/index.html";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();

                if (scheme != null && (scheme.equals("tel") || scheme.equals("mailto"))) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (Exception ignored) {}
                    return true;
                }

                // Open OAuth/navigation in a Custom Tab and return to this activity
                // only when the registered janbbait://auth/callback is reached.
                if ("https".equalsIgnoreCase(scheme)
                        && !"appassets.androidplatform.net".equalsIgnoreCase(uri.getHost())) {
                    try {
                        CustomTabsIntent tabs = new CustomTabsIntent.Builder().build();
                        tabs.launchUrl(MainActivity.this, uri);
                    } catch (Exception customTabError) {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                        catch (Exception ignored) {}
                    }
                    return true;
                }
                return false;
            }
        });
        setContentView(web);
        loadAuthCallback(getIntent());
        if (getIntent() == null || getIntent().getData() == null) web.loadUrl(APP_URL);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadAuthCallback(intent);
    }

    private void loadAuthCallback(Intent intent) {
        if (intent == null || intent.getData() == null || web == null) return;
        Uri uri = intent.getData();
        if (!"janbbait".equals(uri.getScheme()) || !"auth".equals(uri.getHost())) return;
        String callbackUrl = APP_URL;
        String query = uri.getEncodedQuery();
        String fragment = uri.getEncodedFragment();
        if (query != null && !query.isEmpty()) callbackUrl += "?" + query;
        if (fragment != null && !fragment.isEmpty()) callbackUrl += "#" + fragment;
        web.loadUrl(callbackUrl);
    }
}
