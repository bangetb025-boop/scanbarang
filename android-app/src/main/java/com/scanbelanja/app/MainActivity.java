package com.scanbelanja.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String START_URL =
            "https://bangetb025-boop.github.io/scanbarang/";

    private static final int CAMERA_REQUEST = 1001;
    private static final int FILE_CHOOSER_REQUEST = 1002;

    private WebView webView;

    private PermissionRequest pendingPermissionRequest;

    private ValueCallback<Uri[]> filePathCallback;

    private Uri cameraImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        webView.setBackgroundColor(0xFF06111F);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setBuiltInZoomControls(false);
        webView.getSettings().setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request) {

                Uri uri = request.getUrl();
                String host = uri.getHost();

                if (host != null &&
                        host.equals("bangetb025-boop.github.io")) {
                    return false;
                }

                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                }

                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            /*
             * Dipakai oleh Scan Barcode yang menggunakan
             * navigator.mediaDevices.getUserMedia()
             */
            @Override
            public void onPermissionRequest(
                    final PermissionRequest request) {

                runOnUiThread(() -> {

                    boolean wantsCamera = false;

                    for (String resource : request.getResources()) {

                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                .equals(resource)) {

                            wantsCamera = true;
                            break;
                        }
                    }

                    if (!wantsCamera) {
                        request.deny();
                        return;
                    }

                    if (Build.VERSION.SDK_INT < 23 ||
                            checkSelfPermission(
                                    Manifest.permission.CAMERA)
                                    == PackageManager.PERMISSION_GRANTED) {

                        request.grant(new String[]{
                                PermissionRequest.RESOURCE_VIDEO_CAPTURE
                        });

                    } else {

                        pendingPermissionRequest = request;

                        requestPermissions(
                                new String[]{
                                        Manifest.permission.CAMERA
                                },
                                CAMERA_REQUEST
                        );
                    }
                });
            }

            /*
             * Dipakai oleh tombol:
             * <input type="file" accept="image/*" capture="camera">
             *
             * Sebelumnya bagian ini belum ada, sehingga tombol
             * Ambil Foto tidak membuka kamera dengan benar.
             */
            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {

                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }

                MainActivity.this.filePathCallback =
                        filePathCallback;

                if (Build.VERSION.SDK_INT >= 23 &&
                        checkSelfPermission(
                                Manifest.permission.CAMERA)
                                != PackageManager.PERMISSION_GRANTED) {

                    requestPermissions(
                            new String[]{
                                    Manifest.permission.CAMERA
                            },
                            CAMERA_REQUEST
                    );

                    return true;
                }

                openCameraForPhoto();

                return true;
            }
        });

        webView.loadUrl(START_URL);
    }

    /*
     * Membuka kamera Android dan menyiapkan URI untuk foto.
     */
    private void openCameraForPhoto() {

        try {

            ContentValues values = new ContentValues();

            values.put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "ScanBelanja_" + System.currentTimeMillis() + ".jpg"
            );

            values.put(
                    MediaStore.Images.Media.MIME_TYPE,
                    "image/jpeg"
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                values.put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "Pictures/ScanBelanja"
                );
            }

            cameraImageUri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
            );

            if (cameraImageUri == null) {
                throw new Exception("Gagal membuat file foto");
            }

            Intent cameraIntent =
                    new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            cameraIntent.putExtra(
                    MediaStore.EXTRA_OUTPUT,
                    cameraImageUri
            );

            cameraIntent.addFlags(
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            if (cameraIntent.resolveActivity(
                    getPackageManager()) == null) {

                Toast.makeText(
                        this,
                        "Kamera tidak ditemukan.",
                        Toast.LENGTH_SHORT
                ).show();

                cancelFileChooser();
                return;
            }

            startActivityForResult(
                    cameraIntent,
                    FILE_CHOOSER_REQUEST
            );

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Tidak bisa membuka kamera: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();

            cancelFileChooser();
        }
    }

    private void cancelFileChooser() {

        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }

        cameraImageUri = null;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == CAMERA_REQUEST) {

            boolean granted =
                    grantResults.length > 0 &&
                    grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED;

            /*
             * Permission untuk Scan Barcode
             */
            if (pendingPermissionRequest != null) {

                if (granted) {

                    pendingPermissionRequest.grant(
                            new String[]{
                                    PermissionRequest
                                            .RESOURCE_VIDEO_CAPTURE
                            }
                    );

                } else {

                    pendingPermissionRequest.deny();

                    Toast.makeText(
                            this,
                            "Izin kamera diperlukan untuk scan.",
                            Toast.LENGTH_SHORT
                    ).show();
                }

                pendingPermissionRequest = null;
            }

            /*
             * Permission untuk Ambil Foto
             */
            if (filePathCallback != null) {

                if (granted) {

                    openCameraForPhoto();

                } else {

                    Toast.makeText(
                            this,
                            "Izin kamera diperlukan untuk mengambil foto.",
                            Toast.LENGTH_SHORT
                    ).show();

                    cancelFileChooser();
                }
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

            if (resultCode == RESULT_OK &&
                    cameraImageUri != null) {

                Uri[] results =
                        new Uri[]{cameraImageUri};

                filePathCallback.onReceiveValue(results);

            } else {

                if (cameraImageUri != null) {

                    try {
                        getContentResolver().delete(
                                cameraImageUri,
                                null,
                                null
                        );
                    } catch (Exception ignored) {
                    }
                }

                filePathCallback.onReceiveValue(null);
            }

            filePathCallback = null;
            cameraImageUri = null;
        }
    }

    @Override
    public void onBackPressed() {

        if (webView != null &&
                webView.canGoBack()) {

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
