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
                Uri uri = request.getUrl();
                if ("qedaojphnxwoptitfzzk.supabase.co".equalsIgnoreCase(uri.getHost())
                        && "/functions/v1/janb-app".equals(uri.getPath())) {
                    try {
                        HttpURLConnection connection = (HttpURLConnection)
                                new URL(uri.toString()).openConnection();
                        connection.setRequestMethod("GET");
                        connection.setConnectTimeout(20000);
                        connection.setReadTimeout(25000);
                        connection.setInstanceFollowRedirects(true);
                        connection.setRequestProperty("Accept", "text/html");
                        connection.setRequestProperty("Cache-Control", "no-cache");
                        int status = connection.getResponseCode();
                        InputStream body = status >= 400
                                ? connection.getErrorStream() : connection.getInputStream();
                        java.util.Map<String, String> headers = new java.util.HashMap<>();
                        headers.put("Cache-Control", "no-store");
                        headers.put("Access-Control-Allow-Origin", "*");
                        String reason = connection.getResponseMessage();
                        if (reason == null || reason.isEmpty()) reason = "OK";
                        // Force the document MIME type while keeping the requested HTTPS URL
                        // as the WebView document origin for Supabase auth/local storage.
                        return new WebResourceResponse("text/html", "UTF-8",
                                status, reason, headers, body);
                    } catch (Exception error) {
                        String message = "<!doctype html><html lang='ar' dir='rtl'><meta charset='utf-8'><body style='font-family:Tahoma;padding:24px;text-align:center'>تعذر تحميل التطبيق. تحقق من الإنترنت ثم أعد المحاولة.</body></html>";
                        return new WebResourceResponse("text/html", "UTF-8",
                                503, "Service Unavailable",
                                java.util.Collections.singletonMap("Cache-Control", "no-store"),
                                new java.io.ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)));
                    }
                }
                return assetLoader.shouldInterceptRequest(uri);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();

                // Route Supabase OAuth deep links back into this Activity.
                // WebView does not dispatch custom schemes to Android intent filters by itself.
                if ("janbbait".equalsIgnoreCase(scheme)
                        && "auth".equalsIgnoreCase(uri.getHost())) {
                    handleIncomingLink(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                }

                // Facebook blocks OAuth inside embedded WebViews. Open Supabase's
                // authorization endpoint in a system browser tab so the provider
                // can authenticate and return through the janbbait:// callback.
                if ("https".equalsIgnoreCase(scheme)
                        && "qedaojphnxwoptitfzzk.supabase.co".equalsIgnoreCase(uri.getHost())
                        && uri.getPath() != null
                        && uri.getPath().startsWith("/auth/v1/authorize")) {
                    try {
                        CustomTabsIntent tabs = new CustomTabsIntent.Builder().build();
                        tabs.launchUrl(MainActivity.this, uri);
                    } catch (Exception customTabError) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        } catch (Exception ignored) {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("تعذر فتح تسجيل الدخول")
                                    .setMessage("لم نتمكن من فتح صفحة تسجيل الدخول. تأكد من وجود متصفح على الهاتف ثم حاول مرة أخرى.")
                                    .setPositiveButton("حسنًا", null)
                                    .show();
                        }
                    }
                    return true;
                }

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

    private void loadHostedHtml(String targetUrl, String fallbackSuffix) {
        // Load the app from its real HTTPS origin. The previous implementation
        // downloaded HTML with HttpURLConnection and injected it using
        // loadDataWithBaseURL; that can create an opaque WebView origin and
        // break Supabase PKCE verifier storage when Facebook returns the code.
        // A real navigation keeps localStorage and the OAuth callback on the
        // exact same origin across the external Custom Tab round-trip.
        if (web != null) {
            web.loadUrl(targetUrl);
        }
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
            loadHostedHtml(APP_URL + "?app=1", "");
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
            String hostedSuffix = suffix.isEmpty() ? "" : "&" + suffix.substring(1);
            loadHostedHtml(APP_URL + "?app=1" + hostedSuffix, suffix);
            return;
        }
        loadHostedHtml(APP_URL + "?app=1", "");
    }

    private void loadAuthCallback(Intent intent) {
        if (intent == null || intent.getData() == null || web == null) return;
        Uri uri = intent.getData();
        if (!"janbbait".equalsIgnoreCase(uri.getScheme())
                || !"auth".equalsIgnoreCase(uri.getHost())
                || !"/callback".equals(uri.getPath())) return;

        // Preserve the complete OAuth callback URL, including its query and
        // fragment. Fetching the HTML separately with HttpURLConnection can
        // lose the fragment (#access_token=...) before Supabase JS sees it.
        String hostedUrl = APP_URL + "?app=1";
        String query = uri.getEncodedQuery();
        String fragment = uri.getEncodedFragment();
        if (query != null && !query.isEmpty()) hostedUrl += "&" + query;
        if (fragment != null && !fragment.isEmpty()) hostedUrl += "#" + fragment;

        // Let WebView navigate to the callback URL itself so Supabase JS can
        // detect the OAuth code/tokens in window.location and restore the session.
        web.loadUrl(hostedUrl);
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
