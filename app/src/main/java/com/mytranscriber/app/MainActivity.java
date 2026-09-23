package com.mytranscriber.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.Toast;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class MainActivity extends Activity {

    private WebView webView;

    private ValueCallback<Uri[]> filePathCallback;

    /*
     * HTML မှာရွေးထားတဲ့ Android URI
     */
    private Uri lastSelectedUri;

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int RECORD_AUDIO_REQUEST = 2001;


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

        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        settings.setMediaPlaybackRequiresUserGesture(false);


        /*
         * JavaScript -> Android
         */

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "AndroidBridge"
        );


        /*
         * WebView Client
         */

        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request
                    ) {

                        String url =
                                request.getUrl().toString();

                        if (url.startsWith("https://t.me/")) {

                            Intent intent =
                                    new Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(url)
                                    );

                            startActivity(intent);

                            return true;
                        }

                        return false;
                    }
                }
        );


        /*
         * Chrome Client
         */

        webView.setWebChromeClient(
                new WebChromeClient() {

                    @Override
                    public boolean onShowFileChooser(
                            WebView webView,
                            ValueCallback<Uri[]> callback,
                            FileChooserParams params
                    ) {

                        if (filePathCallback != null) {
                            filePathCallback.onReceiveValue(null);
                        }

                        filePathCallback =
                                callback;


                        Intent intent =
                                new Intent(
                                        Intent.ACTION_OPEN_DOCUMENT
                                );

                        intent.addCategory(
                                Intent.CATEGORY_OPENABLE
                        );

                        intent.setType("*/*");

                        intent.putExtra(
                                Intent.EXTRA_ALLOW_MULTIPLE,
                                false
                        );


                        startActivityForResult(
                                intent,
                                FILE_CHOOSER_REQUEST
                        );


                        return true;
                    }


                    @Override
                    public void onPermissionRequest(
                            final PermissionRequest request
                    ) {

                        runOnUiThread(
                                new Runnable() {

                                    @Override
                                    public void run() {

                                        request.grant(
                                                request.getResources()
                                        );

                                    }

                                }
                        );
                    }

                }
        );


        /*
         * WebView Download
         */

        webView.setDownloadListener(
                (url, userAgent, contentDisposition,
                 mimeType, contentLength) -> {

                    try {

                        android.app.DownloadManager
                                .Request request =
                                new android.app.DownloadManager
                                        .Request(
                                                Uri.parse(url)
                                        );

                        request.setMimeType(mimeType);

                        request.setNotificationVisibility(
                                android.app.DownloadManager
                                        .Request
                                        .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                        );

                        request.setDestinationInExternalPublicDir(
                                Environment.DIRECTORY_DOWNLOADS,
                                "MyTranscriber"
                        );

                        android.app.DownloadManager manager =
                                (android.app.DownloadManager)
                                        getSystemService(
                                                DOWNLOAD_SERVICE
                                        );

                        manager.enqueue(request);

                    } catch (Exception e) {

                        Toast.makeText(
                                MainActivity.this,
                                "Download မအောင်မြင်ပါ",
                                Toast.LENGTH_SHORT
                        ).show();

                    }

                }
        );


        /*
         * Microphone permission
         */

        if (Build.VERSION.SDK_INT >= 23) {

            if (checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.RECORD_AUDIO
                        },
                        RECORD_AUDIO_REQUEST
                );

            }

        }


        webView.loadUrl(
                "file:///android_asset/index.html"
        );


        setContentView(webView);
    }


    /*
     * File chooser result
     */

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


        if (requestCode ==
                FILE_CHOOSER_REQUEST) {

            Uri[] results = null;


            if (resultCode ==
                    RESULT_OK &&
                data != null) {

                Uri uri =
                        data.getData();


                if (uri != null) {

                    results =
                            new Uri[]{uri};


                    /*
                     * Compressor အတွက်
                     * နောက်ဆုံးရွေးထားတဲ့ URI
                     */

                    lastSelectedUri =
                            uri;


                    try {

                        getContentResolver()
                                .takePersistableUriPermission(
                                        uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                );

                    } catch (Exception ignored) {
                    }

                }

            }


            if (filePathCallback != null) {

                filePathCallback.onReceiveValue(
                        results
                );

                filePathCallback = null;
            }

        }
    }


    /*
     * =====================================
     * ANDROID JAVASCRIPT BRIDGE
     * =====================================
     */

    public class AndroidBridge {


        @JavascriptInterface
        public void compressLastSelectedFile(
                final int bitrateKbps
        ) {

            if (lastSelectedUri == null) {

                runOnUiThread(
                        () ->
                                sendCompressionResult(
                                        false,
                                        "ဖိုင်ကို Android မှ မတွေ့ပါ။ " +
                                        "ဖိုင်ကို ပြန်ရွေးပါ။",
                                        0,
                                        0,
                                        ""
                                )
                );

                return;
            }


            new Thread(
                    () -> {

                        try {

                            long originalSize =
                                    getFileSize(
                                            lastSelectedUri
                                    );


                            File outputFile =
                                    compressToM4a(
                                            lastSelectedUri,
                                            bitrateKbps
                                    );


                            long compressedSize =
                                    outputFile.length();


                            runOnUiThread(
                                    () ->
                                            sendCompressionResult(
                                                    true,
                                                    "OK",
                                                    originalSize,
                                                    compressedSize,
                                                    outputFile
                                                            .getAbsolutePath()
                                            )
                            );


                        } catch (Exception e) {

                            runOnUiThread(
                                    () ->
                                            sendCompressionResult(
                                                    false,
                                                    e.getMessage() != null
                                                            ? e.getMessage()
                                                            : "Compression Error",
                                                    0,
                                                    0,
                                                    ""
                                            )
                            );

                        }

                    }
            ).start();
        }
    }


    /*
     * =====================================
     * SEND RESULT TO JAVASCRIPT
     * =====================================
     */

    private void sendCompressionResult(
            boolean success,
            String message,
            long originalSize,
            long compressedSize,
            String outputPath
    ) {

        String js =
                "onCompressionFinished(" +
                success + "," +
                "'" + jsEscape(message) + "'," +
                originalSize + "," +
                compressedSize + "," +
                "'" + jsEscape(outputPath) + "'" +
                ");";


        webView.evaluateJavascript(
                js,
                null
        );
    }


    private String jsEscape(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }


    /*
     * =====================================
     * FILE SIZE
     * =====================================
     */

    private long getFileSize(
            Uri uri
    ) {

        Cursor cursor = null;

        try {

            cursor =
                    getContentResolver()
                            .query(
                                    uri,
                                    null,
                                    null,
                                    null,
                                    null
                            );


            if (cursor != null &&
                cursor.moveToFirst()) {

                int index =
                        cursor.getColumnIndex(
                                OpenableColumns.SIZE
                        );


                if (index >= 0) {

                    return cursor.getLong(index);
                }
            }

        } catch (Exception ignored) {

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }


        return 0;
    }


    /*
     * =====================================
     * AAC / M4A COMPRESSOR
     * =====================================
     */

    private File compressToM4a(
            Uri inputUri,
            int bitrateKbps
    ) throws Exception {


        MediaExtractor extractor =
                new MediaExtractor();


        extractor.setDataSource(
                this,
                inputUri,
                null
        );


        int audioTrack =
                -1;


        MediaFormat inputFormat =
                null;


        /*
         * Find audio track
         */

        for (
                int i = 0;
                i < extractor.getTrackCount();
                i++
        ) {

            MediaFormat format =
                    extractor.getTrackFormat(i);


            String mime =
                    format.getString(
                            MediaFormat.KEY_MIME
                    );


            if (
                    mime != null &&
                    mime.startsWith("audio/")
            ) {

                audioTrack = i;

                inputFormat = format;

                break;
            }
        }


        if (audioTrack < 0) {

            extractor.release();

            throw new Exception(
                    "Audio track မတွေ့ပါ။"
            );
        }


        extractor.selectTrack(
                audioTrack
        );


        String inputMime =
                inputFormat.getString(
                        MediaFormat.KEY_MIME
                );


        /*
         * Decoder
         */

        MediaCodec decoder =
                MediaCodec.createDecoderByType(
                        inputMime
                );


        decoder.configure(
                inputFormat,
                null,
                null,
                0
        );


        decoder.start();


        /*
         * Output sample rate
         */

        int sampleRate =
                inputFormat.containsKey(
                        MediaFormat.KEY_SAMPLE_RATE
                )
                ? inputFormat.getInteger(
                        MediaFormat.KEY_SAMPLE_RATE
                )
                : 44100;


        int channels =
                inputFormat.containsKey(
                        MediaFormat.KEY_CHANNEL_COUNT
                )
                ? inputFormat.getInteger(
                        MediaFormat.KEY_CHANNEL_COUNT
                )
                : 2;


        /*
         * AAC encoder က
         * stereo/mono ကို support လုပ်တယ်
         */

        if (channels < 1) {
            channels = 1;
        }

        if (channels > 2) {
            channels = 2;
        }


        /*
         * AAC Encoder
         */

        MediaFormat outputFormat =
                MediaFormat.createAudioFormat(
                        "audio/mp4a-latm",
                        sampleRate,
                        channels
                );


        outputFormat.setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                2
        );


        outputFormat.setInteger(
                MediaFormat.KEY_BIT_RATE,
                bitrateKbps * 1000
        );


        outputFormat.setInteger(
                MediaFormat.KEY_MAX_INPUT_SIZE,
                16384
        );


        MediaCodec encoder =
                MediaCodec.createEncoderByType(
                        "audio/mp4a-latm"
                );


        encoder.configure(
                outputFormat,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
        );


        encoder.start();


        /*
         * Output folder
         */

        File directory =
                new File(
                        Environment
                                .getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_DOWNLOADS
                                ),
                        "MyTranscriber"
                );


        if (!directory.exists()) {

            if (!directory.mkdirs()) {

                decoder.stop();
                decoder.release();

                encoder.stop();
                encoder.release();

                extractor.release();

                throw new Exception(
                        "Output folder ဖန်တီး၍မရပါ။"
                );
            }
        }


        String fileName =
                "compressed_" +
                System.currentTimeMillis() +
                ".m4a";


        File outputFile =
                new File(
                        directory,
                        fileName
                );


        MediaMuxer muxer =
                new MediaMuxer(
                        outputFile.getAbsolutePath(),
                        MediaMuxer.OutputFormat
                                .MUXER_OUTPUT_MPEG_4
                );


        int outputTrack =
                -1;


        boolean muxerStarted =
                false;


        MediaCodec.BufferInfo bufferInfo =
                new MediaCodec.BufferInfo();


        boolean inputDone =
                false;

        boolean decoderDone =
                false;

        boolean encoderDone =
                false;


        long timeoutUs =
                10000;


        try {

            while (!encoderDone) {


                /*
                 * -------------------------
                 * Feed decoder
                 * -------------------------
                 */

                if (!inputDone) {

                    int inputIndex =
                            decoder.dequeueInputBuffer(
                                    timeoutUs
                            );


                    if (inputIndex >= 0) {

                        ByteBuffer inputBuffer =
                                decoder.getInputBuffer(
                                        inputIndex
                                );


                        int sampleSize =
                                extractor.readSampleData(
                                        inputBuffer,
                                        0
                                );


                        if (sampleSize < 0) {

                            decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec
                                            .BUFFER_FLAG_END_OF_STREAM
                            );

                            inputDone = true;

                        } else {

                            long presentationTime =
                                    extractor
                                            .getSampleTime();


                            decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    presentationTime,
                                    0
                            );


                            extractor.advance();
                        }
                    }
                }


                /*
                 * -------------------------
                 * Decoder output
                 * -------------------------
                 */

                if (!decoderDone) {

                    int outputIndex =
                            decoder.dequeueOutputBuffer(
                                    bufferInfo,
                                    timeoutUs
                            );


                    if (outputIndex ==
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                        /*
                         * Decoder format changed.
                         * PCM output format is now known.
                         */

                    } else if (outputIndex >= 0) {

                        ByteBuffer decodedBuffer =
                                decoder.getOutputBuffer(
                                        outputIndex
                                );


                        if (decodedBuffer != null &&
                            bufferInfo.size > 0) {


                            /*
                             * Feed PCM into encoder
                             */

                            int encoderInputIndex =
                                    encoder.dequeueInputBuffer(
                                            timeoutUs
                                    );


                            if (encoderInputIndex >= 0) {

                                ByteBuffer encoderInput =
                                        encoder.getInputBuffer(
                                                encoderInputIndex
                                        );


                                encoderInput.clear();


                                decodedBuffer.position(
                                        bufferInfo.offset
                                );


                                decodedBuffer.limit(
                                        bufferInfo.offset +
                                        bufferInfo.size
                                );


                                encoderInput.put(
                                        decodedBuffer
                                );


                                encoder.queueInputBuffer(
                                        encoderInputIndex,
                                        0,
                                        bufferInfo.size,
                                        bufferInfo.presentationTimeUs,
                                        0
                                );
                            }
                        }


                        if (
                                (bufferInfo.flags &
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                != 0
                        ) {

                            decoderDone = true;


                            int encoderInputIndex =
                                    encoder.dequeueInputBuffer(
                                            timeoutUs
                                    );


                            if (encoderInputIndex >= 0) {

                                encoder.queueInputBuffer(
                                        encoderInputIndex,
                                        0,
                                        0,
                                        bufferInfo.presentationTimeUs,
                                        MediaCodec
                                                .BUFFER_FLAG_END_OF_STREAM
                                );
                            }
                        }


                        decoder.releaseOutputBuffer(
                                outputIndex,
                                false
                        );
                    }
                }


                /*
                 * -------------------------
                 * Encoder output
                 * -------------------------
                 */

                int encoderOutputIndex =
                        encoder.dequeueOutputBuffer(
                                bufferInfo,
                                timeoutUs
                        );


                if (
                        encoderOutputIndex ==
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
                ) {

                    MediaFormat newFormat =
                            encoder.getOutputFormat();


                    outputTrack =
                            muxer.addTrack(
                                    newFormat
                            );


                    muxer.start();

                    muxerStarted = true;


                } else if (
                        encoderOutputIndex >= 0
                ) {

                    ByteBuffer encodedBuffer =
                            encoder.getOutputBuffer(
                                    encoderOutputIndex
                            );


                    if (encodedBuffer != null &&
                        bufferInfo.size > 0 &&
                        muxerStarted) {


                        encodedBuffer.position(
                                bufferInfo.offset
                        );


                        encodedBuffer.limit(
                                bufferInfo.offset +
                                bufferInfo.size
                        );


                        muxer.writeSampleData(
                                outputTrack,
                                encodedBuffer,
                                bufferInfo
                        );
                    }


                    if (
                            (bufferInfo.flags &
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            != 0
                    ) {

                        encoderDone = true;
                    }


                    encoder.releaseOutputBuffer(
                            encoderOutputIndex,
                            false
                    );
                }
            }


        } finally {


            try {
                extractor.release();
            } catch (Exception ignored) {
            }


            try {
                decoder.stop();
            } catch (Exception ignored) {
            }


            try {
                decoder.release();
            } catch (Exception ignored) {
            }


            try {
                encoder.stop();
            } catch (Exception ignored) {
            }


            try {
                encoder.release();
            } catch (Exception ignored) {
            }


            try {

                if (muxerStarted) {
                    muxer.stop();
                }

            } catch (Exception ignored) {
            }


            try {
                muxer.release();
            } catch (Exception ignored) {
            }
        }


        if (!outputFile.exists() ||
            outputFile.length() == 0) {

            throw new Exception(
                    "M4A ဖိုင်ထုတ်၍ မရပါ။"
            );
        }


        return outputFile;
    }


    /*
     * =====================================
     * BACK BUTTON
     * =====================================
     */

    @Override
    public void onBackPressed() {

        if (
                webView != null &&
                webView.canGoBack()
        ) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }
}
