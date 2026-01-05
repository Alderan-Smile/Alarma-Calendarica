package cl.drsmile.alarmacalendarica.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsCompat.Type;
import androidx.core.graphics.Insets;
import android.widget.ScrollView;

import java.util.Calendar;

import cl.drsmile.alarmacalendarica.db.AlarmEntity;
import cl.drsmile.alarmacalendarica.conn.AlarmRepository;
import cl.drsmile.alarmacalendarica.scheduler.AlarmScheduler;
import cl.drsmile.alarmacalendarica.R;

public class AlarmEditActivity extends AppCompatActivity {
    private TimePicker timePicker;
    private CheckBox cbSun, cbMon, cbTue, cbWed, cbThu, cbFri, cbSat, cbOnlyWorkdays;
    private CheckBox cbSaturdayWorkday, cbSundayWorkday;
    private CheckBox cbOneShot;
    private EditText etLabel;
    private Button btnSave, btnCancel, btnDelete;
    private AlarmRepository repo;
    private AlarmEntity alarm;
    private TextView tvSoundLabel;
    private String selectedSoundUri;
    private ActivityResultLauncher<Intent> pickSoundLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm_edit);

        repo = new AlarmRepository(this);

        // ensure ScrollView & content avoid status/navigation bars so buttons are accessible
        final ScrollView scroll = findViewById(R.id.scroll_root);
        final View content = findViewById(R.id.ll_content);
        if (scroll != null) scroll.setClipToPadding(false);
        ViewCompat.setOnApplyWindowInsetsListener(scroll != null ? scroll : findViewById(android.R.id.content), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int top = sys.top;
            int bottom = sys.bottom;
            float density = getResources().getDisplayMetrics().density;
            int padExtra = (int) (16 * density + 0.5f);
            // apply padding to content linear layout so ScrollView can scroll under system bars
            if (content != null) content.setPadding(content.getPaddingLeft(), top + padExtra, content.getPaddingRight(), bottom + padExtra);
            return insets;
        });
        ViewCompat.requestApplyInsets(scroll != null ? scroll : findViewById(android.R.id.content));

        timePicker = findViewById(R.id.time_picker);
        cbSun = findViewById(R.id.cb_sun);
        cbMon = findViewById(R.id.cb_mon);
        cbTue = findViewById(R.id.cb_tue);
        cbWed = findViewById(R.id.cb_wed);
        cbThu = findViewById(R.id.cb_thu);
        cbFri = findViewById(R.id.cb_fri);
        cbSat = findViewById(R.id.cb_sat);
        cbSaturdayWorkday = findViewById(R.id.cb_saturday_workday);
        cbSundayWorkday = findViewById(R.id.cb_sunday_workday);
        cbOneShot = findViewById(R.id.cb_one_shot);
        etLabel = findViewById(R.id.et_label);
        cbOnlyWorkdays = findViewById(R.id.cb_only_workdays);
        btnSave = findViewById(R.id.btn_save);
        btnCancel = findViewById(R.id.btn_cancel);
        btnDelete = findViewById(R.id.btn_delete);
        Button btnPickSound = findViewById(R.id.btn_pick_sound);
        tvSoundLabel = findViewById(R.id.tv_sound_label);
        pickSoundLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                android.net.Uri uri = result.getData().getData();
                if (uri != null) {
                    try { getContentResolver().takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
                    selectedSoundUri = uri.toString();
                    tvSoundLabel.setText(uri.getLastPathSegment());
                    if (alarm != null) alarm.soundUri = selectedSoundUri;
                }
            }
        });
        btnPickSound.setOnClickListener(v -> {
            Intent intent = new Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("audio/*");
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            pickSoundLauncher.launch(intent);
        });

        long id = getIntent().getLongExtra("alarm_id", -1);
        if (id != -1) {
            repo.getById(id, a -> runOnUiThread(() -> {
                if (a == null) return;
                alarm = a;
                timePicker.setHour(alarm.hour);
                timePicker.setMinute(alarm.minute);
                etLabel.setText(alarm.label);
                setCheckFromMask(alarm.daysOfWeekMask);
                cbOnlyWorkdays.setChecked(alarm.onlyWorkdays);
                cbSaturdayWorkday.setChecked(alarm.saturdayWorkday);
                cbSundayWorkday.setChecked(alarm.sundayWorkday);
                cbOneShot.setChecked(alarm.oneShot);
                // load saved sound label
                if (alarm.soundUri != null) {
                    selectedSoundUri = alarm.soundUri;
                    try { tvSoundLabel.setText(android.net.Uri.parse(selectedSoundUri).getLastPathSegment()); } catch (Exception ignored) {}
                }
                // show delete button when editing an existing alarm
                btnDelete.setVisibility(android.view.View.VISIBLE);
            }));
        } else {
            alarm = new AlarmEntity();
            Calendar c = Calendar.getInstance();
            alarm.hour = c.get(Calendar.HOUR_OF_DAY);
            alarm.minute = c.get(Calendar.MINUTE);
            timePicker.setHour(alarm.hour);
            timePicker.setMinute(alarm.minute);
            alarm.requestCode = (int)(System.currentTimeMillis() & 0x7fffffff);
            alarm.enabled = true;
            cbOnlyWorkdays.setChecked(false);
            cbOneShot.setChecked(false);
        }

        btnSave.setOnClickListener(v -> save());
        btnCancel.setOnClickListener(v -> finish());
        btnDelete.setOnClickListener(v -> {
            if (alarm == null || alarm.id == 0) return;
            new android.app.AlertDialog.Builder(AlarmEditActivity.this)
                    .setTitle("Eliminar alarma")
                    .setMessage("¿Eliminar esta alarma?")
                    .setPositiveButton("Eliminar", (d, w) -> {
                        repo.delete(alarm);
                        AlarmScheduler.cancel(AlarmEditActivity.this, alarm);
                        cl.drsmile.alarmacalendarica.service.AlarmNotificationManager.updateNextAlarmNotification(AlarmEditActivity.this);
                        finish();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });

        // Enforce constraints: when onlyWorkdays is checked, prevent selecting weekend days unless corresponding weekendWork flag is set
        cbOnlyWorkdays.setOnCheckedChangeListener((buttonView, isChecked) -> applyWorkdayConstraints());
        cbSaturdayWorkday.setOnCheckedChangeListener((buttonView, isChecked) -> applyWorkdayConstraints());
        cbSundayWorkday.setOnCheckedChangeListener((buttonView, isChecked) -> applyWorkdayConstraints());

        // apply constraints initially after potential async load
        runOnUiThread(this::applyWorkdayConstraints);
    }

    private void setCheckFromMask(int mask) {
        cbSun.setChecked((mask & (1 << (Calendar.SUNDAY - 1))) != 0);
        cbMon.setChecked((mask & (1 << (Calendar.MONDAY - 1))) != 0);
        cbTue.setChecked((mask & (1 << (Calendar.TUESDAY - 1))) != 0);
        cbWed.setChecked((mask & (1 << (Calendar.WEDNESDAY - 1))) != 0);
        cbThu.setChecked((mask & (1 << (Calendar.THURSDAY - 1))) != 0);
        cbFri.setChecked((mask & (1 << (Calendar.FRIDAY - 1))) != 0);
        cbSat.setChecked((mask & (1 << (Calendar.SATURDAY - 1))) != 0);
    }

    private int buildMaskFromChecks() {
        int mask = 0;
        if (cbSun.isChecked()) mask |= 1 << (Calendar.SUNDAY - 1);
        if (cbMon.isChecked()) mask |= 1 << (Calendar.MONDAY - 1);
        if (cbTue.isChecked()) mask |= 1 << (Calendar.TUESDAY - 1);
        if (cbWed.isChecked()) mask |= 1 << (Calendar.WEDNESDAY - 1);
        if (cbThu.isChecked()) mask |= 1 << (Calendar.THURSDAY - 1);
        if (cbFri.isChecked()) mask |= 1 << (Calendar.FRIDAY - 1);
        if (cbSat.isChecked()) mask |= 1 << (Calendar.SATURDAY - 1);
        return mask;
    }

    private void save() {
        // validation: if onlyWorkdays is enabled, do not allow selecting Sat/Sun unless weekendWork flag set
        if (cbOnlyWorkdays.isChecked()) {
            if (cbSat.isChecked() && !cbSaturdayWorkday.isChecked()) {
                runOnUiThread(() -> Toast.makeText(this, "Habilita 'Sábados laborables' para permitir alarmas los sábados.", Toast.LENGTH_LONG).show());
                return;
            }
            if (cbSun.isChecked() && !cbSundayWorkday.isChecked()) {
                runOnUiThread(() -> Toast.makeText(this, "Habilita 'Domingos laborables' para permitir alarmas los domingos.", Toast.LENGTH_LONG).show());
                return;
            }
        }
        alarm.hour = timePicker.getHour();
        alarm.minute = timePicker.getMinute();
        alarm.label = etLabel.getText().toString();
        alarm.daysOfWeekMask = buildMaskFromChecks();
        alarm.onlyWorkdays = cbOnlyWorkdays.isChecked();
        alarm.saturdayWorkday = cbSaturdayWorkday.isChecked();
        alarm.sundayWorkday = cbSundayWorkday.isChecked();
        alarm.oneShot = cbOneShot.isChecked();
        alarm.enabled = true;

        repo.insert(alarm, id -> {
            // callback from AlarmRepository runs on background thread; schedule there to avoid Room main-thread DB access
            alarm.id = id;
            // Ensure holidays for current year and selected country are present before scheduling
            try {
                String country = androidx.preference.PreferenceManager.getDefaultSharedPreferences(AlarmEditActivity.this).getString("pref_country", null);
                int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
                if (country != null) {
                    java.util.List<cl.drsmile.alarmacalendarica.db.HolidayEntity> existing = cl.drsmile.alarmacalendarica.db.AppDatabase.getInstance(AlarmEditActivity.this).holidayDao().getByCountryAndYear(country, year);
                    if (existing == null || existing.isEmpty()) {
                        // perform synchronous fetch on this background thread
                        try {
                            cl.drsmile.alarmacalendarica.conn.ApiRepository api = new cl.drsmile.alarmacalendarica.conn.ApiRepository();
                            java.util.List<cl.drsmile.alarmacalendarica.dto.HolidayDTO> respBody = api.fetchHolidaysSync(year, country);
                            if (respBody != null) {
                                java.util.List<cl.drsmile.alarmacalendarica.db.HolidayEntity> entities = new java.util.ArrayList<>();
                                for (cl.drsmile.alarmacalendarica.dto.HolidayDTO h : respBody) {
                                    cl.drsmile.alarmacalendarica.db.HolidayEntity he = new cl.drsmile.alarmacalendarica.db.HolidayEntity();
                                    he.date = h.getDate(); he.localName = h.getLocalName(); he.name = h.getName(); he.countryCode = h.getCountryCode(); he.year = h.getYear();
                                    entities.add(he);
                                }
                                cl.drsmile.alarmacalendarica.db.AppDatabase.getInstance(AlarmEditActivity.this).holidayDao().insertAll(entities);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
            AlarmScheduler.schedule(AlarmEditActivity.this, alarm);
            // then update UI on main thread
            runOnUiThread(() -> {
                Toast.makeText(AlarmEditActivity.this, "Alarma guardada", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void applyWorkdayConstraints() {
        boolean only = cbOnlyWorkdays.isChecked();
        // if onlyWorkdays is true and saturdayWorkday not set -> disable cbSat
        if (only) {
            if (!cbSaturdayWorkday.isChecked()) {
                cbSat.setChecked(false);
                cbSat.setEnabled(false);
            } else {
                cbSat.setEnabled(true);
            }
            if (!cbSundayWorkday.isChecked()) {
                cbSun.setChecked(false);
                cbSun.setEnabled(false);
            } else {
                cbSun.setEnabled(true);
            }
        } else {
            cbSat.setEnabled(true);
            cbSun.setEnabled(true);
        }
    }

    // ActivityResultLauncher handles sound picking; no onActivityResult needed
}
