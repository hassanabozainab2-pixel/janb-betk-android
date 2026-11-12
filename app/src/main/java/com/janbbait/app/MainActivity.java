package com.janbbait.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebResourceError;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.provider.OpenableColumns;
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
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 4107;
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

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                    FileChooserParams fileChooserParams) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    Intent intent = fileChooserParams.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception error) {
                    filePathCallback = null;
                    return false;
                }
            }
        });

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
        handleIncomingLink(getIntent());
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
                        targetUrl, page, "text/html", "UTF-8", null));
            } catch (Exception error) {
                web.post(() -> web.loadUrl(LOCAL_APP_URL));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web == null) {
            finish();
            return;
        }

        // Close the deepest in-app screen before leaving the Activity.
        web.evaluateJavascript(
                "(function(){"
                + "var pages=['businessDetailPage','featuredDetailPage','categoryPage'];"
                + "for(var i=0;i<pages.length;i++){"
                + "var page=document.getElementById(pages[i]);"
                + "if(page && page.classList.contains('show')){page.classList.remove('show');return 'handled';}"
                + "}"
                + "var ids=['featuredDetailModal','adModal','choiceModal','modal'];"
                + "for(var i=0;i<ids.length;i++){"
                + "var el=document.getElementById(ids[i]);"
                + "if(el && el.classList.contains('show')){el.classList.remove('show');return 'handled';}"
                + "}"
                + "var drop=document.getElementById('accountDropdown');"
                + "if(drop && !drop.classList.contains('hidden')){drop.classList.add('hidden');return 'handled';}"
                + "return 'no';"
                + "})()",
                value -> {
                    if ("\"handled\"".equals(value)) return;
                    if (web != null && web.canGoBack()) {
                        web.goBack();
                    } else {
                        finish();
                    }
                });
    }

    private void showExitConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("الخروج من جنب بيتك")
                .setMessage("هل تريد الخروج من التطبيق؟")
                .setNegativeButton("إلغاء", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("خروج", (dialog, which) -> finish())
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                Uri result = data.getData();
                if (result != null) results = new Uri[]{result};
                else if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingLink(intent);
    }

    private void loadLocalApp(String suffix) {
        if (web == null) return;
        web.loadUrl(LOCAL_APP_URL + (suffix == null ? "" : suffix));
    }

    private void handleIncomingLink(Intent intent) {
        if (intent == null || intent.getData() == null) {
            loadLocalApp("");
            return;
        }
        Uri uri = intent.getData();
        if ("janbbait".equals(uri.getScheme()) && "auth".equals(uri.getHost())) {
            loadAuthCallback(intent);
            return;
        }
        if ("https".equalsIgnoreCase(uri.getScheme())
                && "hassanabozainab2-pixel.github.io".equalsIgnoreCase(uri.getHost())) {
            String businessId = uri.getQueryParameter("business");
            String suffix = "";
            if (businessId != null && businessId.matches("[0-9a-fA-F-]{36}")) {
                suffix = "?business=" + Uri.encode(businessId);
            }
            loadLocalApp(suffix);
            return;
        }
        loadLocalApp("");
    }

    private void loadAuthCallback(Intent intent) {
        if (intent == null || intent.getData() == null || web == null) return;
        Uri uri = intent.getData();
        if (!"janbbait".equals(uri.getScheme()) || !"auth".equals(uri.getHost())) return;
        String callbackUrl = "";
        String query = uri.getEncodedQuery();
        String fragment = uri.getEncodedFragment();
        if (query != null && !query.isEmpty()) callbackUrl += "?" + query;
        if (fragment != null && !fragment.isEmpty()) callbackUrl += "#" + fragment;
        loadLocalApp(callbackUrl);
    }

    @Override
    protected void onDestroy() {
        loader.shutdownNow();
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
