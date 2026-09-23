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


        webView.setWebViewClient(
                new WebViewClient()
        );


        webView.setWebChromeClient(
                new WebChromeClient() {

                    @Override
                    public boolean onShowFileChooser(
                            WebView webView,
                            ValueCallback<Uri[]> filePathCallback,
                            FileChooserParams fileChooserParams) {

                        if (MainActivity.this.filePathCallback != null) {

                            MainActivity.this.filePathCallback
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

                            MainActivity.this.filePathCallback =
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


        // -----------------------------------------------------
        // WEBVIEW FILE PICKER
        // -----------------------------------------------------

        if (requestCode ==
                WEB_FILE_PICKER_REQUEST) {

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

                filePathCallback
                        .onReceiveValue(results);

                filePathCallback = null;
            }

            return;
        }


        // -----------------------------------------------------
        // SAVE COMPRESSED FILE
        // -----------------------------------------------------

        if (requestCode ==
                SAVE_FILE_REQUEST) {

            if (resultCode == RESULT_OK
                    && data != null
                    && data.getData() != null
                    && pendingDownloadPath != null
                    && !pendingDownloadPath.isEmpty()) {

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


        // -----------------------------------------------------
        // COMPRESSOR FILE PICKER
        // -----------------------------------------------------

        if (requestCode ==
                FILE_PICKER_REQUEST
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {

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

        if (lastSelectedUri == null
