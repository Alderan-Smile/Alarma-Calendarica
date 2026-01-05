package cl.drsmile.alarmacalendarica.conn;

import android.content.Context;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import cl.drsmile.alarmacalendarica.dto.CountryDTO;
import cl.drsmile.alarmacalendarica.dto.HolidayDTO;
import cl.drsmile.alarmacalendarica.db.AppDatabase;
import cl.drsmile.alarmacalendarica.db.CountryEntity;
import cl.drsmile.alarmacalendarica.db.HolidayEntity;

public class LocalRepository {
    private final AppDatabase db;
    private final Executor io = Executors.newSingleThreadExecutor();
    private final Context context;

    public LocalRepository(Context context) {
        db = AppDatabase.getInstance(context);
        this.context = context;
    }

    public void saveCountries(final List<CountryDTO> dtos) {
        io.execute(() -> {
            List<CountryEntity> entities = new ArrayList<>();
            for (CountryDTO d : dtos) {
                entities.add(new CountryEntity(d.getCountryCode(), d.getName()));
            }
            db.countryDao().insertAll(entities);
        });
    }

    public void saveHolidays(final List<HolidayDTO> dtos) {
        io.execute(() -> {
            List<HolidayEntity> entities = new ArrayList<>();
            String countryForBatch = null;
            Integer yearForBatch = null;
            for (HolidayDTO h : dtos) {
                HolidayEntity he = new HolidayEntity();
                he.date = h.getDate();
                he.localName = h.getLocalName();
                he.name = h.getName();
                he.countryCode = h.getCountryCode();
                he.isCustom = false;
                // Ensure year is set: if DTO lacks it, derive from the date
                if (h.getYear() != null) he.year = h.getYear();
                else if (h.getDate() != null) {
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    c.setTime(h.getDate());
                    he.year = c.get(java.util.Calendar.YEAR);
                } else he.year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
                if (countryForBatch == null) countryForBatch = he.countryCode;
                if (yearForBatch == null) yearForBatch = he.year;
                entities.add(he);
            }
            // remove existing holidays for this country/year to keep DB consistent
            try {
                if (countryForBatch != null && yearForBatch != null) {
                    db.holidayDao().deleteByCountryAndYear(countryForBatch, yearForBatch);
                }
            } catch (Exception ignored) {}
            db.holidayDao().insertAll(entities);
            android.util.Log.i("LocalRepository", "Saved " + entities.size() + " holidays for " + countryForBatch + "/" + yearForBatch);
            // notify app components that holidays were updated
            try {
                android.content.Intent b = new android.content.Intent("cl.drsmile.alarmacalendarica.action.HOLIDAYS_UPDATED");
                // include simple extras so listeners can react
                if (dtos != null && !dtos.isEmpty()) {
                    b.putExtra("country", dtos.get(0).getCountryCode());
                    int y = dtos.get(0).getYear() != null ? dtos.get(0).getYear() : java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
                    b.putExtra("year", y);
                    b.putExtra("count", entities.size());
                }
                context.sendBroadcast(b);
            } catch (Exception ignored) {}
            // After saving holidays, reschedule all enabled alarms so they consider the new holidays
            try {
                List<cl.drsmile.alarmacalendarica.db.AlarmEntity> alarms = db.alarmDao().getAll();
                if (alarms != null) {
                    for (cl.drsmile.alarmacalendarica.db.AlarmEntity a : alarms) {
                        if (a.enabled) {
                            cl.drsmile.alarmacalendarica.scheduler.AlarmScheduler.schedule(context, a);
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    public void getCountries(final Callback<List<CountryEntity>> callback) {
        io.execute(() -> {
            List<CountryEntity> result = db.countryDao().getAll();
            callback.onComplete(result);
        });
    }

    public void getHolidays(final String countryCode, final int year, final Callback<List<HolidayEntity>> callback) {
        io.execute(() -> {
            List<HolidayEntity> result = db.holidayDao().getByCountryAndYear(countryCode, year);
            callback.onComplete(result);
        });
    }

    // Inserta un feriado personalizado (vacaciones/días libres) para un único día
    public void insertCustomHoliday(final HolidayEntity holiday) {
        io.execute(() -> {
            try {
                List<HolidayEntity> list = new ArrayList<>();
                holiday.isCustom = true;
                list.add(holiday);
                // do NOT delete existing holidays for the year — this was removing official holidays
                db.holidayDao().insertAll(list);
                android.util.Log.i("LocalRepository", "Inserted custom holiday: " + holiday.localName + " / " + holiday.date + " ");
                // notify listeners
                try {
                    android.content.Intent b = new android.content.Intent("cl.drsmile.alarmacalendarica.action.HOLIDAYS_UPDATED");
                    b.putExtra("country", holiday.countryCode);
                    b.putExtra("year", holiday.year != null ? holiday.year : java.util.Calendar.getInstance().get(java.util.Calendar.YEAR));
                    b.putExtra("count", 1);
                    context.sendBroadcast(b);
                } catch (Exception ignored) {}
                // reschedule alarms
                try {
                    List<cl.drsmile.alarmacalendarica.db.AlarmEntity> alarms = db.alarmDao().getAll();
                    if (alarms != null) {
                        for (cl.drsmile.alarmacalendarica.db.AlarmEntity a : alarms) {
                            if (a.enabled) cl.drsmile.alarmacalendarica.scheduler.AlarmScheduler.schedule(context, a);
                        }
                    }
                } catch (Exception ignored) {}
            } catch (Exception ex) {
                android.util.Log.e("LocalRepository", "Failed to insert custom holiday", ex);
            }
        });
    }

    // Inserta un rango de feriados: genera un HolidayEntity por cada día entre start..end (inclusive)
    public void insertCustomHolidayRange(final Date start, final Date end, final String name, final String countryCode) {
        io.execute(() -> {
            try {
                List<HolidayEntity> list = new ArrayList<>();
                Calendar c = Calendar.getInstance();
                c.setTime(start);
                Calendar endCal = Calendar.getInstance();
                endCal.setTime(end);
                // normalize time portion to avoid DST issues: set to midnight
                c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
                endCal.set(Calendar.HOUR_OF_DAY, 0); endCal.set(Calendar.MINUTE, 0); endCal.set(Calendar.SECOND, 0); endCal.set(Calendar.MILLISECOND, 0);
                while (!c.after(endCal)) {
                    HolidayEntity he = new HolidayEntity();
                    he.date = c.getTime();
                    he.localName = name;
                    he.name = name;
                    he.countryCode = countryCode;
                    he.year = c.get(Calendar.YEAR);
                    he.isCustom = true;
                    list.add(he);
                    c.add(Calendar.DAY_OF_MONTH, 1);
                }
                if (!list.isEmpty()) {
                    // avoid inserting exact duplicates: fetch existing holidays in range for country
                    List<HolidayEntity> existing = db.holidayDao().getInRangeForCountry(start, end, countryCode);
                    // build a set of existing (date+localName) combos to detect exact duplicates
                    Set<String> existingKeys = new HashSet<>();
                    if (existing != null) {
                        for (HolidayEntity e : existing) {
                            String key = e.date.getTime() + "|" + (e.localName != null ? e.localName : "");
                            existingKeys.add(key);
                        }
                    }
                    List<HolidayEntity> toInsert = new ArrayList<>();
                    for (HolidayEntity h : list) {
                        String key = h.date.getTime() + "|" + (h.localName != null ? h.localName : "");
                        if (existingKeys.contains(key)) {
                            // skip exact duplicate
                            continue;
                        }
                        toInsert.add(h);
                    }
                    if (!toInsert.isEmpty()) {
                        db.holidayDao().insertAll(toInsert);
                        android.util.Log.i("LocalRepository", "Inserted " + toInsert.size() + " custom holidays for " + countryCode + " range (after dedupe)");
                        // broadcast update
                        try {
                            android.content.Intent b = new android.content.Intent("cl.drsmile.alarmacalendarica.action.HOLIDAYS_UPDATED");
                            b.putExtra("country", countryCode);
                            b.putExtra("year", list.get(0).year);
                            b.putExtra("count", toInsert.size());
                            context.sendBroadcast(b);
                        } catch (Exception ignored) {}
                        // reschedule alarms
                        try {
                            List<cl.drsmile.alarmacalendarica.db.AlarmEntity> alarms = db.alarmDao().getAll();
                            if (alarms != null) {
                                for (cl.drsmile.alarmacalendarica.db.AlarmEntity a : alarms) {
                                    if (a.enabled) cl.drsmile.alarmacalendarica.scheduler.AlarmScheduler.schedule(context, a);
                                }
                            }
                        } catch (Exception ignored) {}
                    } else {
                        android.util.Log.i("LocalRepository", "No new custom holidays to insert after dedupe for " + countryCode + " range");
                    }
                }
            } catch (Exception ex) {
                android.util.Log.e("LocalRepository", "Failed to insert custom holiday range", ex);
            }
        });
    }

    // query holidays in range (async)
    public void findHolidaysInRange(final Date start, final Date end, final Callback<List<HolidayEntity>> callback) {
        io.execute(() -> {
            List<HolidayEntity> list = db.holidayDao().getInRange(start, end);
            callback.onComplete(list);
        });
    }

    // update a holiday (used to edit name)
    public void updateHoliday(final HolidayEntity h, final Callback<Void> callback) {
        io.execute(() -> {
            try {
                db.holidayDao().update(h);
                // notify listeners
                try {
                    android.content.Intent b = new android.content.Intent("cl.drsmile.alarmacalendarica.action.HOLIDAYS_UPDATED");
                    b.putExtra("country", h.countryCode);
                    b.putExtra("year", h.year != null ? h.year : java.util.Calendar.getInstance().get(java.util.Calendar.YEAR));
                    b.putExtra("count", 1);
                    context.sendBroadcast(b);
                } catch (Exception ignored) {}
                callback.onComplete(null);
            } catch (Exception ex) {
                android.util.Log.e("LocalRepository", "Failed to update holiday", ex);
                callback.onComplete(null);
            }
        });
    }

    // delete holiday by id
    public void deleteHolidayById(final long id, final Callback<Void> callback) {
        io.execute(() -> {
            try {
                db.holidayDao().deleteById(id);
                try {
                    android.content.Intent b = new android.content.Intent("cl.drsmile.alarmacalendarica.action.HOLIDAYS_UPDATED");
                    context.sendBroadcast(b);
                } catch (Exception ignored) {}
                callback.onComplete(null);
            } catch (Exception ex) {
                android.util.Log.e("LocalRepository", "Failed to delete holiday", ex);
                callback.onComplete(null);
            }
        });
    }

    // delete custom holidays in a range for a country
    public void deleteCustomHolidaysInRange(final Date start, final Date end, final String countryCode, final Callback<Integer> callback) {
        io.execute(() -> {
            try {
                int deleted = db.holidayDao().deleteCustomInRange(start, end, countryCode);
                try {
                    android.content.Intent b = new android.content.Intent("cl.drsmile.alarmacalendarica.action.HOLIDAYS_UPDATED");
                    b.putExtra("country", countryCode);
                    context.sendBroadcast(b);
                } catch (Exception ignored) {}
                callback.onComplete(deleted);
            } catch (Exception ex) {
                android.util.Log.e("LocalRepository", "Failed to delete custom holidays in range", ex);
                callback.onComplete(0);
            }
        });
    }

    // export custom holidays to CSV (async). Returns File absolute path via callback
    public void exportCustomHolidaysToCsv(final Date from, final Date to, final String countryCode, final Callback<String> callback) {
        io.execute(() -> {
            java.io.File outFile = null;
            java.io.BufferedWriter bw = null;
            try {
                List<HolidayEntity> list = db.holidayDao().getInRangeForCountry(from != null ? from : new Date(0), to != null ? to : new Date(Long.MAX_VALUE), countryCode);
                // filter only custom
                List<HolidayEntity> custom = new ArrayList<>();
                if (list != null) {
                    for (HolidayEntity h : list) {
                        if (h.isCustom) custom.add(h);
                    }
                }
                java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd");
                String fileName = "custom_holidays_" + (countryCode != null ? countryCode : "ALL") + "_" + System.currentTimeMillis() + ".csv";
                outFile = new java.io.File(context.getCacheDir(), fileName);
                bw = new java.io.BufferedWriter(new java.io.FileWriter(outFile));
                // header
                bw.write("date,localName,name,countryCode,year,isCustom\n");
                if (custom != null) {
                    for (HolidayEntity h : custom) {
                        String dateStr = h.date != null ? fmt.format(h.date) : "";
                        String local = h.localName != null ? h.localName.replaceAll("\n"," ").replaceAll(","," ") : "";
                        String name = h.name != null ? h.name.replaceAll("\n"," ").replaceAll(","," ") : "";
                        String cc = h.countryCode != null ? h.countryCode : "";
                        String yr = h.year != null ? h.year.toString() : "";
                        String isC = h.isCustom ? "1" : "0";
                        bw.write(String.join(",", dateStr, local, name, cc, yr, isC));
                        bw.write("\n");
                    }
                }
                bw.flush();
                callback.onComplete(outFile.getAbsolutePath());
            } catch (Exception ex) {
                android.util.Log.e("LocalRepository", "Failed to export custom holidays to CSV", ex);
                if (callback != null) callback.onComplete(null);
            } finally {
                try { if (bw != null) bw.close(); } catch (Exception ignored) {}
            }
        });
    }

    public interface Callback<T> {
        void onComplete(T result);
    }
}
