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

    /*
     * Compression ပြီးတဲ့ output file
     */
    private String pendingDownloadPath = "";

    private static final int FILE_PICKER_REQUEST = 1001;

    private static final int WEB_FILE_PICKER_REQUEST = 2001;

    private static final int SAVE_FILE_REQUEST = 3001;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

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


        /*
         * HTML <input type="file">
         * အတွက် Android file picker
         */
        webView.setWebChromeClient(
                new WebChromeClient() {

                    @Override
                    public boolean onShowFileChooser(
                            WebView webView,
                            ValueCallback<Uri[]> filePathCallback,
                            FileChooserParams fileChooserParams) {

                        if (MainActivity.this.filePathCallback != null) {

                            MainActivity.this
                                    .filePathCallback
                                    .onReceiveValue(null);
                        }


                        MainActivity.this.filePathCallback =
                                filePathCallback;


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
                                    .filePathCallback = null;

                            return false;
                        }
                    }
                }
        );


        /*
         * JavaScript ↔ Android
         */
        webView.addJavascriptInterface(
                new AndroidBridge(),
                "AndroidBridge"
        );


        webView.loadUrl(
                "file:///android_asset/index.html"
        );


        setContentView(webView);
    }


    /*
     * Compressor file picker
     */
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
                    "File picker ဖွင့်မရပါ:\n" +
                            e.getMessage(),
                    0,
                    0,
                    ""
            );
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


        /*
         * WebView HTML file input
         *
         * Groq / Gemini file picker
         */
        if (
                requestCode ==
                        WEB_FILE_PICKER_REQUEST
        ) {

            if (filePathCallback != null) {

                Uri[] results = null;


                if (
                        resultCode ==
                                RESULT_OK
                        && data != null
                        && data.getData() != null
                ) {

                    Uri selectedUri =
                            data.getData();

                    results =
                            new Uri[]{
                                    selectedUri
                            };
                }


                filePathCallback
                        .onReceiveValue(
                                results
                        );

                filePathCallback = null;
            }


            return;
        }


        /*
         * Save / Download
         */
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


                    if (!sourceFile.exists()) {

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
                            (
                                    length =
                                            input.read(
                                                    buffer
                                            )
                            ) != -1
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


        /*
         * Compressor file picker
         */
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


            /*
             * Permission
             */
            try {

                final int takeFlags =
                        data.getFlags()
                                &
                        (
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


            /*
             * File size
             */
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
                                    android.provider
                                            .OpenableColumns
                                            .SIZE
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


            /*
             * File name
             */
            String name =
                    getFileName(
                            lastSelectedUri
                    );


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


    /*
     * Get selected file name
     */
    private String getFileName(
            Uri uri) {

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
                                android.provider
                                        .OpenableColumns
                                        .DISPLAY_NAME
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


    /*
     * =========================
     * COMPRESSOR
     * =========================
     */
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


        File outputDir =
                new File(
                        getExternalFilesDir(
                                Environment
                                        .DIRECTORY_MUSIC
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


        int bitrate =
                bitrateKbps * 1000;


        AudioEncoderSettings audioSettings =
                new AudioEncoderSettings.Builder()
                        .setBitrate(bitrate)
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


                                        /*
                                         * Output size မလျော့ရင်
                                         * file ကို ဖျက်မယ်
                                         */
                                        if (
                                                compressedSize >=
                                                        originalFileSize
                                        ) {

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
                                                originalFileSize -
                                                        compressedSize;


                                        double percent =
                                                (
                                                        saved *
                                                                100.0
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
                                                outputFile
                                                        .getAbsolutePath()
                                        );
                                    }


                                    @Override
                                    public void onError(
                                            androidx.media3.transformer.Composition composition,
                                            ExportResult result,
                                            ExportException exception) {

                                        if (
                                                outputFile.exists()
                                        ) {

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


        MediaItem mediaItem =
                MediaItem.fromUri(
                        lastSelectedUri
                );


        /*
         * Video ဖြစ်ရင် video track ဖယ်မယ်
         * Audio ဖြစ်ရင် audio ကို encode မယ်
         */
        EditedMediaItem editedMediaItem =
                new EditedMediaItem.Builder(
                        mediaItem
                )
                        .setRemoveVideo(true)
                        .build();


        try {

            sendProgress(
                    "Audio ကို ချုံ့နေပါတယ်..."
            );


            transformer.start(
                    editedMediaItem,
                    outputFile.getAbsolutePath()
            );

        } catch (Exception e) {

            if (
                    outputFile.exists()
            ) {

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


    /*
     * =========================
     * ANDROID BRIDGE
     * =========================
     */
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


                MainActivity.this
                        .compressAudio(
                                bitrateKbps
                        );
            });
        }


        /*
         * =========================
         * DOWNLOAD / SAVE
         * =========================
         */
        @JavascriptInterface
        public void downloadCompressedFile(
                String outputPath) {

            runOnUiThread(() -> {

                try {

                    File sourceFile =
                            new File(
                                    outputPath
                            );


                    if (!sourceFile.exists()) {

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
                            sourceFile.getName()
                    );


                    startActivityForResult(
                            intent,
                            SAVE_FILE_REQUEST
                    );


                } catch (Exception e) {

                    showDownloadError(
                            e.getMessage()
                    );
                }
            });
        }
    }


    /*
     * =========================
     * PROGRESS
     * =========================
     */
    private void sendProgress(
            String message) {

        String safe =
                escapeJsString(
                        message
                );


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


    /*
     * =========================
     * COMPRESSION RESULT
     * =========================
     */
    private void sendResult(
            boolean success,
            String message,
            long originalSize,
            long compressedSize,
            String outputPath) {

        String safeMessage =
                escapeJsString(
                        message
                );


        String safePath =
                escapeJsString(
                        outputPath
                );


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


        runOnUiThread(() -> {

            webView.evaluateJavascript(
                    js,
                    null
            );
        });
    }


    /*
     * =========================
     * DOWNLOAD RESULT
     * =========================
     */
    private void showDownloadSuccess() {

        runOnUiThread(() -> {

            webView.evaluateJavascript(

                    "if(window.onDownloadFinished){" +
                            "window.onDownloadFinished(" +
                            "true," +
                            "'Audio ကို သိမ်းပြီးပါပြီ။'" +
                            ");}",

                    null
            );
        });
    }


    private void showDownloadError(
            String error) {

        if (error == null) {

            error =
                    "Unknown error";
        }


        String safe =
                escapeJsString(
                        error
                );


        runOnUiThread(() -> {

            webView.evaluateJavascript(

                    "if(window.onDownloadFinished){" +
                            "window.onDownloadFinished(" +
                            "false,'" +
                            safe +
                            "');}",

                    null
            );
        });
    }


    /*
     * =========================
     * JAVASCRIPT ESCAPE
     * =========================
     */
    private String escapeJsString(
            String text) {

        if (text == null) {

            return "";
        }


        return text
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "'",
                        "\\'"
                )
                .replace(
                        "\"",
                        "\\\""
                )
                .replace(
                        "\n",
                        "\\n"
                )
                .replace(
                        "\r",
                        "\\r"
                );
    }


    /*
     * =========================
     * FORMAT SIZE
     * =========================
     */
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
