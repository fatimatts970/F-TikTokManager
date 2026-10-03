package com.ftiktokmanager.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Grid: first cell is "Add account", then one card per account. */
public class AccountAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onAdd();
        void onOpen(CloneModel m);
        void onMore(CloneModel m);
    }

    private static final int T_ADD = 0;
    private static final int T_ACCOUNT = 1;

    private final List<CloneModel> items = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());
    private String deviceLine = "";

    public AccountAdapter(Listener l) {
        this.listener = l;
    }

    public void submit(List<CloneModel> list, String deviceLine) {
        items.clear();
        items.addAll(list);
        this.deviceLine = deviceLine;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? T_ADD : T_ACCOUNT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == T_ADD) {
            return new AddHolder(inf.inflate(R.layout.item_add_account, parent, false));
        }
        return new AccHolder(inf.inflate(R.layout.item_account, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AddHolder) {
            holder.itemView.setOnClickListener(v -> listener.onAdd());
            return;
        }
        final CloneModel m = items.get(position - 1);
        AccHolder h = (AccHolder) holder;
        String name = m.name == null ? "" : m.name.trim();
        h.avatar.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.getDefault()));
        h.name.setText(name);
        h.last.setText(m.lastOpened > 0 ? "Last: " + fmt.format(new Date(m.lastOpened)) : "Last: never");
        h.device.setText(deviceLine);
        h.itemView.setOnClickListener(v -> listener.onOpen(m));
        h.itemView.setOnLongClickListener(v -> {
            listener.onMore(m);
            return true;
        });
        h.more.setOnClickListener(v -> listener.onMore(m));
    }

    static class AddHolder extends RecyclerView.ViewHolder {
        AddHolder(View v) {
            super(v);
        }
    }

    static class AccHolder extends RecyclerView.ViewHolder {
        final TextView avatar, name, last, device;
        final View more;

        AccHolder(View v) {
            super(v);
            avatar = v.findViewById(R.id.txtAvatar);
            name = v.findViewById(R.id.txtName);
            last = v.findViewById(R.id.txtLast);
            device = v.findViewById(R.id.txtDevice);
            more = v.findViewById(R.id.btnMore);
        }
    }
}
