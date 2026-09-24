package com.mytranscriber.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;

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
import com.unity3d.ads.UnityAdsShowOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView webView;

    private ValueCallback<Uri[]> filePathCallback;

    private Uri lastSelectedUri;

    private long originalFileSize = 0;

    private String pendingDownloadPath = "";

    private static final int FILE_PICKER_REQUEST = 1001;
    private static final int WEB_FILE_PICKER_REQUEST = 2001;
    private static final int SAVE_FILE_REQUEST = 3001;

    // =========================================================
    // UNITY ADS
    // =========================================================

    private static final String UNITY_GAME_ID = "800380386";

    /*
     * true = Test Ads
     * false = Live Ads
     */
    private static final boolean UNITY_TEST_MODE = true;

    /*
     * Unity Dashboard မှာရှိတဲ့
     * BP_Interstitial_Android ရဲ့ Placement ID
     */
    private static final String UNITY_INTERSTITIAL_AD_UNIT_ID =
            "2371efce-e990-498c-991f-877f167bc049";

    private boolean unityInterstitialReady = false;

    private TextView unityDebugText;

    // =========================================================
    // REMOTE MAINTENANCE
    // =========================================================

    private static final String MAINTENANCE_URL =
            "https://liamliam131999.github.io/my-transcriber/maintenance.json";

    private View maintenanceView;

    private boolean maintenanceMode = false;

    // =========================================================
    // COMPRESSOR STATE
    // =========================================================

    private int currentCompressionBitrate = 64;

    private int compressionAttempt = 0;

    private static final int MAX_COMPRESSION_ATTEMPTS = 2;

    private boolean compressionResultSent = false;

    // =========================================================
    // ON CREATE
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkMaintenance();
    }

    // =========================================================
    // DEBUG UNITY STATUS
    // =========================================================

    private void showUnityDebug(final String message) {

        Log.d("UnityAds", message);

        runOnUiThread(() -> {

            Toast.makeText(
                    MainActivity.this,
                    message,
                    Toast.LENGTH_LONG
            ).show();

            if (unityDebugText != null) {

                unityDebugText.setText(
                        "Unity Ads: " + message
                );
            }
        });
    }

    // =========================================================
    // MAINTENANCE CHECK
    // =========================================================

    private void checkMaintenance() {

        Thread thread = new Thread(() -> {

            HttpURLConnection connection = null;

            try {

                URL url = new URL(
                        MAINTENANCE_URL +
                                "?t=" +
                                System.currentTimeMillis()
                );

                connection =
                        (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setUseCaches(false);

                connection.setRequestProperty(
                        "Cache-Control",
                        "no-cache"
                );

                connection.connect();

                int responseCode =
                        connection.getResponseCode();

                if (responseCode >= 200 &&
                        responseCode < 300) {

                    InputStream input =
                            connection.getInputStream();

                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            input,
                                            "UTF-8"
                                    )
                            );

                    StringBuilder result =
                            new StringBuilder();

                    String line;

                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }

                    reader.close();
                    input.close();

                    JSONObject json =
                            new JSONObject(result.toString());

                    boolean maintenance =
                            json.optBoolean(
                                    "maintenance",
                                    false
                            );

                    String message =
                            json.optString(
                                    "message",
                                    "We are currently performing maintenance. Please try again later."
                            );

                    if (maintenance) {

                        runOnUiThread(
                                () -> showMaintenanceScreen(message)
                        );

                    } else {

                        runOnUiThread(
                                this::startNormalApp
                        );
                    }

                } else {

                    runOnUiThread(
                            this::startNormalApp
                    );
                }

            } catch (Exception e) {

                runOnUiThread(
                        this::startNormalApp
                );

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });

        thread.start();
    }

    // =========================================================
    // NORMAL APP
    // =========================================================

    private void startNormalApp() {

        if (maintenanceMode) {
            return;
        }

        initializeUnityAds();

        setupWebView();
    }

    // =========================================================
    // UNITY ADS INITIALIZATION
    // =========================================================

    private void initializeUnityAds() {

        showUnityDebug("Initializing...");

        try {

            InitializationConfiguration config =
                    new InitializationConfiguration.Builder(
                            UNITY_GAME_ID
                    )
                            .withTestMode(
                                    UNITY_TEST_MODE
                            )
                            .build();

            InitializationListener listener =
                    error -> {

                        if (error == null) {

                            showUnityDebug(
                                    "Initialized successfully"
                            );

                            Log.d(
                                    "UnityAds",
                                    "Unity Game ID: "
                                            + UNITY_GAME_ID
                            );

                            Log.d(
                                    "UnityAds",
                                    "Test Mode: "
                                            + UNITY_TEST_MODE
                            );

                            Log.d(
                                    "UnityAds",
                                    "SDK Version: "
                                            + UnityAds.getVersion()
                            );

                            Log.d(
                                    "UnityAds",
                                    "Is Initialized: "
                                            + UnityAds.isInitialized()
                            );

                            loadUnityInterstitial();

                        } else {

                            showUnityDebug(
                                    "Initialization FAILED: "
                                            + error
                            );

                            Log.e(
                                    "UnityAds",
                                    "Unity Ads initialization failed: "
                                            + error
                            );
                        }
                    };

            UnityAds.initialize(
                    config,
                    listener
            );

        } catch (Exception e) {

            showUnityDebug(
                    "Initialization ERROR: "
                            + e.getMessage()
            );

            Log.e(
                    "UnityAds",
                    "Unity Ads initialization error",
                    e
            );
        }
    }

    // =========================================================
    // UNITY INTERSTITIAL LOAD
    // =========================================================

    private void loadUnityInterstitial() {

        runOnUiThread(() -> {

            showUnityDebug("Ad loading...");

            try {

                unityInterstitialReady = false;

                UnityAds.load(
                        UNITY_INTERSTITIAL_AD_UNIT_ID,
                        new IUnityAdsLoadListener() {

                            @Override
                            public void onUnityAdsAdLoaded(
                                    String placementId) {

                                if (
                                        UNITY_INTERSTITIAL_AD_UNIT_ID
                                                .equals(placementId)
                                ) {

                                    unityInterstitialReady = true;

                                    showUnityDebug(
                                            "AD LOADED ✓"
                                    );

                                    Log.d(
                                            "UnityAds",
                                            "Interstitial loaded successfully. "
                                                    + placementId
                                    );
                                }
                            }

                            @Override
                            public void onUnityAdsFailedToLoad(
                                    String placementId,
                                    UnityAds.UnityAdsLoadError error,
                                    String message) {

                                unityInterstitialReady = false;

                                String debugMessage =
                                        "AD LOAD FAILED: "
                                                + error
                                                + " - "
                                                + message;

                                showUnityDebug(
                                        debugMessage
                                );

                                Log.e(
                                        "UnityAds",
                                        debugMessage
                                );
                            }
                        }
                );

            } catch (Exception e) {

                unityInterstitialReady = false;

                showUnityDebug(
                        "AD LOAD EXCEPTION: "
                                + e.getMessage()
                );

                Log.e(
                        "UnityAds",
                        "Interstitial load exception",
                        e
                );
            }
        });
    }

    // =========================================================
    // UNITY INTERSTITIAL SHOW
    // =========================================================

    private void showInterstitialThenResult(
            final File outputFile,
            final long compressedSize) {

        runOnUiThread(() -> {

            if (!unityInterstitialReady) {

                showUnityDebug(
                        "Ad NOT READY - showing result"
                );

                loadUnityInterstitial();

                sendCompressionSuccessResult(
                        outputFile,
                        compressedSize
                );

                return;
            }

            unityInterstitialReady = false;

            showUnityDebug(
                    "Showing Interstitial..."
            );

            try {

                UnityAds.show(
                        MainActivity.this,
                        UNITY_INTERSTITIAL_AD_UNIT_ID,
                        new UnityAdsShowOptions(),
                        new IUnityAdsShowListener() {

                            @Override
                            public void onUnityAdsShowFailure(
                                    String placementId,
                                    UnityAds.UnityAdsShowError error,
                                    String message) {

                                String debugMessage =
                                        "AD SHOW FAILED: "
                                                + error
                                                + " - "
                                                + message;

                                showUnityDebug(
                                        debugMessage
                                );

                                Log.e(
                                        "UnityAds",
                                        debugMessage
                                );

                                loadUnityInterstitial();

                                sendCompressionSuccessResult(
                                        outputFile,
                                        compressedSize
                                );
                            }

                            @Override
                            public void onUnityAdsShowStart(
                                    String placementId) {

                                showUnityDebug(
                                        "AD STARTED ✓"
                                );

                                Log.d(
                                        "UnityAds",
                                        "Interstitial started: "
                                                + placementId
                                );
                            }

                            @Override
                            public void onUnityAdsShowClick(
                                    String placementId) {

                                showUnityDebug(
                                        "AD CLICKED"
                                );

                                Log.d(
                                        "UnityAds",
                                        "Interstitial clicked: "
                                                + placementId
                                );
                            }

                            @Override
                            public void onUnityAdsShowComplete(
                                    String placementId,
                                    UnityAds.UnityAdsShowCompletionState state) {

                                showUnityDebug(
                                        "AD COMPLETED ✓"
                                );

                                Log.d(
                                        "UnityAds",
                                        "Interstitial completed: "
                                                + placementId
                                                + " State: "
                                                + state
                                );

                                loadUnityInterstitial();

                                sendCompressionSuccessResult(
                                        outputFile,
                                        compressedSize
                                );
                            }
                        }
                );

            } catch (Exception e) {

                showUnityDebug(
                        "AD SHOW EXCEPTION: "
                                + e.getMessage()
                );

                Log.e(
                        "UnityAds",
                        "Interstitial show exception",
                        e
                );

                loadUnityInterstitial();

                sendCompressionSuccessResult(
                        outputFile,
                        compressedSize
                );
            }
        });
    }

    // =========================================================
    // SEND COMPRESSION SUCCESS RESULT
    // =========================================================

    private void sendCompressionSuccessResult(
            File outputFile,
            long compressedSize) {

        if (compressionResultSent) {
            return;
        }

        compressionResultSent = true;

        sendResult(
                true,
                "Compression အောင်မြင်ပါပြီ။",
                originalFileSize,
                compressedSize,
                outputFile.getAbsolutePath()
        );
    }

    // =========================================================
    // MAINTENANCE SCREEN
    // =========================================================

    private void showMaintenanceScreen(String message) {

        maintenanceMode = true;

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setGravity(
                Gravity.CENTER
        );

        root.setPadding(
                30,
                30,
                30,
                30
        );

        root.setBackgroundColor(
                Color.rgb(16, 17, 20)
        );

        LinearLayout box =
                new LinearLayout(this);

        box.setOrientation(
                LinearLayout.VERTICAL
        );

        box.setGravity(
                Gravity.CENTER
        );

        box.setPadding(
                30,
                30,
                30,
                30
        );

        box.setBackgroundColor(
                Color.rgb(25, 26, 31)
        );

        TextView icon =
                new TextView(this);

        icon.setText("🔧");
        icon.setTextSize(50);
        icon.setGravity(Gravity.CENTER);

        TextView title =
                new TextView(this);

        title.setText("App Maintenance");
        title.setTextColor(Color.WHITE);
        title.setTextSize(23);
        title.setGravity(Gravity.CENTER);

        title.setPadding(
                0,
                15,
                0,
                10
        );

        TextView messageView =
                new TextView(this);

        messageView.setText(message);

        messageView.setTextColor(
                Color.rgb(169, 173, 183)
        );

        messageView.setTextSize(15);

        messageView.setGravity(
                Gravity.CENTER
        );

        messageView.setLineSpacing(
                0,
                1.4f
        );

        ProgressBar progressBar =
                new ProgressBar(this);

        progressBar.setVisibility(
                View.GONE
        );

        box.addView(
                icon,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        box.addView(
                title,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        box.addView(
                messageView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        root.addView(
                box,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        setContentView(root);

        maintenanceView = root;
    }

    // =========================================================
    // WEBVIEW SETUP
    // =========================================================

    private void setupWebView() {

        webView = new WebView(this);

        WebSettings settings =
                webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebViewClient(
                new WebViewClient()
        );

        webView.setWebChromeClient(
                new WebChromeClient() {

                    @Override
                    public boolean onShowFileChooser(
                            WebView webView,
                            ValueCallback<Uri[]> callback,
                            FileChooserParams fileChooserParams) {

                        if (
                                MainActivity.this
                                        .filePathCallback
                                        != null
                        ) {

                            MainActivity.this
                                    .filePathCallback
                                    .onReceiveValue(null);
                        }

                        MainActivity.this
                                .filePathCallback =
                                callback;

                        try {

                            Intent intent =
                                    new Intent(
                                            Intent.ACTION_OPEN_DOCUMENT
                                    );

                            intent.addCategory(
                                    Intent.CATEGORY_OPENABLE
                            );

                            intent.setType("*/*");

                            intent.putExtra(
                                    Intent.EXTRA_MIME_TYPES,
                                    new String[]{
                                            "audio/*",
                                            "video/*"
                                    }
                            );

                            startActivityForResult(
                                    intent,
                                    WEB_FILE_PICKER_REQUEST
                            );

                            return true;

                        } catch (Exception e) {

                            MainActivity.this
                                    .filePathCallback =
                                    null;

                            return false;
                        }
                    }
                }
        );

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "AndroidBridge"
        );

        FrameLayout frameLayout =
                new FrameLayout(this);

        frameLayout.addView(
                webView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        unityDebugText =
                new TextView(this);

        unityDebugText.setText(
                "Unity Ads: Starting..."
        );

        unityDebugText.setTextColor(
                Color.WHITE
        );

        unityDebugText.setTextSize(12);

        unityDebugText.setBackgroundColor(
                Color.argb(
                        190,
                        0,
                        0,
                        0
                )
        );

        unityDebugText.setPadding(
                12,
                8,
                12,
                8
        );

        FrameLayout.LayoutParams debugParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        debugParams.gravity =
                Gravity.TOP |
                        Gravity.CENTER_HORIZONTAL;

        debugParams.topMargin = 20;

        frameLayout.addView(
                unityDebugText,
                debugParams
        );

        webView.loadUrl(
                "file:///android_asset/index.html"
        );

        setContentView(frameLayout);
    }

    // =========================================================
    // FILE PICKER
    // =========================================================

    public void openFilePicker() {

        try {

            Intent intent =
                    new Intent(
                            Intent.ACTION_OPEN_DOCUMENT
                    );

            intent.addCategory(
                    Intent.CATEGORY_OPENABLE
            );

            intent.setType("*/*");

            intent.putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    new String[]{
                            "audio/*",
                            "video/*"
                    }
            );

            startActivityForResult(
                    intent,
                    FILE_PICKER_REQUEST
            );

        } catch (Exception e) {

            sendResult(
                    false,
                    "File picker ဖွင့်မရပါ:\n"
                            + e.getMessage(),
                    0,
                    0,
                    ""
            );
        }
    }

    // =========================================================
    // ACTIVITY RESULT
    // =========================================================

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

        if (
                requestCode ==
                        WEB_FILE_PICKER_REQUEST
        ) {

            if (
                    filePathCallback != null
            ) {

                Uri[] results = null;

                if (
                        resultCode ==
                                RESULT_OK
                                && data != null
                                && data.getData() != null
                ) {

                    results =
                            new Uri[]{
                                    data.getData()
                            };
                }

                filePathCallback
                        .onReceiveValue(results);

                filePathCallback = null;
            }

            return;
        }

        if (
                requestCode ==
                        SAVE_FILE_REQUEST
        ) {

            if (
                    resultCode ==
                            RESULT_OK
                            && data != null
                            && data.getData() != null
                            && pendingDownloadPath != null
                            && !pendingDownloadPath.isEmpty()
            ) {

                Uri destinationUri =
                        data.getData();

                try {

                    File sourceFile =
                            new File(
                                    pendingDownloadPath
                            );

                    if (
                            !sourceFile.exists()
                    ) {

                        throw new Exception(
                                "Output file မတွေ့ပါ။"
                        );
                    }

                    InputStream input =
                            new FileInputStream(
                                    sourceFile
                            );

                    OutputStream output =
                            getContentResolver()
                                    .openOutputStream(
                                            destinationUri
                                    );

                    if (output == null) {

                        input.close();

                        throw new Exception(
                                "Save location ကို ဖွင့်မရပါ။"
                        );
                    }

                    byte[] buffer =
                            new byte[8192];

                    int length;

                    while (
                            (length =
                                    input.read(buffer))
                                    != -1
                    ) {

                        output.write(
                                buffer,
                                0,
                                length
                        );
                    }

                    output.flush();

                    input.close();
                    output.close();

                    showDownloadSuccess();

                } catch (Exception e) {

                    showDownloadError(
                            e.getMessage()
                    );
                }
            }

            pendingDownloadPath = "";

            return;
        }

        if (
                requestCode ==
                        FILE_PICKER_REQUEST
                        && resultCode ==
                        RESULT_OK
                        && data != null
                        && data.getData() != null
        ) {

            lastSelectedUri =
                    data.getData();

            try {

                final int takeFlags =
                        data.getFlags()
                                & (
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        |
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        );

                getContentResolver()
                        .takePersistableUriPermission(
                                lastSelectedUri,
                                takeFlags
                        );

            } catch (Exception ignored) {
            }

            originalFileSize = 0;

            try {

                Cursor cursor =
                        getContentResolver().query(
                                lastSelectedUri,
                                null,
                                null,
                                null,
                                null
                        );

                if (cursor != null) {

                    int sizeIndex =
                            cursor.getColumnIndex(
                                    OpenableColumns.SIZE
                            );

                    if (
                            cursor.moveToFirst()
                                    && sizeIndex >= 0
                    ) {

                        originalFileSize =
                                cursor.getLong(
                                        sizeIndex
                                );
                    }

                    cursor.close();
                }

            } catch (Exception e) {

                originalFileSize = 0;
            }

            String name =
                    getFileName(
                            lastSelectedUri
                    );

            String jsName =
                    escapeJsString(name);

            final long selectedSize =
                    originalFileSize;

            runOnUiThread(
                    () ->
                            webView.evaluateJavascript(
                                    "if(window.onNativeFileSelected){" +
                                            "window.onNativeFileSelected('" +
                                            jsName +
                                            "'," +
                                            selectedSize +
                                            ");}",
                                    null
                            )
            );
        }
    }

    // =========================================================
    // GET FILE NAME
    // =========================================================

    private String getFileName(Uri uri) {

        String result =
                "selected_file";

        try {

            Cursor cursor =
                    getContentResolver().query(
                            uri,
                            null,
                            null,
                            null,
                            null
                    );

            if (cursor != null) {

                int nameIndex =
                        cursor.getColumnIndex(
                                OpenableColumns.DISPLAY_NAME
                        );

                if (
                        cursor.moveToFirst()
                                && nameIndex >= 0
                ) {

                    result =
                            cursor.getString(
                                    nameIndex
                            );
                }

                cursor.close();
            }

        } catch (Exception ignored) {
        }

        return result;
    }

    // =========================================================
    // COMPRESS AUDIO
    // =========================================================

    private void compressAudio(
            final int requestedBitrateKbps) {

        if (
                lastSelectedUri == null
        ) {

            sendResult(
                    false,
                    "အရင်ဆုံး Video သို့မဟုတ် Audio ဖိုင်ရွေးပါ။",
                    0,
                    0,
                    ""
            );

            return;
        }

        if (
                originalFileSize <= 0
        ) {

            sendResult(
                    false,
                    "မူရင်း file size ကို မဖတ်နိုင်ပါ။",
                    0,
                    0,
                    ""
            );

            return;
        }

        compressionAttempt = 0;

        compressionResultSent = false;

        int safeRequestedBitrate =
                Math.max(
                        24,
                        Math.min(
                                requestedBitrateKbps,
                                128
                        )
                );

        int sourceBitrateKbps =
                getSourceBitrateKbps(
                        lastSelectedUri
                );

        int targetBitrate =
                safeRequestedBitrate;

        if (
                sourceBitrateKbps > 0
        ) {

            int sourceBasedTarget =
                    (int)
                            Math.floor(
                                    sourceBitrateKbps *
                                            0.70
                            );

            sourceBasedTarget =
                    Math.max(
                            24,
                            sourceBasedTarget
                    );

            targetBitrate =
                    Math.min(
                            safeRequestedBitrate,
                            sourceBasedTarget
                    );
        }

        currentCompressionBitrate =
                targetBitrate;

        runOnUiThread(
                () ->
                        sendProgress(
                                "Audio ကို စတင်ချုံ့နေပါတယ်..."
                        )
        );

        try {

            File musicDir =
                    getExternalFilesDir(
                            Environment.DIRECTORY_MUSIC
                    );

            if (musicDir == null) {

                throw new Exception(
                        "Music folder မရပါ။"
                );
            }

            File outputDir =
                    new File(
                            musicDir,
                            "MyTranscriber"
                    );

            if (
                    !outputDir.exists()
                            && !outputDir.mkdirs()
            ) {

                throw new Exception(
                        "Output folder ဖန်တီးမရပါ။"
                );
            }

            String timestamp =
                    new SimpleDateFormat(
                            "yyyyMMdd_HHmmss",
                            Locale.US
                    ).format(
                            new Date()
                    );

            File outputFile =
                    new File(
                            outputDir,
                            "compressed_audio_" +
                                    timestamp +
                                    ".mp4"
                    );

            if (outputFile.exists()) {
                outputFile.delete();
            }

            startAudioCompression(
                    outputFile,
                    targetBitrate
            );

        } catch (Exception e) {

            sendResult(
                    false,
                    e.getMessage() != null
                            ? e.getMessage()
                            : "Compression error",
                    originalFileSize,
                    0,
                    ""
            );
        }
    }

    // =========================================================
    // GET SOURCE BITRATE
    // =========================================================

    private int getSourceBitrateKbps(
            Uri uri) {

        MediaMetadataRetriever retriever =
                new MediaMetadataRetriever();

        try {

            retriever.setDataSource(
                    this,
                    uri
            );

            String mimeType =
                    retriever.extractMetadata(
                            MediaMetadataRetriever
                                    .METADATA_KEY_MIMETYPE
                    );

            if (
                    mimeType != null &&
                            mimeType.toLowerCase(
                                    Locale.US
                            ).startsWith("video/")
            ) {

                return 0;
            }

            String bitrate =
                    retriever.extractMetadata(
                            MediaMetadataRetriever
                                    .METADATA_KEY_BITRATE
                    );

            if (
                    bitrate != null &&
                            !bitrate.isEmpty()
            ) {

                long bitrateValue =
                        Long.parseLong(
                                bitrate
                        );

                if (
                        bitrateValue > 0
                ) {

                    return (int)
                            Math.max(
                                    1,
                                    bitrateValue / 1000
                            );
                }
            }

        } catch (Exception ignored) {

        } finally {

            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }

        return 0;
    }

    // =========================================================
    // START AUDIO COMPRESSION
    // =========================================================

    private void startAudioCompression(
            final File outputFile,
            final int bitrateKbps) {

        try {

            MediaItem mediaItem =
                    MediaItem.fromUri(
                            lastSelectedUri
                    );

            AudioEncoderSettings audioSettings =
                    new AudioEncoderSettings.Builder()
                            .setBitrate(
                                    bitrateKbps * 1000
                            )
                            .build();

            DefaultEncoderFactory encoderFactory =
                    new DefaultEncoderFactory.Builder(
                            this
                    )
                            .setRequestedAudioEncoderSettings(
                                    audioSettings
                            )
                            .build();

            Transformer transformer =
                    new Transformer.Builder(
                            this
                    )
                            .setEncoderFactory(
                                    encoderFactory
                            )
                            .addListener(
                                    new Transformer.Listener() {

                                        @Override
                                        public void onCompleted(
                                                Composition composition,
                                                ExportResult exportResult) {

                                            handleCompressionSuccess(
                                                    outputFile,
                                                    bitrateKbps
                                            );
                                        }

                                        @Override
                                        public void onError(
                                                Composition composition,
                                                ExportResult exportResult,
                                                ExportException exportException) {

                                            handleCompressionError(
                                                    exportException
                                            );
                                        }
                                    }
                            )
                            .build();

            EditedMediaItem editedMediaItem =
                    new EditedMediaItem.Builder(
                            mediaItem
                    )
                            .setRemoveVideo(true)
                            .build();

            final String progressMessage =
                    "Audio ကို " +
                            bitrateKbps +
                            " kbps နဲ့ encode လုပ်နေပါတယ်...";

            runOnUiThread(
                    () ->
                            sendProgress(
                                    progressMessage
                            )
            );

            transformer.start(
                    editedMediaItem,
                    outputFile.getAbsolutePath()
            );

        } catch (Exception e) {

            sendResult(
                    false,
                    e.getMessage() != null
                            ? e.getMessage()
                            : "Compression error",
                    originalFileSize,
                    0,
                    ""
            );
        }
    }

    // =========================================================
    // COMPRESSION SUCCESS
    // =========================================================

    private void handleCompressionSuccess(
            File outputFile,
            int usedBitrateKbps) {

        runOnUiThread(
                () -> {

                    if (!outputFile.exists()) {

                        sendResult(
                                false,
                                "Output file မတွေ့ပါ။",
                                originalFileSize,
                                0,
                                ""
                        );

                        return;
                    }

                    long compressedSize =
                            outputFile.length();

                    if (compressedSize <= 0) {

                        outputFile.delete();

                        sendResult(
                                false,
                                "Output file အရွယ်အစား မမှန်ပါ။",
                                originalFileSize,
                                0,
                                ""
                        );

                        return;
                    }

                    if (
                            compressedSize >=
                                    originalFileSize
                    ) {

                        if (
                                compressionAttempt <
                                        MAX_COMPRESSION_ATTEMPTS - 1
                        ) {

                            compressionAttempt++;

                            int retryBitrate =
                                    Math.max(
                                            24,
                                            usedBitrateKbps / 2
                                    );

                            if (
                                    retryBitrate <
                                            usedBitrateKbps
                            ) {

                                outputFile.delete();

                                currentCompressionBitrate =
                                        retryBitrate;

                                sendProgress(
                                        "Output size မသေးသေးပါ။ " +
                                                retryBitrate +
                                                " kbps နဲ့ ထပ်ချုံ့နေပါတယ်..."
                                );

                                String timestamp =
                                        new SimpleDateFormat(
                                                "yyyyMMdd_HHmmss_SSS",
                                                Locale.US
                                        ).format(
                                                new Date()
                                        );

                                File retryFile =
                                        new File(
                                                outputFile.getParentFile(),
                                                "compressed_audio_" +
                                                        timestamp +
                                                        ".mp4"
                                        );

                                startAudioCompression(
                                        retryFile,
                                        retryBitrate
                                );

                                return;
                            }
                        }

                        outputFile.delete();

                        sendResult(
                                false,
                                "ဒီဖိုင်က သေးအောင်ချုံ့ဖို့ မလွယ်ပါ။ " +
                                        "မူရင်း Audio က bitrate နိမ့်နေပြီးသား ဖြစ်နိုင်ပါတယ်။",
                                originalFileSize,
                                compressedSize,
                                ""
                        );

                        return;
                    }

                    showInterstitialThenResult(
                            outputFile,
                            compressedSize
                    );
                }
        );
    }

    // =========================================================
    // COMPRESSION ERROR
    // =========================================================

    private void handleCompressionError(
            ExportException exception) {

        runOnUiThread(
                () -> {

                    String message =
                            exception.getMessage();

                    if (
                            message == null ||
                                    message.isEmpty()
                    ) {

                        message =
                                "Compression မအောင်မြင်ပါ။";
                    }

                    sendResult(
                            false,
                            message,
                            originalFileSize,
                            0,
                            ""
                    );
                }
        );
    }

    // =========================================================
    // SEND RESULT TO JAVASCRIPT
    // =========================================================

    private void sendResult(
            boolean success,
            String message,
            long originalSize,
            long compressedSize,
            String outputPath) {

        String jsMessage =
                escapeJsString(
                        message == null
                                ? ""
                                : message
                );

        String jsPath =
                escapeJsString(
                        outputPath == null
                                ? ""
                                : outputPath
                );

        String script =
                "if(window.onCompressionFinished){" +
                        "window.onCompressionFinished(" +
                        success +
                        ",'" +
                        jsMessage +
                        "'," +
                        originalSize +
                        "," +
                        compressedSize +
                        ",'" +
                        jsPath +
                        "');}";

        runOnUiThread(
                () ->
                        webView.evaluateJavascript(
                                script,
                                null
                        )
        );
    }

    // =========================================================
    // PROGRESS
    // =========================================================

    private void sendProgress(
            String message) {

        String jsMessage =
                escapeJsString(
                        message == null
                                ? ""
                                : message
                );

        String script =
                "if(window.onCompressionProgress){" +
                        "window.onCompressionProgress('" +
                        jsMessage +
                        "');}";

        runOnUiThread(
                () ->
                        webView.evaluateJavascript(
                                script,
                                null
                        )
        );
    }

    // =========================================================
    // DOWNLOAD COMPRESSED FILE
    // =========================================================

    private void downloadCompressedFile(
            String outputPath) {

        if (
                outputPath == null
                        || outputPath.isEmpty()
        ) {

            showDownloadError(
                    "Output file မတွေ့ပါ။"
            );

            return;
        }

        File file =
                new File(outputPath);

        if (!file.exists()) {

            showDownloadError(
                    "Output file မတွေ့ပါ။"
            );

            return;
        }

        pendingDownloadPath =
                outputPath;

        Intent intent =
                new Intent(
                        Intent.ACTION_CREATE_DOCUMENT
                );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType(
                "audio/mp4"
        );

        intent.putExtra(
                Intent.EXTRA_TITLE,
                file.getName()
        );

        try {

            startActivityForResult(
                    intent,
                    SAVE_FILE_REQUEST
            );

        } catch (Exception e) {

            pendingDownloadPath = "";

            showDownloadError(
                    e.getMessage()
            );
        }
    }

    // =========================================================
    // DOWNLOAD SUCCESS
    // =========================================================

    private void showDownloadSuccess() {

        runOnUiThread(
                () -> {

                    String script =
                            "if(window.onDownloadFinished){" +
                                    "window.onDownloadFinished(true," +
                                    "'File ကို သိမ်းပြီးပါပြီ။');}";

                    webView.evaluateJavascript(
                            script,
                            null
                    );
                }
        );
    }

    // =========================================================
    // DOWNLOAD ERROR
    // =========================================================

    private void showDownloadError(
            String message) {

        String safeMessage =
                escapeJsString(
                        message == null
                                ? "Unknown error"
                                : message
                );

        runOnUiThread(
                () -> {

                    String script =
                            "if(window.onDownloadFinished){" +
                                    "window.onDownloadFinished(false,'" +
                                    safeMessage +
                                    "');}";

                    webView.evaluateJavascript(
                            script,
                            null
                    );
                }
        );
    }

    // =========================================================
    // ESCAPE JAVASCRIPT STRING
    // =========================================================

    private String escapeJsString(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("</", "<\\/");
    }

    // =========================================================
    // ANDROID BRIDGE
    // =========================================================

    public class AndroidBridge {

        @JavascriptInterface
        public void selectCompressorFile() {

            runOnUiThread(
                    () -> openFilePicker()
            );
        }

        @JavascriptInterface
        public void compressAudio(
                int bitrateKbps) {

            runOnUiThread(
                    () ->
                            MainActivity.this
                                    .compressAudio(
                                            bitrateKbps
                                    )
            );
        }

        @JavascriptInterface
        public void downloadCompressedFile(
                String outputPath) {

            runOnUiThread(
                    () ->
                            MainActivity.this
                                    .downloadCompressedFile(
                                            outputPath
                                    )
            );
        }

        @JavascriptInterface
        public void openTelegram() {

            runOnUiThread(
                    () -> {

                        try {

                            Intent telegramIntent =
                                    new Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(
                                                    "tg://resolve?domain=liamliam131999"
                                            )
                                    );

                            startActivity(
                                    telegramIntent
                            );

                        } catch (
                                ActivityNotFoundException e
                        ) {

                            try {

                                Intent browserIntent =
                                        new Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(
                                                        "https://t.me/liamliam131999"
                                                )
                                        );

                                startActivity(
                                        browserIntent
                                );

                            } catch (
                                    Exception browserError
                            ) {

                                showDownloadError(
                                        "Telegram / Browser ဖွင့်မရပါ။"
                                );
                            }
                        }
                    }
            );
        }
    }
            }
