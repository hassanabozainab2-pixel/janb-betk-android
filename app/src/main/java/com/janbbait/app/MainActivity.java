package com.janbbait.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebResourceError;
import android.content.Intent;
import android.net.Uri;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.webkit.WebViewAssetLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebViewAssetLoader assetLoader;
    private WebView web;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private static final String APP_URL = "https://qedaojphnxwoptitfzzk.supabase.co/functions/v1/janb-app";
    private static final String LOCAL_APP_URL = "https://appassets.androidplatform.net/assets/index.html";

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

                if ("https".equalsIgnoreCase(scheme)
                        && !"qedaojphnxwoptitfzzk.supabase.co".equalsIgnoreCase(uri.getHost())
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
        if (getIntent() == null || getIntent().getData() == null) {
            loadHostedHtml(APP_URL);
        }
    }

    private void loadHostedHtml(String targetUrl) {
        loader.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(targetUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(25000);
                connection.setRequestProperty("Accept", "text/html");
                connection.setRequestProperty("Cache-Control", "no-cache");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException("HTTP " + status);
                }
                StringBuilder html = new StringBuilder();
                try (InputStream input = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) html.append(line).append('\n');
                }
                String page = html.toString();
                if (!page.trim().toLowerCase().startsWith("<!doctype html")
                        && !page.trim().toLowerCase().startsWith("<html")) {
                    throw new IllegalStateException("The server did not return an HTML document");
                }
                web.post(() -> web.loadDataWithBaseURL(
                        APP_URL, page, "text/html", "UTF-8", null));
            } catch (Exception error) {
                web.post(() -> web.loadUrl(LOCAL_APP_URL));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
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
        loadHostedHtml(callbackUrl);
    }

    @Override
    protected void onDestroy() {
        loader.shutdownNow();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
