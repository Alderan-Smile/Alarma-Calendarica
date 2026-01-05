package cl.drsmile.alarmacalendarica.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsCompat.Type;
import androidx.core.graphics.Insets;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cl.drsmile.alarmacalendarica.db.AlarmEntity;
import cl.drsmile.alarmacalendarica.conn.AlarmRepository;
import cl.drsmile.alarmacalendarica.conn.ApiRepository;
import cl.drsmile.alarmacalendarica.conn.LocalRepository;
import cl.drsmile.alarmacalendarica.scheduler.AlarmScheduler;
import cl.drsmile.alarmacalendarica.R;
import android.content.pm.PackageManager;
import androidx.preference.PreferenceManager;

public class AlarmListActivity extends AppCompatActivity {
    private static final String TAG = "AlarmListActivity";
    private RecyclerView rv;
    private AlarmAdapter adapter;
    private AlarmRepository repo;
    private TextView tvEmpty;
    private ProgressBar pbSync;

    private final ActivityResultLauncher<Intent> countrySelectionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null) {
                        String country = data.getStringExtra("selected_country");
                        if (country != null) {
                            fetchHolidaysFor(country);
                            loadAlarms();
                        }
                    }
                }
            });

    // request permission launcher for POST_NOTIFICATIONS (Android 13+)
    private final ActivityResultLauncher<String> requestNotificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    // optional: show a short explanation toast
                    runOnUiThread(() -> Toast.makeText(AlarmListActivity.this, "Permiso de notificaciones no concedido; algunas notificaciones pueden no mostrarse.", Toast.LENGTH_LONG).show());
                } else {
                    // update notification once permission is granted
                    cl.drsmile.alarmacalendarica.service.AlarmNotificationManager.updateNextAlarmNotification(AlarmListActivity.this);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm_list);

        // Apply window insets to root content so navigation bar doesn't overlap bottom UI (RecyclerView / FAB)
        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int navBottom = sys.bottom;
            int navLeft = sys.left;
            int navRight = sys.right;
            int sysTop = sys.top;
            float density = getResources().getDisplayMetrics().density;
            int extra = (int) (8 * density + 0.5f);

            // adjust RecyclerView padding: use baseline paddings stored in tag to avoid accumulation
            View rvView = findViewById(R.id.rv_alarms);
            if (rvView != null) {
                int[] base = (int[]) rvView.getTag(R.id.rv_alarms);
                if (base == null) {
                    base = new int[]{rvView.getPaddingLeft(), rvView.getPaddingTop(), rvView.getPaddingRight(), rvView.getPaddingBottom()};
                    rvView.setTag(R.id.rv_alarms, base);
                }
                rvView.setPadding(base[0] + navLeft + extra, base[1], base[2] + navRight + extra, base[3] + navBottom + extra);
            }

            // adjust FAB bottom/right margin so it's above nav bar
            FloatingActionButton fab = findViewById(R.id.fab_add_alarm);
            if (fab != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) fab.getLayoutParams();
                int[] base = (int[]) fab.getTag(R.id.fab_add_alarm);
                if (base == null) {
                    base = new int[]{lp.leftMargin, lp.topMargin, lp.rightMargin, lp.bottomMargin};
                    fab.setTag(R.id.fab_add_alarm, base);
                }
                lp.setMargins(base[0], base[1], base[2] + navRight + extra, base[3] + navBottom + extra);
                fab.setLayoutParams(lp);
            }

            // adjust toolbar top padding so title/overflow are below status bar (use baseline)
            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                int[] base = (int[]) toolbar.getTag(R.id.toolbar);
                if (base == null) {
                    base = new int[]{toolbar.getPaddingLeft(), toolbar.getPaddingTop(), toolbar.getPaddingRight(), toolbar.getPaddingBottom()};
                    toolbar.setTag(R.id.toolbar, base);
                }
                toolbar.setPadding(base[0], base[1] + sysTop, base[2], base[3]);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        // locate pb_sync by id name to avoid lint/resource mismatch warnings
        int pbId = getResources().getIdentifier("pb_sync", "id", getPackageName());
        if (pbId != 0) this.pbSync = findViewById(pbId);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        // ensure toolbar is inset below status bar so overflow/menu isn't overlapped
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            int top = insets.getInsets(Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        // no fallback via status_bar_height to avoid resource-reflection lint warnings; rely on window insets
        // do not set popup theme here (use app theme); toolbar insets applied above
        try {
            setSupportActionBar(toolbar);
        } catch (IllegalStateException ise) {
            // If the theme already provides an action bar (window decor), fall back to using the
            // toolbar as a normal view to avoid crashing. We still set the title so UI remains ok.
            Log.w(TAG, "Could not set support action bar - using toolbar as plain view", ise);
            toolbar.setTitle(getString(R.string.app_name));
        }
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                countrySelectionLauncher.launch(new Intent(AlarmListActivity.this, CountrySelectionActivity.class));
                return true;
            }
            if (item.getItemId() == R.id.action_sync) {
                // manual sync trigger: fetch holidays for current country
                String country = androidx.preference.PreferenceManager.getDefaultSharedPreferences(AlarmListActivity.this).getString("pref_country", null);
                if (country != null) {
                    fetchHolidaysFor(country);
                    Toast.makeText(AlarmListActivity.this, "Sincronizando feriados...", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AlarmListActivity.this, "Selecciona un país primero.", Toast.LENGTH_SHORT).show();
                    countrySelectionLauncher.launch(new Intent(AlarmListActivity.this, CountrySelectionActivity.class));
                }
                return true;
            }
            if (item.getItemId() == R.id.action_stop_current) {
                // stop currently ringing alarm
                cl.drsmile.alarmacalendarica.service.AlarmNotificationManager.stopCurrentAlarm(AlarmListActivity.this);
                return true;
            }
            return false;
        });

        // initialize repository early so lifecycle methods can safely use it
        repo = new AlarmRepository(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String country = prefs.getString("pref_country", null);
        if (country == null) {
            // launch selection and skip loading alarms until user picks a country
            countrySelectionLauncher.launch(new Intent(this, CountrySelectionActivity.class));
        } else {
            fetchHolidaysFor(country);
        }

        // handle system intents to set/show alarms
        Intent in = getIntent();
        if (in != null) {
            String action = in.getAction();
            if ("android.intent.action.SET_ALARM".equals(action)) {
                // extract hour/minutes/message/days and launch AlarmEditActivity prefilled
                int hour = in.getIntExtra("android.intent.extra.alarm.HOUR", -1);
                int minutes = in.getIntExtra("android.intent.extra.alarm.MINUTES", 0);
                String message = in.getStringExtra("android.intent.extra.ALARM_MESSAGE");
                Intent edit = new Intent(this, AlarmEditActivity.class);
                // we will prefill by passing extras; AlarmEditActivity currently expects id or default values, so
                // we pass extras and AlarmEditActivity will pick them up if present (we will add handling)
                edit.putExtra("pref_hour", hour);
                edit.putExtra("pref_minute", minutes);
                if (message != null) edit.putExtra("pref_label", message);
                startActivity(edit);
            } else if ("android.intent.action.SHOW_ALARMS".equals(action)) {
                // do nothing — we are the alarms list
            }
        }

        checkExactAlarmsPermission();
        requestNotificationsPermissionIfNeeded();

        Log.d(TAG, "onCreate");

        rv = findViewById(R.id.rv_alarms);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AlarmAdapter(new ArrayList<>());
        rv.setAdapter(adapter);

        tvEmpty = findViewById(R.id.tv_empty);
        // ensure placeholder shown immediately until data loads
        rv.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.VISIBLE);

        // use string resource
        tvEmpty.setText(getString(R.string.empty_alarms));
        tvEmpty.bringToFront();

        FloatingActionButton fab = findViewById(R.id.fab_add_alarm);
        fab.setOnClickListener(v -> startActivity(new Intent(this, AlarmEditActivity.class)));

        loadAlarms();
    }

    private void loadAlarms() {
        repo.getAll(list -> runOnUiThread(() -> {
            Log.d(TAG, "loaded alarms: " + (list==null?0:list.size()));
            adapter.setItems(list);
            if (list == null || list.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                rv.setVisibility(View.GONE);
            } else {
                tvEmpty.setVisibility(View.GONE);
                rv.setVisibility(View.VISIBLE);
            }
        }));
    }

    class AlarmAdapter extends RecyclerView.Adapter<AlarmAdapter.VH> {
        private List<AlarmEntity> items;

        AlarmAdapter(List<AlarmEntity> items) { this.items = items; }

        void setItems(List<AlarmEntity> newItems) { this.items = newItems; notifyDataSetChanged(); }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alarm, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            AlarmEntity a = items.get(position);
            int hour = a.hour; int minute = a.minute;
            holder.tvTime.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute));
            holder.tvLabel.setText(a.label == null ? "" : a.label);
            holder.tvWorkday.setVisibility(a.onlyWorkdays ? View.VISIBLE : View.GONE);
            // compute and show a short preview of next few occurrences
            try {
                StringBuilder sb = new StringBuilder();
                long next = System.currentTimeMillis();
                int show = 3;
                for (int i = 0; i < show; i++) {
                    // use the full scheduler that respects holidays so the UI preview matches scheduled alarms
                    next = cl.drsmile.alarmacalendarica.scheduler.AlarmScheduler.computeNextTriggerFrom(AlarmListActivity.this, a, next);
                    java.text.DateFormat df = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT);
                    if (i > 0) sb.append(" · ");
                    sb.append(df.format(new java.util.Date(next)));
                    // move 1 ms after this next to find next occurrence
                    next += 1;
                }
                holder.tvPreview.setText(sb.toString());
                holder.tvPreview.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                holder.tvPreview.setVisibility(View.GONE);
            }
            holder.switchEnabled.setChecked(a.enabled);
            holder.switchEnabled.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
                a.enabled = isChecked;
                repo.update(a);
                if (isChecked) AlarmScheduler.schedule(AlarmListActivity.this, a);
                else AlarmScheduler.cancel(AlarmListActivity.this, a);
            });

            holder.itemView.setOnClickListener(v -> {
                Intent i = new Intent(AlarmListActivity.this, AlarmEditActivity.class);
                i.putExtra("alarm_id", a.id);
                startActivity(i);
            });
            // tap time or preview to open calendar view
            holder.tvTime.setOnClickListener(v -> {
                Intent i = new Intent(AlarmListActivity.this, AlarmCalendarActivity.class);
                i.putExtra(AlarmCalendarActivity.EXTRA_ALARM_ID, a.id);
                startActivity(i);
            });
        }

        @Override
        public int getItemCount() { return items == null ? 0 : items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTime, tvLabel;
            SwitchCompat switchEnabled;
            TextView tvWorkday;
            TextView tvPreview;
            VH(View itemView) {
                super(itemView);
                tvTime = itemView.findViewById(R.id.tv_time);
                tvLabel = itemView.findViewById(R.id.tv_label);
                tvWorkday = itemView.findViewById(R.id.tv_workday);
                tvPreview = itemView.findViewById(R.id.tv_preview);
                switchEnabled = itemView.findViewById(R.id.switch_enabled);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAlarms();
    }

    private void fetchHolidaysFor(String country) {
        int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        runOnUiThread(() -> { if (pbSync != null) pbSync.setVisibility(View.VISIBLE); });
        ApiRepository api = new ApiRepository();
        api.fetchHolidaysResolve(year, country, new retrofit2.Callback<java.util.List<cl.drsmile.alarmacalendarica.dto.HolidayDTO>>() {
            @Override
            public void onResponse(retrofit2.Call<java.util.List<cl.drsmile.alarmacalendarica.dto.HolidayDTO>> call, retrofit2.Response<java.util.List<cl.drsmile.alarmacalendarica.dto.HolidayDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    new LocalRepository(AlarmListActivity.this).saveHolidays(response.body());
                }
                runOnUiThread(() -> { if (pbSync != null) pbSync.setVisibility(View.GONE); Toast.makeText(AlarmListActivity.this, "Sincronización completada", Toast.LENGTH_SHORT).show(); });
            }

            @Override
            public void onFailure(retrofit2.Call<java.util.List<cl.drsmile.alarmacalendarica.dto.HolidayDTO>> call, Throwable t) {
                runOnUiThread(() -> { if (pbSync != null) pbSync.setVisibility(View.GONE); Toast.makeText(AlarmListActivity.this, "Error al descargar feriados", Toast.LENGTH_SHORT).show(); });
            }
        });
    }

    private void checkExactAlarmsPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                // show a dialog to user to open settings for exact alarms
                runOnUiThread(() -> {
                    androidx.appcompat.app.AlertDialog.Builder b = new androidx.appcompat.app.AlertDialog.Builder(AlarmListActivity.this);
                    b.setTitle("Permiso para alarmas exactas");
                    b.setMessage("Para que las alarmas suenen exactamente cuando deben, permite alarmas exactas en la configuración de la app.");
                    b.setPositiveButton("Abrir ajustes", (d, w) -> {
                        try {
                            // ACTION_REQUEST_SCHEDULE_EXACT_ALARMS may not be available at compile time on older SDKs,
                            // use the action string directly to avoid the missing-symbol error.
                            Intent i = new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARMS");
                            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(i);
                        } catch (Exception ex) {
                            // fallback: open app settings
                            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            i.setData(android.net.Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        }
                    });
                    b.setNegativeButton("Cancelar", null);
                    b.show();
                });
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_alarm_list, menu);
        // add a Manual Sync item programmatically (keeps XML simple)
        menu.add(0, R.id.action_sync, 0, "Sincronizar").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, R.id.action_stop_current, 1, "Detener alarma").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            countrySelectionLauncher.launch(new Intent(this, CountrySelectionActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void requestNotificationsPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // ask the user
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }
}

