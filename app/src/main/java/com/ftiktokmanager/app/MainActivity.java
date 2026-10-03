package com.ftiktokmanager.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private DbHelper db;
    private ListView listView;
    private Button btnVcamToggle;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new DbHelper(this);
        sp = getSharedPreferences("cfg", MODE_PRIVATE);
        listView = findViewById(R.id.listViewClones);
        btnVcamToggle = findViewById(R.id.btnVcamToggle);

        updateVcamButtonState();

        btnVcamToggle.setOnClickListener(v -> {
            boolean current = sp.getBoolean("cam_virtual", false);
            sp.edit().putBoolean("cam_virtual", !current).apply();
            updateVcamButtonState();
        });

        findViewById(R.id.btnAddClone).setOnClickListener(v -> {
            int count = db.getAllAccounts().size() + 1;
            db.addAccount("Clone " + count);
            loadAccounts();
        });

        loadAccounts();
    }

    private void updateVcamButtonState() {
        boolean vcam = sp.getBoolean("cam_virtual", false);
        btnVcamToggle.setText(vcam ? "🎭 VCAM: ON" : "📷 VCAM: OFF");
    }

    private void loadAccounts() {
        List<CloneModel> accounts = db.getAllAccounts();
        if (accounts.isEmpty()) {
            db.addAccount("Clone 1");
            accounts = db.getAllAccounts();
        }

        ArrayAdapter<CloneModel> adapter = new ArrayAdapter<CloneModel>(this, R.layout.item_clone, accounts) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.item_clone, parent, false);
                }
                CloneModel acc = getItem(position);
                TextView txtAvatar = convertView.findViewById(R.id.txtAvatar);
                TextView txtName = convertView.findViewById(R.id.txtName);
                TextView txtDetails = convertView.findViewById(R.id.txtDetails);

                txtAvatar.setText("C" + acc.id);
                txtName.setText(acc.name);
                txtDetails.setText("Isolated Profile · ID #" + acc.id);

                convertView.setOnClickListener(v -> {
                    Intent intent = new Intent(MainActivity.this, WebActivity.class);
                    intent.putExtra("account_id", acc.id);
                    intent.putExtra("account_name", acc.name);
                    startActivity(intent);
                });

                return convertView;
            }
        };
        listView.setAdapter(adapter);
    }
}
