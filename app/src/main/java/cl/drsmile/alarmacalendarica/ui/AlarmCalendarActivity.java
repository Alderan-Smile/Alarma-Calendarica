package cl.drsmile.alarmacalendarica.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.annotation.SuppressLint;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsCompat.Type;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import cl.drsmile.alarmacalendarica.R;
import cl.drsmile.alarmacalendarica.db.HolidayEntity;
import cl.drsmile.alarmacalendarica.db.AppDatabase;
import cl.drsmile.alarmacalendarica.db.AlarmEntity;
import cl.drsmile.alarmacalendarica.conn.AlarmRepository;

public class AlarmCalendarActivity extends AppCompatActivity {
    public static final String EXTRA_ALARM_ID = "alarm_id";

    private RecyclerView rv;
    private TextView tvMonth;
    private AlarmEntity alarm;

    private Calendar current;
    private List<HolidayEntity> holidays = new ArrayList<>();
    private android.content.BroadcastReceiver holidayUpdateReceiver;
    private ActivityResultLauncher<android.content.Intent> customHolidayLauncher;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm_calendar);

        // Ensure the root content view accounts for system bar insets so UI isn't overlapped in any orientation
        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int navBottom = sys.bottom;
            int navLeft = sys.left;
            int navRight = sys.right;
            int sysTop = sys.top;
            float density = getResources().getDisplayMetrics().density;
            int extra = (int) (6 * density + 0.5f);

            // adjust RecyclerView padding baseline
            View rvView = findViewById(R.id.rv_calendar);
            if (rvView != null) {
                int[] base = (int[]) rvView.getTag(R.id.rv_calendar);
                if (base == null) {
                    base = new int[]{rvView.getPaddingLeft(), rvView.getPaddingTop(), rvView.getPaddingRight(), rvView.getPaddingBottom()};
                    rvView.setTag(R.id.rv_calendar, base);
                }
                rvView.setPadding(base[0] + navLeft + extra, base[1], base[2] + navRight + extra, base[3] + navBottom + extra);
            }

            // adjust toolbar top padding
            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                int[] base = (int[]) toolbar.getTag(R.id.toolbar);
                if (base == null) {
                    base = new int[]{toolbar.getPaddingLeft(), toolbar.getPaddingTop(), toolbar.getPaddingRight(), toolbar.getPaddingBottom()};
                    toolbar.setTag(R.id.toolbar, base);
                }
                toolbar.setPadding(base[0], base[1] + sysTop, base[2], base[3]);
            }

            // adjust FloatingActionButton margin so it sits above navigation bar
            View fab = findViewById(R.id.btn_add_custom_holiday);
            if (fab != null) {
                // store base margin in tag
                int[] base = (int[]) fab.getTag(R.id.btn_add_custom_holiday);
                if (base == null) {
                    // left, top, right, bottom margins baseline (we'll only adjust bottom)
                    if (fab.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) fab.getLayoutParams();
                        base = new int[]{lp.leftMargin, lp.topMargin, lp.rightMargin, lp.bottomMargin};
                    } else base = new int[]{0,0,0,0};
                    fab.setTag(R.id.btn_add_custom_holiday, base);
                }
                if (fab.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) fab.getLayoutParams();
                    lp.leftMargin = base[0] + navRight + extra; // nudge away from right inset
                    lp.rightMargin = base[2] + navRight + extra;
                    lp.bottomMargin = base[3] + navBottom + extra;
                    fab.setLayoutParams(lp);
                } else {
                    // fallback: adjust padding
                    fab.setPadding(fab.getPaddingLeft(), fab.getPaddingTop(), fab.getPaddingRight() + navRight + extra, fab.getPaddingBottom() + navBottom + extra);
                }
            }

            return insets;
        });
        ViewCompat.requestApplyInsets(content);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        // ensure toolbar is positioned below the status bar to avoid overlap with navigation icon
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insetsx) -> {
            int top = insetsx.getInsets(Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
            return insetsx;
        });
        setSupportActionBar(toolbar);
        // show back/home icon to return to alarm list
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert);
        toolbar.setNavigationOnClickListener(v -> {
            // navigate back to AlarmListActivity
            android.content.Intent i = new android.content.Intent(AlarmCalendarActivity.this, cl.drsmile.alarmacalendarica.ui.AlarmListActivity.class);
            i.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });

        // populate weekday headers (Mon..Sun localized short names)
        String[] shortWeekdays = new DateFormatSymbols().getShortWeekdays();
        // shortWeekdays starts at index 1=Sunday; map to our header views (starting Sunday)
        int[] hdrIds = new int[]{R.id.hdr_day_0,R.id.hdr_day_1,R.id.hdr_day_2,R.id.hdr_day_3,R.id.hdr_day_4,R.id.hdr_day_5,R.id.hdr_day_6};
        for (int i = 0; i < 7; i++) {
            TextView tv = findViewById(hdrIds[i]);
            tv.setText(shortWeekdays[i+1]);
        }

        tvMonth = findViewById(R.id.tv_month);
        rv = findViewById(R.id.rv_calendar);
        rv.setLayoutManager(new GridLayoutManager(this, 7));

        long alarmId = getIntent().getLongExtra(EXTRA_ALARM_ID, -1);
        if (alarmId != -1) {
            new AlarmRepository(this).getById(alarmId, a -> {
                this.alarm = a;
                runOnUiThread(() -> refresh());
            });
        }

        // load holidays for the current year/country from DB
        current = Calendar.getInstance();
        loadHolidaysForYear(current.get(Calendar.YEAR));

        // register receiver to refresh calendar when holidays are updated
        holidayUpdateReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, android.content.Intent intent) {
                String action = intent == null ? null : intent.getAction();
                if ("cl.drsmile.alarmacalendarica.action.HOLIDAYS_UPDATED".equals(action)) {
                    int y = intent.getIntExtra("year", current.get(Calendar.YEAR));
                    // if current displayed year matches notification year, reload
                    if (y == current.get(Calendar.YEAR)) loadHolidaysForYear(y);
                    // show short feedback if count included
                    int count = intent.getIntExtra("count", -1);
                    if (count >= 0) android.util.Log.i("AlarmCalendarActivity", "Received HOLIDAYS_UPDATED count=" + count);
                }
            }
        };
        // Register receiver: on Android 13+ we must specify exported/not-exported flag when registering non-system broadcasts
        android.content.IntentFilter filter = new android.content.IntentFilter("cl.drsmile.alarmacalendarica.action.HOLIDAYS_UPDATED");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // explicitly not exported
            registerReceiver(holidayUpdateReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(holidayUpdateReceiver, filter);
        }

        findViewById(R.id.btn_prev).setOnClickListener(v -> { current.add(Calendar.MONTH, -1); refresh(); });
        findViewById(R.id.btn_next).setOnClickListener(v -> { current.add(Calendar.MONTH, 1); refresh(); });

        // register ActivityResult launcher to handle result from CustomHolidayActivity
        customHolidayLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                // refresh holidays for the current year
                loadHolidaysForYear(current.get(Calendar.YEAR));
            }
        });

        View fab = findViewById(R.id.btn_add_custom_holiday);
        if (fab != null) {
            fab.setOnClickListener(v -> {
                android.content.Intent i = new android.content.Intent(AlarmCalendarActivity.this, CustomHolidayActivity.class);
                customHolidayLauncher.launch(i);
            });
        }
    }

    private void loadHolidaysForYear(int year) {
        new Thread(() -> {
            // using AppDatabase directly to fetch cached holidays
            String country = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this).getString("pref_country", null);
            if (country == null) return;
            holidays = AppDatabase.getInstance(this).holidayDao().getByCountryAndYear(country, year);
            android.util.Log.i("AlarmCalendarActivity", "Loaded " + (holidays==null?0:holidays.size()) + " holidays for " + country + "/" + year);
            if (holidays != null && !holidays.isEmpty()) {
                for (int i = 0; i < Math.min(5, holidays.size()); i++) {
                    android.util.Log.i("AlarmCalendarActivity", " Holiday sample: " + holidays.get(i).localName + " / " + holidays.get(i).date);
                }
            }
            runOnUiThread(this::refresh);
        }).start();
    }

    private void refresh() {
        runOnUiThread(() -> {
            tvMonth.setText(new DateFormatSymbols().getMonths()[current.get(Calendar.MONTH)] + " " + current.get(Calendar.YEAR));
            List<Date> days = generateDaysForMonth(current);
            rv.setAdapter(new Adapter(days));
        });
    }

    private List<Date> generateDaysForMonth(Calendar cal) {
        List<Date> days = new ArrayList<>();
        Calendar c = (Calendar) cal.clone();
        c.set(Calendar.DAY_OF_MONTH, 1);
        int firstDow = c.get(Calendar.DAY_OF_WEEK);
        // backfill previous month days to align
        int pad = firstDow - Calendar.SUNDAY; // Sunday=1
        c.add(Calendar.DAY_OF_MONTH, -pad);
        while (days.size() < 42) { // 6 weeks
            days.add(c.getTime());
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return days;
    }

    class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<Date> days;
        Adapter(List<Date> days) { this.days = days; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_calendar_day, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Date d = days.get(position);
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            holder.tv.setText(String.valueOf(c.get(Calendar.DAY_OF_MONTH)));
            holder.tvHoliday.setVisibility(View.GONE);

            boolean sameMonth = c.get(Calendar.MONTH) == current.get(Calendar.MONTH);
            holder.tv.setAlpha(sameMonth ? 1f : 0.35f);

            // determine flags: weekend, holiday, active day per alarm
            boolean weekend = (c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY);
            boolean isHoliday = false;
            for (HolidayEntity h : holidays) {
                Calendar hc = Calendar.getInstance();
                hc.setTime(h.date);
                if (hc.get(Calendar.YEAR) == c.get(Calendar.YEAR) && hc.get(Calendar.MONTH) == c.get(Calendar.MONTH) && hc.get(Calendar.DAY_OF_MONTH) == c.get(Calendar.DAY_OF_MONTH)) {
                    isHoliday = true; break;
                }
            }

            boolean active = false;
            if (alarm != null) {
                if (alarm.daysOfWeekMask == 0) {
                    // one-shot: active if equals alarm date
                    Calendar ac = Calendar.getInstance(); ac.setTimeInMillis(alarm.nextTriggerAt);
                    active = (ac.get(Calendar.YEAR)==c.get(Calendar.YEAR) && ac.get(Calendar.MONTH)==c.get(Calendar.MONTH) && ac.get(Calendar.DAY_OF_MONTH)==c.get(Calendar.DAY_OF_MONTH));
                } else {
                    int dow = c.get(Calendar.DAY_OF_WEEK);
                    int maskBit = 1 << (dow - 1);
                    if ((alarm.daysOfWeekMask & maskBit) != 0) {
                        if (alarm.onlyWorkdays) {
                            active = !(dow==Calendar.SATURDAY || dow==Calendar.SUNDAY) && !isHoliday;
                        } else active = true;
                    }
                }
            }

            // apply coloring
            if (isHoliday) {
                // find holiday entity for this day to check if custom
                HolidayEntity found = null;
                for (HolidayEntity h : holidays) {
                    Calendar hc = Calendar.getInstance();
                    hc.setTime(h.date);
                    if (hc.get(Calendar.YEAR) == c.get(Calendar.YEAR) && hc.get(Calendar.MONTH) == c.get(Calendar.MONTH) && hc.get(Calendar.DAY_OF_MONTH) == c.get(Calendar.DAY_OF_MONTH)) {
                        found = h; break;
                    }
                }
                if (found != null && found.isCustom) {
                    // custom holidays: use a blue background
                    holder.tv.setTextColor(getColor(android.R.color.white));
                    holder.tv.setBackgroundColor(getColor(android.R.color.holo_blue_dark));
                } else {
                    // official holiday
                    holder.tv.setTextColor(getColor(android.R.color.white));
                    holder.tv.setBackgroundColor(getColor(android.R.color.holo_red_dark));
                }
            } else if (weekend) holder.tv.setTextColor(getColor(android.R.color.darker_gray));
            else holder.tv.setTextColor(getColor(android.R.color.black));

            // if not holiday, mark selected to show alarm active (selector will highlight)
            if (!isHoliday) {
                holder.tv.setSelected(active);
            } else {
                // if holiday and also active, show a subtle indicator (keep colored background)
                holder.tv.setSelected(false);
            }

            // if holiday, show its short name under the number and make clickable for details
            if (isHoliday) {
                for (HolidayEntity h : holidays) {
                    Calendar hc = Calendar.getInstance();
                    hc.setTime(h.date);
                    if (hc.get(Calendar.YEAR) == c.get(Calendar.YEAR) && hc.get(Calendar.MONTH) == c.get(Calendar.MONTH) && hc.get(Calendar.DAY_OF_MONTH) == c.get(Calendar.DAY_OF_MONTH)) {
                        String label = h.localName != null && !h.localName.isEmpty() ? h.localName : h.name;
                        // shorten label if too long
                        if (label != null && label.length() > 12) label = label.substring(0, 12) + "..";
                        holder.tvHoliday.setText(label);
                        holder.tvHoliday.setVisibility(View.VISIBLE);
                        View dot = holder.itemView.findViewById(R.id.v_holiday_dot);
                        if (dot != null) dot.setVisibility(View.VISIBLE);
                        holder.itemView.setOnClickListener(v -> showHolidayDialog(h));
                        break;
                    }
                }
            } else {
                holder.itemView.setOnClickListener(null);
                View dot = holder.itemView.findViewById(R.id.v_holiday_dot);
                if (dot != null) dot.setVisibility(View.GONE);
            }

        }

        @Override
        public int getItemCount() { return days.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tv;
            TextView tvHoliday;
            VH(@NonNull View itemView) {
                super(itemView);
                tv = itemView.findViewById(R.id.tv_day);
                tvHoliday = itemView.findViewById(R.id.tv_holiday);
            }
        }
    }

    private void showHolidayDialog(HolidayEntity h) {
        androidx.appcompat.app.AlertDialog.Builder b = new androidx.appcompat.app.AlertDialog.Builder(this);
        String title = h.localName != null && !h.localName.isEmpty() ? h.localName : h.name;
        b.setTitle(title);
        StringBuilder msg = new StringBuilder();
        msg.append("Nombre: ").append(h.name == null ? "-" : h.name).append('\n');
        msg.append("Local: ").append(h.localName == null ? "-" : h.localName).append('\n');
        msg.append("Fecha: ").append(java.text.DateFormat.getDateInstance().format(h.date)).append('\n');
        b.setMessage(msg.toString());

        if (h.isCustom) {
            b.setPositiveButton("Editar", (d, w) -> {
                // show edit dialog with an EditText to change name
                EditText input = new EditText(this);
                input.setText(h.localName != null ? h.localName : "");
                new AlertDialog.Builder(this)
                        .setTitle("Editar feriado")
                        .setView(input)
                        .setPositiveButton("Guardar", (dd, ww) -> {
                            String newName = input.getText().toString().trim();
                            if (!newName.isEmpty()) {
                                h.localName = newName; h.name = newName;
                                new cl.drsmile.alarmacalendarica.conn.LocalRepository(this).updateHoliday(h, res -> runOnUiThread(this::refresh));
                            }
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
            b.setNeutralButton("Eliminar", (d, w) -> {
                new AlertDialog.Builder(this)
                        .setTitle("Confirmar eliminación")
                        .setMessage("Eliminar este feriado personalizado?")
                        .setPositiveButton("Eliminar", (dd, ww) -> new cl.drsmile.alarmacalendarica.conn.LocalRepository(this).deleteHolidayById(h.id, res -> runOnUiThread(this::refresh)))
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
            b.setNegativeButton("Cerrar", null);
        } else {
            b.setPositiveButton("Cerrar", null);
        }
        b.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { if (holidayUpdateReceiver != null) unregisterReceiver(holidayUpdateReceiver); } catch (Exception ignored) {}
    }
}
