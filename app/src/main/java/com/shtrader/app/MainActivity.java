package com.shtrader.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.media.projection.MediaProjection;
import android.media.ImageReader;
import android.media.Image;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Toast;

import java.nio.ByteBuffer;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 7001;
    private MediaProjectionManager projectionManager;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private ImageView preview;
    private TextView status;
    private int screenWidth, screenHeight, screenDensity;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        screenDensity = getResources().getDisplayMetrics().densityDpi;
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 28, 24, 20);
        root.setBackgroundColor(0xFF080D16);

        TextView title = new TextView(this);
        title.setText("SH TRADER");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 18, 0, 8);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("SCREEN CAPTURE");
        sub.setTextColor(0xFF35A7FF);
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, 22);
        root.addView(sub);

        Button start = new Button(this);
        start.setText("START SCREEN CAPTURE");
        root.addView(start, new LinearLayout.LayoutParams(-1, -2));

        Button stop = new Button(this);
        stop.setText("STOP CAPTURE");
        root.addView(stop, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setText("Status: Ready. Tap START to grant screen-capture permission.");
        status.setTextColor(0xFFB7C4D3);
        status.setTextSize(14);
        status.setPadding(4, 18, 4, 12);
        root.addView(status);

        preview = new ImageView(this);
        preview.setBackgroundColor(0xFF121C2A);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, 0, 1);
        imageParams.topMargin = 8;
        root.addView(preview, imageParams);

        TextView note = new TextView(this);
        note.setText("Privacy: capture starts only after your permission. The preview stays on this device; no upload or automatic trading.");
        note.setTextColor(0xFF8191A5);
        note.setTextSize(11);
        note.setPadding(4, 14, 4, 0);
        root.addView(note);

        setContentView(root);
        start.setOnClickListener(v -> {
            if (projection == null) startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE);
        });
        stop.setOnClickListener(v -> stopCapture());
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                projection = projectionManager.getMediaProjection(resultCode, data);
                beginCapture();
            } else {
                status.setText("Status: Permission not granted.");
            }
        }
    }

    private void beginCapture() {
        try {
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
            virtualDisplay = projection.createVirtualDisplay("SH_TRADER_CAPTURE",
                    screenWidth, screenHeight, screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);
            status.setText("Status: Capturing screen. Latest preview will appear below.");
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image == null) return;
                    Image.Plane plane = image.getPlanes()[0];
                    ByteBuffer buffer = plane.getBuffer();
                    int pixelStride = plane.getPixelStride();
                    int rowStride = plane.getRowStride();
                    int rowPadding = rowStride - pixelStride * screenWidth;
                    Bitmap full = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride,
                            screenHeight, Bitmap.Config.ARGB_8888);
                    full.copyPixelsFromBuffer(buffer);
                    Bitmap cropped = Bitmap.createBitmap(full, 0, 0, screenWidth, screenHeight);
                    full.recycle();
                    runOnUiThread(() -> preview.setImageBitmap(cropped));
                } catch (Exception ignored) {
                } finally {
                    if (image != null) image.close();
                }
            }, new Handler(Looper.getMainLooper()));
        } catch (Exception e) {
            status.setText("Status: Could not start capture: " + e.getMessage());
            stopCapture();
        }
    }

    private void stopCapture() {
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (imageReader != null) { imageReader.close(); imageReader = null; }
        if (projection != null) { projection.stop(); projection = null; }
        status.setText("Status: Capture stopped.");
    }

    @Override protected void onDestroy() {
        stopCapture();
        super.onDestroy();
    }
}
