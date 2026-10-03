package com.ftiktokmanager.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;

public class LinksActivity extends AppCompatActivity {
    private final List<LinkModel> items = new ArrayList<>();
    private RecyclerView.Adapter<LinkHolder> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_links);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddLink).setOnClickListener(v -> addLink());

        RecyclerView rv = findViewById(R.id.rvLinks);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecyclerView.Adapter<LinkHolder>() {
            @NonNull
            @Override
            public LinkHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new LinkHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_link, parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull LinkHolder h, int position) {
                final LinkModel l = items.get(position);
                h.title.setText(l.title);
                h.url.setText(l.url);
                h.itemView.setOnClickListener(v -> Ui.openUrlInAccount(LinksActivity.this, l.url));
                h.delete.setOnClickListener(v -> confirmDelete(l));
            }

            @Override
            public int getItemCount() {
                return items.size();
            }
        };
        rv.setAdapter(adapter);
        load();
    }

    private void load() {
        App.io(() -> {
            final List<LinkModel> list = App.db().getLinks();
            App.ui(() -> {
                if (isFinishing() || isDestroyed()) return;
                items.clear();
                items.addAll(list);
                adapter.notifyDataSetChanged();
            });
        });
    }

    private void addLink() {
        Ui.promptTwo(this, "Add link", "Title (optional)", "https://…", (title, url) -> {
            String u = url.trim();
            if (u.isEmpty()) return;
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
            final String finalUrl = u;
            final String t = title.isEmpty() ? u.replaceFirst("^https?://", "") : title;
            App.io(() -> {
                if (App.db().hasLink(finalUrl)) {
                    App.ui(() -> Toast.makeText(this, "Link already saved", Toast.LENGTH_SHORT).show());
                    return;
                }
                App.db().addLink(t, finalUrl);
                load();
            });
        });
    }

    private void confirmDelete(final LinkModel l) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete link?")
                .setMessage(l.title)
                .setPositiveButton("Delete", (d, w) -> App.io(() -> {
                    App.db().deleteLink(l.id);
                    load();
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    static class LinkHolder extends RecyclerView.ViewHolder {
        final TextView title, url;
        final View delete;

        LinkHolder(View v) {
            super(v);
            title = v.findViewById(R.id.txtTitle);
            url = v.findViewById(R.id.txtUrl);
            delete = v.findViewById(R.id.btnDelete);
        }
    }
}
