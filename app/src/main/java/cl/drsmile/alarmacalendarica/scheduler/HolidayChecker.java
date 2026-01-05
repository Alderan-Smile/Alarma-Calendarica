package cl.drsmile.alarmacalendarica.scheduler;

import android.content.Context;
import androidx.preference.PreferenceManager;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cl.drsmile.alarmacalendarica.db.AppDatabase;
import cl.drsmile.alarmacalendarica.db.HolidayEntity;

public class HolidayChecker {
    private final Context context;
    // cache key: year+"_"+country -> set of yyyy-MM-dd strings
    private final Map<String, Set<String>> cache = new HashMap<>();

    public HolidayChecker(Context context) {
        this.context = context.getApplicationContext();
    }

    private String keyFor(int year, String country) {
        return year + "_" + (country == null ? "" : country);
    }

    private String dateKey(Calendar c) {
        int y = c.get(Calendar.YEAR);
        int m = c.get(Calendar.MONTH) + 1; // month 0-based
        int d = c.get(Calendar.DAY_OF_MONTH);
        return String.format("%04d-%02d-%02d", y, m, d);
    }

    // allow loading only for current year and next year to avoid memory / IO overhead
    private boolean isYearAllowed(int year) {
        int now = Calendar.getInstance().get(Calendar.YEAR);
        return year == now || year == (now + 1);
    }

    private void ensureLoadedAsync(int year, String country) {
        // do not attempt to load years outside the allowed window
        if (!isYearAllowed(year)) return;

        String k = keyFor(year, country);
        synchronized (cache) {
            // prune any cached years outside the allowed window to save memory
            java.util.Iterator<String> it = cache.keySet().iterator();
            while (it.hasNext()) {
                String existingKey = it.next();
                try {
                    String[] parts = existingKey.split("_");
                    int existingYear = Integer.parseInt(parts[0]);
                    if (!isYearAllowed(existingYear)) {
                        it.remove();
                    }
                } catch (Exception ignored) { }
            }

            if (cache.containsKey(k)) return; // already loaded or loading
            cache.put(k, new HashSet<>()); // placeholder to mark loading
        }
        new Thread(() -> {
            try {
                List<HolidayEntity> list = AppDatabase.getInstance(context).holidayDao().getByCountryAndYear(country, year);
                Set<String> set = new HashSet<>();
                if (list != null) {
                    for (HolidayEntity h : list) {
                        if (h.date != null) {
                            Calendar hc = Calendar.getInstance();
                            hc.setTime(h.date);
                            set.add(dateKey(hc));
                        }
                    }
                }
                synchronized (cache) {
                    cache.put(k, set);
                }
            } catch (Exception ignored) {
                synchronized (cache) {
                    cache.remove(k);
                }
            }
        }).start();
    }

    public boolean isHoliday(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        int year = c.get(Calendar.YEAR);
        String countryCode = PreferenceManager.getDefaultSharedPreferences(context).getString("pref_country", null);
        if (countryCode == null) return false;

        // Only consider holidays for current and next year
        if (!isYearAllowed(year)) return false;

        String k = keyFor(year, countryCode);
        synchronized (cache) {
            if (!cache.containsKey(k)) {
                // trigger async load and return false for now to avoid blocking main thread
                ensureLoadedAsync(year, countryCode);
                return false;
            }
            Set<String> set = cache.get(k);
            if (set == null || set.isEmpty()) return false;
            return set.contains(dateKey(c));
        }
    }
}
