package cl.drsmile.alarmacalendarica.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cl.drsmile.alarmacalendarica.R;
import cl.drsmile.alarmacalendarica.conn.LocalRepository;
import cl.drsmile.alarmacalendarica.db.HolidayEntity;

import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// explicit import for adapter (same package) to ensure resolution
import cl.drsmile.alarmacalendarica.ui.CustomHolidayAdapter;

public class CustomHolidayListActivity extends AppCompatActivity implements CustomHolidayAdapter.OnItemActionListener {

    private RecyclerView rv;
    private CustomHolidayAdapter adapter;
    private EditText etSearch;
    private Button btnExport, btnPickFrom, btnPickTo, btnDeleteRange;
    private TextView tvEmpty;
    private Date fromDate, toDate;
    private Calendar fromCal, toCal;
    private LocalRepository repo;
    private String country;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_holiday_list);
        // Apply window insets to avoid overlap with status/navigation
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop() + sys.top, v.getPaddingRight(), v.getPaddingBottom() + sys.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(findViewById(android.R.id.content));

        repo = new LocalRepository(this);
        // default country from preferences
        country = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this).getString("pref_country", "CL");

        rv = findViewById(R.id.rv_holidays);
        etSearch = findViewById(R.id.et_search);
        btnExport = findViewById(R.id.btn_export);
        btnPickFrom = findViewById(R.id.btn_pick_from);
        btnPickTo = findViewById(R.id.btn_pick_to);
        btnDeleteRange = findViewById(R.id.btn_delete_range);
        tvEmpty = findViewById(R.id.tv_empty);

        adapter = new CustomHolidayAdapter(new ArrayList<>(), this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        fromCal = Calendar.getInstance();
        toCal = Calendar.getInstance();
        fromDate = new Date(fromCal.getTimeInMillis());
        toDate = new Date(toCal.getTimeInMillis());
        updatePickButtons();

        btnPickFrom.setOnClickListener(v -> showDatePicker(true));
        btnPickTo.setOnClickListener(v -> showDatePicker(false));

        btnExport.setOnClickListener(v -> doExport());
        btnDeleteRange.setOnClickListener(v -> doDeleteRange());

        loadList();
    }

    private void updatePickButtons() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        btnPickFrom.setText(fmt.format(fromDate));
        btnPickTo.setText(fmt.format(toDate));
    }

    private void showDatePicker(boolean isFrom) {
        Calendar cal = isFrom ? fromCal : toCal;
        DatePickerDialog dp = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            if (isFrom) fromDate = cal.getTime(); else toDate = cal.getTime();
            if (fromDate.after(toDate)) { // ensure order
                if (isFrom) { toCal.setTime(fromDate); toDate = fromDate; } else { fromCal.setTime(toDate); fromDate = toDate; }
            }
            updatePickButtons();
            loadList();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dp.show();
    }

    private void loadList() {
        // load custom holidays in range and filter by search
        repo.findHolidaysInRange(fromDate, toDate, list -> runOnUiThread(() -> {
            List<HolidayEntity> filtered = new ArrayList<>();
            if (list != null) {
                for (HolidayEntity h : list) {
                    if (!h.isCustom) continue; // only custom
                    String q = etSearch.getText() != null ? etSearch.getText().toString().toLowerCase() : "";
                    if (!q.isEmpty()) {
                        if ((h.localName != null && h.localName.toLowerCase().contains(q)) || (h.name != null && h.name.toLowerCase().contains(q))) {
                            filtered.add(h);
                        }
                    } else filtered.add(h);
                }
            }
            adapter.setItems(filtered);
            tvEmpty.setVisibility(filtered.isEmpty() ? TextView.VISIBLE : TextView.GONE);
        }));
    }

    private void doExport() {
        repo.exportCustomHolidaysToCsv(fromDate, toDate, country, path -> runOnUiThread(() -> {
            if (path == null) {
                Toast.makeText(CustomHolidayListActivity.this, "Error al exportar", Toast.LENGTH_SHORT).show();
                return;
            }
            // share file
            File f = new File(path);
            try {
                Uri uri = FileProvider.getUriForFile(CustomHolidayListActivity.this, getPackageName() + ".fileprovider", f);
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/csv");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share, "Compartir CSV"));
            } catch (Exception ex) {
                Toast.makeText(CustomHolidayListActivity.this, "No se pudo compartir el archivo", Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private void doDeleteRange() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Eliminar feriados personalizados")
                .setMessage("¿Eliminar solo los feriados personalizados en el rango seleccionado?")
                .setPositiveButton("Eliminar", (d, w) -> {
                    repo.deleteCustomHolidaysInRange(fromDate, toDate, country, deleted -> runOnUiThread(() -> {
                        Toast.makeText(CustomHolidayListActivity.this, "Eliminados: " + deleted, Toast.LENGTH_SHORT).show();
                        loadList();
                    }));
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public void onEdit(HolidayEntity holiday) {
        // open editor activity (reuse CustomHolidayActivity for editing single day?)
        Toast.makeText(this, "Editar no implementado", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDelete(HolidayEntity holiday) {
        repo.deleteHolidayById(holiday.id, v -> runOnUiThread(() -> loadList()));
    }
}
