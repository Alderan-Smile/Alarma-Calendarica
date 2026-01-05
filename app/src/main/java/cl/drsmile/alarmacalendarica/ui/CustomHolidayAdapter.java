package cl.drsmile.alarmacalendarica.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import cl.drsmile.alarmacalendarica.R;
import cl.drsmile.alarmacalendarica.db.HolidayEntity;

public class CustomHolidayAdapter extends RecyclerView.Adapter<CustomHolidayAdapter.VH> {

    private List<HolidayEntity> items;
    private final OnItemActionListener listener;
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public interface OnItemActionListener {
        void onEdit(HolidayEntity holiday);
        void onDelete(HolidayEntity holiday);
    }

    public CustomHolidayAdapter(List<HolidayEntity> items, OnItemActionListener l) {
        this.items = items;
        this.listener = l;
    }

    public void setItems(List<HolidayEntity> list) {
        this.items = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_custom_holiday, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        HolidayEntity h = items.get(position);
        holder.tvDate.setText(h.date != null ? fmt.format(h.date) : "");
        holder.tvName.setText(h.localName != null ? h.localName : "");
        holder.tvBadge.setVisibility(h.isCustom ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(h);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onDelete(h);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvDate, tvName, tvBadge;
        VH(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvName = itemView.findViewById(R.id.tv_name);
            tvBadge = itemView.findViewById(R.id.tv_badge);
        }
    }
}
