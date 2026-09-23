package com.mytranscriber.app;

import android.app.Activity;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView webView;

    // HTML <input type="file"> အတွက်
    private ValueCallback<Uri[]> filePathCallback;

    // Compressor အတွက်
    private Uri lastSelectedUri;
    private long originalFileSize = 0;

    // Compressor picker
    private static final int FILE_PICKER_REQUEST = 1001;

    // HTML file input picker
    private static final int WEB_FILE_PICKER_REQUEST = 2001;


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


        // =====================================================
        // WEBVIEW FILE INPUT SUPPORT
        // =====================================================

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {

                // အရင် callback ရှိရင် ပိတ်
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }

                MainActivity.this.filePathCallback =
                        filePathCallback;

                try {

                    Intent intent =
                            new Intent(Intent.ACTION_OPEN_DOCUMENT);

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

                    MainActivity.this.filePathCallback = null;

                    return false;
                }
            }
        });


        // =====================================================
        // JAVASCRIPT BRIDGE
        // =====================================================

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "AndroidBridge"
        );


        // =====================================================
        // LOAD HTML
        // =====================================================

        webView.loadUrl(
                "file:///android_asset/index.html"
        );

        setContentView(webView);
    }


    // =========================================================
    // COMPRESSOR FILE PICKER
    // =========================================================

    public void openFilePicker() {

        try {

            Intent intent =
                    new Intent(Intent.ACTION_OPEN_DOCUMENT);

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
                    "File picker ဖွင့်မရပါ:\n" +
                            e.getMessage(),
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


        // =====================================================
        // HTML FILE INPUT
        // Groq / Gemini
        // =====================================================

        if (requestCode == WEB_FILE_PICKER_REQUEST) {

            if (filePathCallback != null) {

                Uri[] results = null;

                if (resultCode == RESULT_OK
                        && data != null
                        && data.getData() != null) {

                    Uri selectedUri =
                            data.getData();

                    results =
                            new Uri[]{
                                    selectedUri
                            };
                }

                filePathCallback.onReceiveValue(
                        results
                );

                filePathCallback = null;
            }

            return;
        }


        // =====================================================
        // COMPRESSOR
        // =====================================================

        if (requestCode == FILE_PICKER_REQUEST
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {

            lastSelectedUri =
                    data.getData();


            // Persist permission
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


            // =================================================
            // GET FILE SIZE
            // =================================================

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


            // =================================================
            // GET FILE NAME
            // =================================================

            String name =
                    getFileName(
                            lastSelectedUri
                    );


            String jsName =
                    escapeJsString(name);


            // =================================================
            // SEND TO HTML
            // =================================================

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

        String result =
                "selected_file";

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
            final int bitrateKbps) {

        if (lastSelectedUri == null) {

            sendResult(
                    false,
                    "ဖိုင်အရင်ရွေးပါ။",
                    0,
                    0,
                    ""
            );

            return;
        }


        if (originalFileSize <= 0) {

            sendResult(
                    false,
                    "မူရင်းဖိုင် size ကို မဖတ်နိုင်ပါ။",
                    0,
                    0,
                    ""
            );

            return;
        }


        // =====================================================
        // OUTPUT DIRECTORY
        // =====================================================

        File outputDir =
                new File(
                        getExternalFilesDir(
                                Environment.DIRECTORY_MUSIC
                        ),
                        "MyTranscriber"
                );


        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }


        String time =
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
                                time +
                                ".mp4"
                );


        if (outputFile.exists()) {
            outputFile.delete();
        }


        // =====================================================
        // BITRATE
        // =====================================================

        int bitrate =
                bitrateKbps * 1000;


        AudioEncoderSettings audioSettings =
                new AudioEncoderSettings.Builder()
                        .setBitrate(bitrate)
                        .build();


        DefaultEncoderFactory encoderFactory =
                new DefaultEncoderFactory.Builder(this)
                        .setRequestedAudioEncoderSettings(
                                audioSettings
                        )
                        .build();


        // =====================================================
        // TRANSFORMER
        // =====================================================

        Transformer transformer =
                new Transformer.Builder(this)
                        .setAudioMimeType(
                                MimeTypes.AUDIO_AAC
                        )
                        .setEncoderFactory(
                                encoderFactory
                        )
                        .addListener(
                                new Transformer.Listener() {

                                    @Override
                                    public void onCompleted(
                                            androidx.media3.transformer.Composition composition,
                                            ExportResult result) {

                                        long compressedSize =
                                                outputFile.length();


                                        // =====================================
                                        // OUTPUT MUST BE SMALLER
                                        // =====================================

                                        if (compressedSize
                                                >= originalFileSize) {

                                            outputFile.delete();

                                            sendResult(
                                                    false,
                                                    "Compression လုပ်ပြီးနောက် " +
                                                            "size မလျော့ပါ။\n\n" +
                                                            "48 kbps သို့မဟုတ် " +
                                                            "64 kbps ကို စမ်းကြည့်ပါ။",
                                                    originalFileSize,
                                                    compressedSize,
                                                    ""
                                            );

                                            return;
                                        }


                                        long saved =
                                                originalFileSize
                                                        -
                                                compressedSize;


                                        double percent =
                                                (
                                                        saved * 100.0
                                                )
                                                        /
                                                originalFileSize;


                                        String message =
                                                "Audio compression ပြီးပါပြီ။\n\n" +
                                                        "မူရင်း: " +
                                                        formatSize(
                                                                originalFileSize
                                                        ) +
                                                        "\n" +
                                                        "အသစ်: " +
                                                        formatSize(
                                                                compressedSize
                                                        ) +
                                                        "\n" +
                                                        "လျော့သွားသည်: " +
                                                        String.format(
                                                                Locale.US,
                                                                "%.1f%%",
                                                                percent
                                                        );


                                        sendResult(
                                                true,
                                                message,
                                                originalFileSize,
                                                compressedSize,
                                                outputFile.getAbsolutePath()
                                        );
                                    }


                                    @Override
                                    public void onError(
                                            androidx.media3.transformer.Composition composition,
                                            ExportResult result,
                                            ExportException exception) {

                                        if (outputFile.exists()) {
                                            outputFile.delete();
                                        }


                                        String error =
                                                exception.getMessage();


                                        if (error == null) {
                                            error =
                                                    "Unknown error";
                                        }


                                        sendResult(
                                                false,
                                                "Compression မအောင်မြင်ပါ:\n" +
                                                        error,
                                                originalFileSize,
                                                0,
                                                ""
                                        );
                                    }
                                }
                        )
                        .build();


        // =====================================================
        // REMOVE VIDEO
        // =====================================================

        MediaItem mediaItem =
                MediaItem.fromUri(
                        lastSelectedUri
                );


        EditedMediaItem editedMediaItem =
                new EditedMediaItem.Builder(
                        mediaItem
                )
                        .setRemoveVideo(true)
                        .build();


        // =====================================================
        // START
        // =====================================================

        try {

            sendProgress(
                    "Audio ကို ချုံ့နေပါတယ်..."
            );


            transformer.start(
                    editedMediaItem,
                    outputFile.getAbsolutePath()
            );

        } catch (Exception e) {

            if (outputFile.exists()) {
                outputFile.delete();
            }


            sendResult(
                    false,
                    "Compression စတင်မရပါ:\n" +
                            e.getMessage(),
                    originalFileSize,
                    0,
                    ""
            );
        }
    }


    // =========================================================
    // JAVASCRIPT BRIDGE
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

            runOnUiThread(() -> {

                sendProgress(
                        "Audio ကို ချုံ့နေပါတယ်..."
                );

                MainActivity.this.compressAudio(
                        bitrateKbps
                );
            });
        }
    }


    // =========================================================
    // PROGRESS CALLBACK
    // =========================================================

    private void sendProgress(
            String message) {

        String safe =
                escapeJsString(message);


        runOnUiThread(() -> {

            webView.evaluateJavascript(
                    "if(window.onCompressionProgress){" +
                            "window.onCompressionProgress('" +
                            safe +
                            "');}",
                    null
            );
        });
    }


    // =========================================================
    // RESULT CALLBACK
    // =========================================================

    private void sendResult(
            boolean success,
            String message,
            long originalSize,
            long compressedSize,
            String outputPath) {

        String safeMessage =
                escapeJsString(message);


        String safePath =
                escapeJsString(outputPath);


        String js =
                "if(window.onCompressionFinished){" +
                        "window.onCompressionFinished(" +
                        success +
                        ",'" +
                        safeMessage +
                        "'," +
                        originalSize +
                        "," +
                        compressedSize +
                        ",'" +
                        safePath +
                        "');}";


        runOnUiThread(() ->
                webView.evaluateJavascript(
                        js,
                        null
                )
        );
    }


    // =========================================================
    // ESCAPE JAVASCRIPT STRING
    // =========================================================

    private String escapeJsString(
            String text) {

        if (text == null) {
            return "";
        }


        return text
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }


    // =========================================================
    // FORMAT FILE SIZE
    // =========================================================

    private String formatSize(
            long bytes) {

        if (bytes <= 0) {
            return "0 MB";
        }


        double mb =
                bytes / 1024.0 / 1024.0;


        if (mb < 1) {

            double kb =
                    bytes / 1024.0;


            return String.format(
                    Locale.US,
                    "%.1f KB",
                    kb
            );
        }


        return String.format(
                Locale.US,
                "%.2f MB",
                mb
        );
    }
}
