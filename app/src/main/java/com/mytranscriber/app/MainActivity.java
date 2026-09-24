package com.mytranscriber.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.transformer.AudioEncoderSettings;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;

import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.InitializationConfiguration;
import com.unity3d.ads.InitializationListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.ads.UnityAdsError;
import com.unity3d.ads.UnityAdsShowOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {

    // =========================================================
    // UNITY ADS
    // =========================================================

    private static final String UNITY_GAME_ID =
            "800380386";

    private static final boolean UNITY_TEST_MODE =
            true;

    private static final String UNITY_INTERSTITIAL_AD_UNIT_ID =
            "BP_Interstitial_Android";

    private boolean unityInterstitialReady = false;

    private TextView unityDebugText;

    // =========================================================
    // WEBVIEW
    // =========================================================

    private WebView webView;

    // =========================================================
    // FILE / COMPRESSION
    // =========================================================

    private static final int REQUEST_PICK_FILE = 1001;

    private String selectedFilePath = null;

    private String compressedFilePath = null;

    private int requestedBitrateKbps = 128;

    // =========================================================
    // ON CREATE
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // -----------------------------------------------------
        // ROOT LAYOUT
        // -----------------------------------------------------

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        // -----------------------------------------------------
        // UNITY DEBUG
        // -----------------------------------------------------

        unityDebugText =
                new TextView(this);

        unityDebugText.setText(
                "Unity Ads: Initializing..."
        );

        unityDebugText.setTextSize(12);

        unityDebugText.setPadding(
                12,
                8,
                12,
                8
        );

        root.addView(
                unityDebugText,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        // -----------------------------------------------------
        // WEBVIEW
        // -----------------------------------------------------

        webView =
                new WebView(this);

        root.addView(
                webView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        setContentView(root);

        // -----------------------------------------------------
        // WEBVIEW SETTINGS
        // -----------------------------------------------------

        WebSettings settings =
                webView.getSettings();

        settings.setJavaScriptEnabled(true);

        settings.setDomStorageEnabled(true);

        settings.setAllowFileAccess(true);

        settings.setAllowContentAccess(true);

        settings.setMediaPlaybackRequiresUserGesture(
                false
        );

        webView.setWebViewClient(
                new WebViewClient()
        );

        webView.setWebChromeClient(
                new WebChromeClient()
        );

        // -----------------------------------------------------
        // JAVASCRIPT BRIDGE
        // -----------------------------------------------------

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );

        // -----------------------------------------------------
        // LOAD HTML
        // -----------------------------------------------------

        webView.loadUrl(
                "file:///android_asset/index.html"
        );

        // -----------------------------------------------------
        // UNITY ADS
        // -----------------------------------------------------

        initializeUnityAds();
    }

    // =========================================================
    // UNITY ADS INITIALIZATION
    // =========================================================

    private void initializeUnityAds() {

        updateUnityDebug(
                "Initializing..."
        );

        try {

            InitializationConfiguration configuration =
                    new InitializationConfiguration.Builder(
                            UNITY_GAME_ID
                    )
                            .withTestMode(
                                    UNITY_TEST_MODE
                            )
                            .build();

            InitializationListener listener =
                    new InitializationListener() {

                        @Override
                        public void onInitializationComplete(
                                UnityAdsError error
                        ) {

                            runOnUiThread(() -> {

                                if (error == null) {

                                    updateUnityDebug(
                                            "Initialized successfully"
                                    );

                                    loadUnityInterstitial();

                                } else {

                                    unityInterstitialReady =
                                            false;

                                    String errorMessage =
                                            error.getMessage();

                                    if (errorMessage == null) {
                                        errorMessage =
                                                error.toString();
                                    }

                                    updateUnityDebug(
                                            "INIT FAILED: "
                                                    + errorMessage
                                    );
                                }
                            });
                        }
                    };

            UnityAds.initialize(
                    configuration,
                    listener
            );

        } catch (Exception e) {

            updateUnityDebug(
                    "INIT ERROR: "
                            + e.getMessage()
            );
        }
    }

    // =========================================================
    // LOAD INTERSTITIAL
    // =========================================================

    private void loadUnityInterstitial() {

        unityInterstitialReady =
                false;

        updateUnityDebug(
                "Ad loading..."
        );

        try {

            UnityAds.load(
                    UNITY_INTERSTITIAL_AD_UNIT_ID,
                    new IUnityAdsLoadListener() {

                        @Override
                        public void onUnityAdsAdLoaded(
                                String placementId
                        ) {

                            runOnUiThread(() -> {

                                unityInterstitialReady =
                                        true;

                                updateUnityDebug(
                                        "AD LOADED: "
                                                + placementId
                                );
                            });
                        }

                        @Override
                        public void onUnityAdsFailedToLoad(
                                String placementId,
                                UnityAds.UnityAdsLoadError error,
                                String message
                        ) {

                            runOnUiThread(() -> {

                                unityInterstitialReady =
                                        false;

                                updateUnityDebug(
                                        "AD LOAD FAILED: "
                                                + error
                                                + " - "
                                                + message
                                );
                            });
                        }
                    }
            );

        } catch (Exception e) {

            updateUnityDebug(
                    "AD LOAD ERROR: "
                            + e.getMessage()
            );
        }
    }

    // =========================================================
    // SHOW INTERSTITIAL
    // =========================================================

    private void showInterstitialThenResult(
            final String outputPath
    ) {

        if (!unityInterstitialReady) {

            updateUnityDebug(
                    "Ad not ready - showing result"
            );

            sendCompressionResult(
                    outputPath
            );

            // Try loading again for next compression.
            loadUnityInterstitial();

            return;
        }

        updateUnityDebug(
                "Showing ad..."
        );

        unityInterstitialReady =
                false;

        try {

            UnityAds.show(
                    this,
                    UNITY_INTERSTITIAL_AD_UNIT_ID,
                    new UnityAdsShowOptions(),
                    new IUnityAdsShowListener() {

                        @Override
                        public void onUnityAdsShowFailure(
                                String placementId,
                                UnityAds.UnityAdsShowError error,
                                String message
                        ) {

                            runOnUiThread(() -> {

                                updateUnityDebug(
                                        "AD SHOW FAILED: "
                                                + error
                                                + " - "
                                                + message
                                );

                                sendCompressionResult(
                                        outputPath
                                );

                                loadUnityInterstitial();
                            });
                        }

                        @Override
                        public void onUnityAdsShowStart(
                                String placementId
                        ) {

                            runOnUiThread(() -> {

                                updateUnityDebug(
                                        "AD STARTED"
                                );
                            });
                        }

                        @Override
                        public void onUnityAdsShowClick(
                                String placementId
                        ) {

                            runOnUiThread(() -> {

                                updateUnityDebug(
                                        "AD CLICKED"
                                );
                            });
                        }

                        @Override
                        public void onUnityAdsShowComplete(
                                String placementId,
                                UnityAds.UnityAdsShowCompletionState state
                        ) {

                            runOnUiThread(() -> {

                                updateUnityDebug(
                                        "AD COMPLETED: "
                                                + state
                                );

                                sendCompressionResult(
                                        outputPath
                                );

                                loadUnityInterstitial();
                            });
                        }
                    }
            );

        } catch (Exception e) {

            updateUnityDebug(
                    "AD SHOW ERROR: "
                            + e.getMessage()
            );

            sendCompressionResult(
                    outputPath
            );

            loadUnityInterstitial();
        }
    }

    // =========================================================
    // UNITY DEBUG TEXT
    // =========================================================

    private void updateUnityDebug(
            String message
    ) {

        if (unityDebugText == null) {
            return;
        }

        runOnUiThread(() -> {

            unityDebugText.setText(
                    "Unity Ads: " + message
            );
        });
    }

    // =========================================================
    // SEND COMPRESSION RESULT TO WEBVIEW
    // =========================================================

    private void sendCompressionResult(
            String outputPath
    ) {

        if (webView == null) {
            return;
        }

        if (outputPath == null) {
            return;
        }

        String safePath =
                outputPath
                        .replace(
                                "\\",
                                "\\\\"
                        )
                        .replace(
                                "'",
                                "\\'"
                        );

        webView.post(() -> {

            webView.evaluateJavascript(
                    "window.onCompressionComplete && "
                            + "window.onCompressionComplete('"
                            + safePath
                            + "')",
                    null
            );
        });
    }

    // =========================================================
    // JAVASCRIPT BRIDGE
    // =========================================================

    public class AndroidBridge {

        // -----------------------------------------------------
        // SELECT COMPRESSOR FILE
        // -----------------------------------------------------

        @JavascriptInterface
        public void selectCompressorFile() {

            runOnUiThread(() -> {

                Intent intent =
                        new Intent(
                                Intent.ACTION_OPEN_DOCUMENT
                        );

                intent.addCategory(
                        Intent.CATEGORY_OPENABLE
                );

                intent.setType(
                        "*/*"
                );

                startActivityForResult(
                        intent,
                        REQUEST_PICK_FILE
                );
            });
        }

        // -----------------------------------------------------
        // COMPRESS AUDIO
        // -----------------------------------------------------

        @JavascriptInterface
        public void compressAudio(
                int bitrateKbps
        ) {

            runOnUiThread(() -> {

                if (selectedFilePath == null) {

                    Toast.makeText(
                            MainActivity.this,
                            "Please select a file first",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                requestedBitrateKbps =
                        Math.max(
                                24,
                                Math.min(
                                        bitrateKbps,
                                        128
                                )
                        );

                startCompression(
                        selectedFilePath,
                        requestedBitrateKbps
                );
            });
        }

        // -----------------------------------------------------
        // DOWNLOAD / SAVE
        // -----------------------------------------------------

        @JavascriptInterface
        public void downloadCompressedFile(
                String outputPath
        ) {

            runOnUiThread(() -> {

                if (outputPath == null ||
                        outputPath.trim().isEmpty()) {

                    Toast.makeText(
                            MainActivity.this,
                            "Output file not found",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                File sourceFile =
                        new File(outputPath);

                if (!sourceFile.exists()) {

                    Toast.makeText(
                            MainActivity.this,
                            "Compressed file not found",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                Toast.makeText(
                        MainActivity.this,
                        "File ready: "
                                + sourceFile.getName(),
                        Toast.LENGTH_SHORT
                ).show();
            });
        }

        // -----------------------------------------------------
        // OPEN TELEGRAM
        // -----------------------------------------------------

        @JavascriptInterface
        public void openTelegram() {

            runOnUiThread(() -> {

                try {

                    Intent intent =
                            new Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(
                                            "https://t.me/"
                                    )
                            );

                    startActivity(
                            intent
                    );

                } catch (Exception e) {

                    Toast.makeText(
                            MainActivity.this,
                            "Cannot open Telegram",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        }
    }

    // =========================================================
    // FILE PICKER RESULT
    // =========================================================

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode != REQUEST_PICK_FILE) {
            return;
        }

        if (resultCode != RESULT_OK) {
            return;
        }

        if (data == null) {
            return;
        }

        Uri uri =
                data.getData();

        if (uri == null) {
            return;
        }

        selectedFilePath =
                getPathFromUri(uri);

        if (selectedFilePath == null) {

            Toast.makeText(
                    this,
                    "Cannot read selected file",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        File selectedFile =
                new File(
                        selectedFilePath
                );

        String fileName =
                selectedFile.getName();

        Toast.makeText(
                this,
                "Selected: " + fileName,
                Toast.LENGTH_SHORT
        ).show();

        if (webView != null) {

            String safeName =
                    fileName
                            .replace(
                                    "\\",
                                    "\\\\"
                            )
                            .replace(
                                    "'",
                                    "\\'"
                            );

            webView.evaluateJavascript(
                    "window.onFileSelected && "
                            + "window.onFileSelected('"
                            + safeName
                            + "')",
                    null
            );
        }
    }

    // =========================================================
    // URI -> FILE PATH
    // =========================================================

    private String getPathFromUri(
            Uri uri
    ) {

        // -----------------------------------------------------
        // TRY DIRECT FILE PATH
        // -----------------------------------------------------

        try {

            String[] projection = {
                    android.provider.MediaStore.MediaColumns.DATA
            };

            android.database.Cursor cursor =
                    getContentResolver().query(
                            uri,
                            projection,
                            null,
                            null,
                            null
                    );

            if (cursor != null) {

                int columnIndex =
                        cursor.getColumnIndex(
                                android.provider.MediaStore.MediaColumns.DATA
                        );

                if (columnIndex >= 0 &&
                        cursor.moveToFirst()) {

                    String path =
                            cursor.getString(
                                    columnIndex
                            );

                    cursor.close();

                    if (path != null &&
                            !path.isEmpty()) {

                        return path;
                    }

                } else {

                    cursor.close();
                }
            }

        } catch (Exception ignored) {
        }

        // -----------------------------------------------------
        // FALLBACK: COPY URI TO CACHE
        // -----------------------------------------------------

        try {

            File cacheFile =
                    new File(
                            getCacheDir(),
                            "input_"
                                    + System.currentTimeMillis()
                    );

            InputStream input =
                    getContentResolver()
                            .openInputStream(
                                    uri
                            );

            if (input == null) {
                return null;
            }

            FileOutputStream output =
                    new FileOutputStream(
                            cacheFile
                    );

            byte[] buffer =
                    new byte[8192];

            int length;

            while (
                    (length = input.read(buffer))
                            > 0
            ) {

                output.write(
                        buffer,
                        0,
                        length
                );
            }

            output.flush();

            output.close();

            input.close();

            return cacheFile.getAbsolutePath();

        } catch (Exception e) {

            return null;
        }
    }

    // =========================================================
    // START COMPRESSION
    // =========================================================

    private void startCompression(
            String inputPath,
            int bitrateKbps
    ) {

        File inputFile =
                new File(inputPath);

        if (!inputFile.exists()) {

            Toast.makeText(
                    this,
                    "Input file not found",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Toast.makeText(
                this,
                "Compressing...",
                Toast.LENGTH_SHORT
        ).show();

        File outputDir =
                getExternalFilesDir(
                        Environment.DIRECTORY_MUSIC
                );

        if (outputDir == null) {

            outputDir =
                    getCacheDir();
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String baseName =
                inputFile.getName();

        int dot =
                baseName.lastIndexOf(
                        '.'
                );

        if (dot > 0) {

            baseName =
                    baseName.substring(
                            0,
                            dot
                    );
        }

        File outputFile =
                new File(
                        outputDir,
                        baseName
                                + "_compressed.m4a"
                );

        compressWithTransformer(
                inputFile,
                outputFile,
                bitrateKbps
        );
    }

    // =========================================================
    // MEDIA3 COMPRESSION
    // =========================================================

    private void compressWithTransformer(
            File inputFile,
            File outputFile,
            int bitrateKbps
    ) {

        try {

            if (outputFile.exists()) {
                outputFile.delete();
            }

            // -------------------------------------------------
            // INPUT MEDIA
            // -------------------------------------------------

            MediaItem mediaItem =
                    MediaItem.fromUri(
                            Uri.fromFile(
                                    inputFile
                            )
                    );

            // -------------------------------------------------
            // EDITED MEDIA ITEM
            // -------------------------------------------------

            EditedMediaItem editedMediaItem =
                    new EditedMediaItem.Builder(
                            mediaItem
                    )
                            .build();

            // -------------------------------------------------
            // LISTENER
            // -------------------------------------------------

            Transformer.Listener listener =
                    new Transformer.Listener() {

                        @Override
                        public void onCompleted(
                                Composition composition,
                                ExportResult result
                        ) {

                            runOnUiThread(() -> {

                                handleCompressionFinished(
                                        inputFile,
                                        outputFile
                                );
                            });
                        }

                        @Override
                        public void onError(
                                Composition composition,
                                ExportResult result,
                                ExportException exception
                        ) {

                            runOnUiThread(() -> {

                                String message =
                                        exception.getMessage();

                                if (message == null) {
                                    message =
                                            exception.toString();
                                }

                                Toast.makeText(
                                        MainActivity.this,
                                        "Compression failed: "
                                                + message,
                                        Toast.LENGTH_LONG
                                ).show();
                            });
                        }
                    };

            // -------------------------------------------------
            // AUDIO ENCODER SETTINGS
            // -------------------------------------------------

            AudioEncoderSettings
                    audioEncoderSettings =
                    new AudioEncoderSettings.Builder()
                            .setBitrate(
                                    bitrateKbps * 1000
                            )
                            .build();

            // -------------------------------------------------
            // ENCODER FACTORY
            // -------------------------------------------------

            DefaultEncoderFactory
                    encoderFactory =
                    new DefaultEncoderFactory.Builder(
                            this
                    )
                            .setRequestedAudioEncoderSettings(
                                    audioEncoderSettings
                            )
                            .build();

            // -------------------------------------------------
            // TRANSFORMER
            // -------------------------------------------------

            Transformer transformer =
                    new Transformer.Builder(
                            this
                    )
                            .setAudioMimeType(
                                    "audio/mp4"
                            )
                            .setEncoderFactory(
                                    encoderFactory
                            )
                            .addListener(
                                    listener
                            )
                            .build();

            // -------------------------------------------------
            // START
            // -------------------------------------------------

            transformer.start(
                    editedMediaItem,
                    outputFile.getAbsolutePath()
            );

        } catch (Exception e) {

            String message =
                    e.getMessage();

            if (message == null) {
                message =
                        e.toString();
            }

            Toast.makeText(
                    this,
                    "Compression error: "
                            + message,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // =========================================================
    // COMPRESSION FINISHED
    // =========================================================

    private void handleCompressionFinished(
            File inputFile,
            File outputFile
    ) {

        if (!outputFile.exists()) {

            Toast.makeText(
                    this,
                    "Output file not created",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        long originalSize =
                inputFile.length();

        long compressedSize =
                outputFile.length();

        // -----------------------------------------------------
        // RETRY WITH LOWER BITRATE
        // -----------------------------------------------------

        if (compressedSize >= originalSize &&
                requestedBitrateKbps > 24) {

            int retryBitrate =
                    Math.max(
                            24,
                            requestedBitrateKbps / 2
                    );

            Toast.makeText(
                    this,
                    "Trying lower bitrate...",
                    Toast.LENGTH_SHORT
            ).show();

            requestedBitrateKbps =
                    retryBitrate;

            compressWithTransformer(
                    inputFile,
                    outputFile,
                    retryBitrate
            );

            return;
        }

        // -----------------------------------------------------
        // SAVE RESULT PATH
        // -----------------------------------------------------

        compressedFilePath =
                outputFile.getAbsolutePath();

        Toast.makeText(
                this,
                "Compression complete",
                Toast.LENGTH_SHORT
        ).show();

        // -----------------------------------------------------
        // SHOW AD THEN RESULT
        // -----------------------------------------------------

        showInterstitialThenResult(
                compressedFilePath
        );
    }

    // =========================================================
    // BACK BUTTON
    // =========================================================

    @Override
    public void onBackPressed() {

        if (webView != null &&
                webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }
                                    }
