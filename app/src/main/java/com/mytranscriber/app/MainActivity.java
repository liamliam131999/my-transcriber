package com.mytranscriber.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView webView;

    private ValueCallback<Uri[]> filePathCallback;

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int MICROPHONE_REQUEST = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();

        // JavaScript
        settings.setJavaScriptEnabled(true);

        // Local Storage
        settings.setDomStorageEnabled(true);

        // File access
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // Allow file:// page to access HTTPS APIs
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // Audio
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Web page navigation
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request) {

                String url = request.getUrl().toString();

                // Telegram / external links
                if (url.startsWith("https://t.me/")
                        || url.startsWith("http://t.me/")) {

                    try {
                        Intent intent = new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(url)
                        );
                        startActivity(intent);
                    } catch (Exception ignored) {
                    }

                    return true;
                }

                return false;
            }
        });

        // Chrome features + File picker + Microphone
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {

                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }

                MainActivity.this.filePathCallback = filePathCallback;

                try {
                    Intent intent = fileChooserParams.createIntent();

                    intent.addCategory(Intent.CATEGORY_OPENABLE);

                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST
                    );

                    return true;

                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
            }

            @Override
            public void onPermissionRequest(
                    final PermissionRequest request) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        if (request.getResources() != null) {
                            request.grant(request.getResources());
                        }
                    }
                });
            }
        });

        // Download links
        webView.setDownloadListener(
                new DownloadListener() {

                    @Override
                    public void onDownloadStart(
                            String url,
                            String userAgent,
                            String contentDisposition,
                            String mimeType,
                            long contentLength) {

                        try {

                            DownloadManager.Request request =
                                    new DownloadManager.Request(
                                            Uri.parse(url)
                                    );

                            request.setMimeType(mimeType);

                            request.addRequestHeader(
                                    "User-Agent",
                                    userAgent
                            );

                            request.setDescription(
                                    "My Transcriber ဖိုင်ဒေါင်းလုဒ်လုပ်နေသည်"
                            );

                            request.setTitle(
                                    "My Transcriber"
                            );

                            request.allowScanningByMediaScanner();

                            request.setNotificationVisibility(
                                    DownloadManager.Request
                                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                            );

                            request.setDestinationInExternalPublicDir(
                                    Environment.DIRECTORY_DOWNLOADS,
                                    "MyTranscriber_Download"
                            );

                            DownloadManager manager =
                                    (DownloadManager)
                                            getSystemService(
                                                    DOWNLOAD_SERVICE
                                            );

                            if (manager != null) {
                                manager.enqueue(request);
                            }

                        } catch (Exception ignored) {
                        }
                    }
                }
        );

        // Load HTML
        webView.loadUrl(
                "file:///android_asset/index.html"
        );

        setContentView(webView);

        // Microphone permission
        requestMicrophonePermission();
    }

    private void requestMicrophonePermission() {

        if (android.os.Build.VERSION.SDK_INT >= 23) {

            if (checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.RECORD_AUDIO
                        },
                        MICROPHONE_REQUEST
                );
            }
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == FILE_CHOOSER_REQUEST) {

            if (filePathCallback == null) {
                return;
            }

            Uri[] results = null;

            if (resultCode == RESULT_OK && data != null) {

                Uri uri = data.getData();

                if (uri != null) {
                    results = new Uri[]{uri};
                }
            }

            filePathCallback.onReceiveValue(results);

            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {

        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }

        super.onDestroy();
    }
}
