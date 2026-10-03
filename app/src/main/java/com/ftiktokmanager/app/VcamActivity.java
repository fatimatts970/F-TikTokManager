package com.ftiktokmanager.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.InputStream;

public class VcamActivity extends AppCompatActivity {
    private Uri imageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vcam);

        final ImageView imgPreview = findViewById(R.id.imgPreview);
        String uriStr = getIntent().getStringExtra("image_uri");

        if (uriStr != null && !uriStr.isEmpty()) {
            imageUri = Uri.parse(uriStr);
            loadPreview(imgPreview, imageUri);
        } else {
            Toast.makeText(this, "No image set! Select an image from toolbar first.", Toast.LENGTH_LONG).show();
        }

        findViewById(R.id.btnCancel).setOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });

        findViewById(R.id.btnCapture).setOnClickListener(v -> {
            if (imageUri != null) {
                Intent result = new Intent();
                result.setData(imageUri);
                setResult(Activity.RESULT_OK, result);
            } else {
                setResult(Activity.RESULT_CANCELED);
            }
            finish();
        });
    }

    /** Decodes a down-sampled bitmap off the main thread (big photos used to freeze / crash the old screen). */
    private void loadPreview(final ImageView target, final Uri uri) {
        App.io(() -> {
            Bitmap bmp = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, bounds);
                }
                int sample = 1;
                int longest = Math.max(bounds.outWidth, bounds.outHeight);
                while (longest / sample > 2048) sample *= 2;
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    bmp = BitmapFactory.decodeStream(in, null, opts);
                }
            } catch (Exception | OutOfMemoryError ignored) {
            }
            final Bitmap result = bmp;
            App.ui(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (result != null) {
                    target.setImageBitmap(result);
                } else {
                    imageUri = null;
                    Toast.makeText(this, "Could not open the VCAM image. Pick it again.", Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
