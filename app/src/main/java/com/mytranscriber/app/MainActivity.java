package com.mytranscriber.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
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

    private Uri lastSelectedUri;
    private long originalFileSize = 0;

    private static final int FILE_PICKER_REQUEST = 1001;

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

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "AndroidBridge"
        );

        webView.loadUrl("file:///android_asset/index.html");

        setContentView(webView);
    }

    // =========================================================
    // FILE PICKER
    // =========================================================

    public void openFilePicker() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.addCategory(Intent.CATEGORY_OPENABLE);

        intent.setType("*/*");

        intent.putExtra(
                Intent.EXTRA_MIME_TYPES,
                new String[]{
                        "audio/*",
                        "video/*"
                }
        );

        startActivityForResult(intent, FILE_PICKER_REQUEST);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_PICKER_REQUEST
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {

            lastSelectedUri = data.getData();

            try {
                final int takeFlags =
                        data.getFlags()
                                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                getContentResolver().takePersistableUriPermission(
                        lastSelectedUri,
                        takeFlags
                );
            } catch (Exception ignored) {
            }

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

                    if (cursor.moveToFirst() && sizeIndex >= 0) {
                        originalFileSize =
                                cursor.getLong(sizeIndex);
                    }

                    cursor.close();
                }

            } catch (Exception e) {
                originalFileSize = 0;
            }

            String name = getFileName(lastSelectedUri);

            final String jsName =
                    name.replace("\\", "\\\\")
                            .replace("'", "\\'");

            runOnUiThread(() -> {

                webView.evaluateJavascript(
                        "if(window.onNativeFileSelected){" +
                                "window.onNativeFileSelected('" +
                                jsName +
                                "'," +
                                originalFileSize +
                                ");}",
                        null
                );
            });
        }
    }

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

                if (cursor.moveToFirst() && nameIndex >= 0) {
                    result = cursor.getString(nameIndex);
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
                ).format(new Date());

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
                new DefaultEncoderFactory.Builder(this)
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
                                            ExportResult result
                                    ) {

                                        long compressedSize =
                                                outputFile.length();

                                        /*
                                         * အရေးကြီးတဲ့ check
                                         *
                                         * Output က input ထက်
                                         * ကြီးသွားရင် မသိမ်းဘူး။
                                         */

                                        if (compressedSize >= originalFileSize) {

                                            outputFile.delete();

                                            sendResult(
                                                    false,
                                                    "Compression လုပ်ပြီးနောက် size မလျော့ပါ။ " +
                                                            "ပိုမြင့်တဲ့ bitrate မသုံးဘဲ " +
                                                            "48/64 kbps ကိုရွေးပါ။",
                                                    originalFileSize,
                                                    compressedSize,
                                                    ""
                                            );

                                            return;
                                        }

                                        long saved =
                                                originalFileSize
                                                        - compressedSize;

                                        double percent =
                                                (saved * 100.0)
                                                        / originalFileSize;

                                        String message =
                                                "Audio compression ပြီးပါပြီ။\n\n" +
                                                        "မူရင်း: " +
                                                        formatSize(originalFileSize) +
                                                        "\n" +
                                                        "အသစ်: " +
                                                        formatSize(compressedSize) +
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
                                            ExportException exception
                                    ) {

                                        if (outputFile.exists()) {
                                            outputFile.delete();
                                        }

                                        sendResult(
                                                false,
                                                "Compression မအောင်မြင်ပါ:\n" +
                                                        exception.getMessage(),
                                                originalFileSize,
                                                0,
                                                ""
                                        );
                                    }
                                }
                        )
                        .build();

        /*
         * Video ဖြစ်နေလည်း
         * Video track ကို လုံးဝဖယ်မယ်။
         *
         * Audio ပဲ output ထွက်မယ်။
         */
        MediaItem mediaItem =
                MediaItem.fromUri(lastSelectedUri);

        EditedMediaItem editedMediaItem =
                new EditedMediaItem.Builder(mediaItem)
                        .setRemoveVideo(true)
                        .build();

        try {

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
        public void compressAudio(int bitrateKbps) {

            runOnUiThread(() -> {

                sendProgress(
                        "Audio ကို ချုံ့နေပါတယ်..."
                );

                compressAudio(bitrateKbps);
            });
        }
    }

    // =========================================================
    // JS CALLBACK
    // =========================================================

    private void sendProgress(String message) {

        String safe =
                message.replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n");

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

    private void sendResult(
            boolean success,
            String message,
            long originalSize,
            long compressedSize,
            String outputPath
    ) {

        String safeMessage =
                message.replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n");

        String safePath =
                outputPath.replace("\\", "\\\\")
                        .replace("'", "\\'");

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

    private String formatSize(long bytes) {

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
