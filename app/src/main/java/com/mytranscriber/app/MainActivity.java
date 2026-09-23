package com.mytranscriber.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.transformer.AudioEncoderSettings;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams fileChooserParams) {

                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }

                MainActivity.this.filePathCallback = callback;

                try {
                    Intent intent =
                            new Intent(Intent.ACTION_OPEN_DOCUMENT);

                    intent.addCategory(Intent.CATEGORY_OPENABLE);
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
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
            }
        });

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "AndroidBridge"
        );

        webView.loadUrl(
                "file:///android_asset/index.html"
        );

        setContentView(webView);
    }

    // =========================================================
    // FILE PICKER
    // =========================================================

    public void openFilePicker() {

        try {
            Intent intent =
                    new Intent(Intent.ACTION_OPEN_DOCUMENT);

            intent.addCategory(Intent.CATEGORY_OPENABLE);
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

        // WEBVIEW FILE PICKER
        if (requestCode == WEB_FILE_PICKER_REQUEST) {

            if (filePathCallback != null) {

                Uri[] results = null;

                if (resultCode == RESULT_OK
                        && data != null
                        && data.getData() != null) {

                    results = new Uri[]{
                            data.getData()
                    };
                }

                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }

            return;
        }

        // SAVE COMPRESSED FILE
        if (requestCode == SAVE_FILE_REQUEST) {

            if (resultCode == RESULT_OK
                    && data != null
                    && data.getData() != null
                    && pendingDownloadPath != null
                    && !pendingDownloadPath.isEmpty()) {

                Uri destinationUri = data.getData();

                try {

                    File sourceFile =
                            new File(pendingDownloadPath);

                    if (!sourceFile.exists()) {
                        throw new Exception(
                                "Output file မတွေ့ပါ။"
                        );
                    }

                    InputStream input =
                            new FileInputStream(sourceFile);

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

                    byte[] buffer = new byte[8192];
                    int length;

                    while ((length = input.read(buffer)) != -1) {
                        output.write(buffer, 0, length);
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

        // COMPRESSOR FILE PICKER
        if (requestCode == FILE_PICKER_REQUEST
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {

            lastSelectedUri = data.getData();

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

                android.database.Cursor cursor =
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
                                    android.provider.OpenableColumns.SIZE
                            );

                    if (cursor.moveToFirst()
                            && sizeIndex >= 0) {

                        originalFileSize =
                                cursor.getLong(sizeIndex);
                    }

                    cursor.close();
                }

            } catch (Exception e) {

                originalFileSize = 0;
            }

            String name =
                    getFileName(lastSelectedUri);

            String jsName =
                    escapeJsString(name);

            final long selectedSize =
                    originalFileSize;

            runOnUiThread(() -> {

                webView.evaluateJavascript(
                        "if(window.onNativeFileSelected){" +
                                "window.onNativeFileSelected('" +
                                jsName +
                                "'," +
                                selectedSize +
                                ");}",
                        null
                );
            });
        }
    }

    // =========================================================
    // GET FILE NAME
    // =========================================================

    private String getFileName(Uri uri) {

        String result = "selected_file";

        try {

            android.database.Cursor cursor =
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
                                android.provider.OpenableColumns.DISPLAY_NAME
                        );

                if (cursor.moveToFirst()
                        && nameIndex >= 0) {

                    result =
                            cursor.getString(nameIndex);
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

    private void compressAudio(final int bitrateKbps) {

        if (lastSelectedUri == null) {

            sendResult(
                    false,
                    "အရင်ဆုံး Video သို့မဟုတ် Audio ဖိုင်ရွေးပါ။",
                    0,
                    0,
                    ""
            );

            return;
        }

        if (originalFileSize <= 0) {

            sendResult(
                    false,
                    "မူရင်း file size ကို မဖတ်နိုင်ပါ။",
                    0,
                    0,
                    ""
            );

            return;
        }

        runOnUiThread(() ->
                sendProgress(
                        "Audio ကို စတင်ချုံ့နေပါတယ်..."
                )
        );

        try {

            File outputDir =
                    new File(
                            getExternalFilesDir(
                                    Environment.DIRECTORY_MUSIC
                            ),
                            "MyTranscriber"
                    );

            if (!outputDir.exists()
                    && !outputDir.mkdirs()) {

                throw new Exception(
                        "Output folder ဖန်တီးမရပါ။"
                );
            }

            String timestamp =
                    new SimpleDateFormat(
                            "yyyyMMdd_HHmmss",
                            Locale.US
                    ).format(new Date());

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
                    new DefaultEncoderFactory.Builder(this)
                            .setRequestedVideoEncoderSettings(null)
                            .setRequestedAudioEncoderSettings(
                                    audioSettings
                            )
                            .build();

            Transformer transformer =
                    new Transformer.Builder(this)
                            .setEncoderFactory(
                                    encoderFactory
                            )
                            .setOutputMimeType(
                                    MimeTypes.VIDEO_MP4
                            )
                            .addListener(
                                    new Transformer.Listener() {

                                        @Override
                                        public void onCompleted(
                                                Composition composition,
                                                ExportResult exportResult) {

                                            handleCompressionSuccess(
                                                    outputFile
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

            runOnUiThread(() ->
                    sendProgress(
                            "Video track ကို ဖယ်ပြီး Audio encode လုပ်နေပါတယ်..."
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
            File outputFile) {

        runOnUiThread(() -> {

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

            if (compressedSize >= originalFileSize) {

                outputFile.delete();

                sendResult(
                        false,
                        "Compression ပြီးသော်လည်း Output size က မူရင်းထက် မသေးပါ။",
                        originalFileSize,
                        compressedSize,
                        ""
                );

                return;
            }

            sendResult(
                    true,
                    "Compression အောင်မြင်ပါပြီ။",
                    originalFileSize,
                    compressedSize,
                    outputFile.getAbsolutePath()
            );
        });
    }

    // =========================================================
    // COMPRESSION ERROR
    // =========================================================

    private void handleCompressionError(
            ExportException exception) {

        runOnUiThread(() -> {

            String message =
                    exception.getMessage();

            if (message == null
                    || message.isEmpty()) {

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
        });
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

        runOnUiThread(() ->
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

        webView.evaluateJavascript(
                script,
                null
        );
    }

    // =========================================================
    // DOWNLOAD COMPRESSED FILE
    // =========================================================

    private void downloadCompressedFile(
            String outputPath) {

        if (outputPath == null
                || outputPath.isEmpty()) {

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

        runOnUiThread(() -> {

            String script =
                    "if(window.onDownloadFinished){" +
                            "window.onDownloadFinished(true,'" +
                            "File ကို သိမ်းပြီးပါပြီ။" +
                            "');}";

            webView.evaluateJavascript(
                    script,
                    null
            );
        });
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

        runOnUiThread(() -> {

            String script =
                    "if(window.onDownloadFinished){" +
                            "window.onDownloadFinished(false,'" +
                            safeMessage +
                            "');}";

            webView.evaluateJavascript(
                    script,
                    null
            );
        });
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

            runOnUiThread(() ->
                    openFilePicker()
            );
        }

        @JavascriptInterface
        public void compressAudio(
                int bitrateKbps) {

            runOnUiThread(() ->
                    MainActivity.this.compressAudio(
                            bitrateKbps
                    )
            );
        }

        @JavascriptInterface
        public void downloadCompressedFile(
                String outputPath) {

            runOnUiThread(() ->
                    MainActivity.this.downloadCompressedFile(
                            outputPath
                    )
            );
        }

        @JavascriptInterface
        public void openTelegram() {

            runOnUiThread(() -> {

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

                } catch (ActivityNotFoundException e) {

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

                    } catch (Exception browserError) {

                        showDownloadError(
                                "Telegram / Browser ဖွင့်မရပါ။"
                        );
                    }
                }
            });
        }
    }
            }
