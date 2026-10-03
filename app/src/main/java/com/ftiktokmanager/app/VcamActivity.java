package com.ftiktokmanager.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class VcamActivity extends AppCompatActivity {
    private Uri imageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vcam);

        ImageView imgPreview = findViewById(R.id.imgPreview);
        String uriStr = getIntent().getStringExtra("image_uri");

        if (uriStr != null && !uriStr.isEmpty()) {
            imageUri = Uri.parse(uriStr);
            imgPreview.setImageURI(imageUri);
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
}
