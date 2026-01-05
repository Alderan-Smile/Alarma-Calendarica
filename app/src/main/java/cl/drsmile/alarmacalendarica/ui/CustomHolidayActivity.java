package cl.drsmile.alarmacalendarica.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;
import java.util.Date;

import cl.drsmile.alarmacalendarica.R;
import cl.drsmile.alarmacalendarica.conn.LocalRepository;
import cl.drsmile.alarmacalendarica.db.HolidayEntity;

public class CustomHolidayActivity extends AppCompatActivity {
    private EditText etName;
    private Button btnPickStart, btnPickEnd, btnSave;
    private Calendar startCal, endCal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_holiday);

        etName = findViewById(R.id.et_holiday_name);
        btnPickStart = findViewById(R.id.btn_pick_start_date);
        btnPickEnd = findViewById(R.id.btn_pick_end_date);
        btnSave = findViewById(R.id.btn_save);

        startCal = Calendar.getInstance();
        endCal = (Calendar) startCal.clone();

        btnPickStart.setText(android.text.format.DateFormat.getDateFormat(this).format(startCal.getTime()));
        btnPickEnd.setText(android.text.format.DateFormat.getDateFormat(this).format(endCal.getTime()));

        btnPickStart.setOnClickListener(v -> {
            DatePickerDialog dp = new DatePickerDialog(CustomHolidayActivity.this, (DatePicker view, int year, int month, int dayOfMonth) -> {
                startCal.set(Calendar.YEAR, year);
                startCal.set(Calendar.MONTH, month);
                startCal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                btnPickStart.setText(android.text.format.DateFormat.getDateFormat(CustomHolidayActivity.this).format(startCal.getTime()));
                // ensure end is not before start
                if (endCal.before(startCal)) {
                    endCal.setTime(startCal.getTime());
                    btnPickEnd.setText(android.text.format.DateFormat.getDateFormat(CustomHolidayActivity.this).format(endCal.getTime()));
                }
            }, startCal.get(Calendar.YEAR), startCal.get(Calendar.MONTH), startCal.get(Calendar.DAY_OF_MONTH));
            dp.show();
        });

        btnPickEnd.setOnClickListener(v -> {
            DatePickerDialog dp = new DatePickerDialog(CustomHolidayActivity.this, (DatePicker view, int year, int month, int dayOfMonth) -> {
                endCal.set(Calendar.YEAR, year);
                endCal.set(Calendar.MONTH, month);
                endCal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                btnPickEnd.setText(android.text.format.DateFormat.getDateFormat(CustomHolidayActivity.this).format(endCal.getTime()));
                // ensure start not after end
                if (startCal.after(endCal)) {
                    startCal.setTime(endCal.getTime());
                    btnPickStart.setText(android.text.format.DateFormat.getDateFormat(CustomHolidayActivity.this).format(startCal.getTime()));
                }
            }, endCal.get(Calendar.YEAR), endCal.get(Calendar.MONTH), endCal.get(Calendar.DAY_OF_MONTH));
            dp.show();
        });

        btnSave.setOnClickListener(v -> checkAndSave());
    }

    private void checkAndSave() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Ingresa un nombre para el feriado", Toast.LENGTH_SHORT).show();
            return;
        }
        // check overlaps for range
        LocalRepository repo = new LocalRepository(this);
        Date start = new Date(startCal.getTimeInMillis());
        Date end = new Date(endCal.getTimeInMillis());
        repo.findHolidaysInRange(start, end, list -> {
            if (list != null && !list.isEmpty()) {
                // build message summarizing overlap (count and how many are custom)
                int total = list.size();
                int custom = 0;
                for (HolidayEntity h : list) if (h.isCustom) custom++;
                String msg = "Se encontraron " + total + " feriados en el periodo (" + custom + " personalizados).\n¿Deseas añadir igualmente?";
                runOnUiThread(() -> new AlertDialog.Builder(CustomHolidayActivity.this)
                        .setTitle("Conflicto de fechas")
                        .setMessage(msg)
                        .setPositiveButton("Continuar", (d, w) -> doSave(name, start, end))
                        .setNegativeButton("Cancelar", null)
                        .show());
            } else {
                doSave(name, start, end);
            }
        });
    }

    private void doSave(String name, Date start, Date end) {
        String country = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this).getString("pref_country", "CL");
        // if single day
        if (start.getTime() == end.getTime()) {
            HolidayEntity he = new HolidayEntity();
            he.localName = name;
            he.name = name;
            he.date = start;
            Calendar c = Calendar.getInstance(); c.setTime(start);
            he.year = c.get(Calendar.YEAR);
            he.countryCode = country;
            he.isCustom = true;
            new LocalRepository(this).insertCustomHoliday(he);
        } else {
            new LocalRepository(this).insertCustomHolidayRange(start, end, name, country);
        }
        runOnUiThread(() -> {
            Toast.makeText(CustomHolidayActivity.this, "Feriado personalizado guardado", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        });
    }
}
