package cl.drsmile.alarmacalendarica.scheduler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

import cl.drsmile.alarmacalendarica.db.AlarmEntity;
import cl.drsmile.alarmacalendarica.conn.AlarmRepository;

public class AlarmScheduler {

    public static final String EXTRA_ALARM_ID = "alarm_id";

    public static void schedule(Context context, AlarmEntity alarm) {
        // Run scheduling off the main thread to avoid Room main-thread DB access errors
        new Thread(() -> {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent();
            intent.setClassName(context.getPackageName(), "cl.drsmile.alarmacalendarica.receiver.AlarmReceiver");
            intent.putExtra(EXTRA_ALARM_ID, alarm.id);
            PendingIntent pi = PendingIntent.getBroadcast(context, alarm.requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));

            long triggerAt = computeNextTrigger(context, alarm);
            alarm.nextTriggerAt = triggerAt;

            // If holidays for year+country are missing, trigger an async fetch for them so future scheduling considers holidays
            try {
                String country = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).getString("pref_country", null);
                int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
                if (country != null) {
                    java.util.List<cl.drsmile.alarmacalendarica.db.HolidayEntity> existing = cl.drsmile.alarmacalendarica.db.AppDatabase.getInstance(context).holidayDao().getByCountryAndYear(country, year);
                    if (existing == null || existing.isEmpty()) {
                        // fetch and save asynchronously
                        cl.drsmile.alarmacalendarica.conn.ApiRepository api = new cl.drsmile.alarmacalendarica.conn.ApiRepository();
                        api.fetchHolidays(year, country, new retrofit2.Callback<java.util.List<cl.drsmile.alarmacalendarica.dto.HolidayDTO>>() {
                            @Override
                            public void onResponse(retrofit2.Call<java.util.List<cl.drsmile.alarmacalendarica.dto.HolidayDTO>> call, retrofit2.Response<java.util.List<cl.drsmile.alarmacalendarica.dto.HolidayDTO>> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    new cl.drsmile.alarmacalendarica.conn.LocalRepository(context).saveHolidays(response.body());
                                }
                            }

                            @Override
                            public void onFailure(retrofit2.Call<java.util.List<cl.drsmile.alarmacalendarica.dto.HolidayDTO>> call, Throwable t) { }
                        });
                    }
                }
            } catch (Exception ignored) {}

            // For better system integration (shows alarm icon and behaves like system alarm), try setAlarmClock
            if (am != null) {
                try {
                    Intent showIntent = new Intent(context, cl.drsmile.alarmacalendarica.ui.AlarmListActivity.class);
                    PendingIntent showPending = PendingIntent.getActivity(context, (int)(alarm.requestCode ^ 0xABCDEF), showIntent, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        android.app.AlarmManager.AlarmClockInfo aci = new android.app.AlarmManager.AlarmClockInfo(triggerAt, showPending);
                        am.setAlarmClock(aci, pi);
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                        } else {
                            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                        }
                    }
                } catch (SecurityException se) {
                    // fallback
                    am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                }
            }

            new AlarmRepository(context).update(alarm);
        }).start();
    }

    public static void cancel(Context context, AlarmEntity alarm) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent();
        intent.setClassName(context.getPackageName(), "cl.drsmile.alarmacalendarica.receiver.AlarmReceiver");
        PendingIntent pi = PendingIntent.getBroadcast(context, alarm.requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        if (am != null) am.cancel(pi);
    }

    // Schedule a one-shot snooze for `minutes` minutes from now using a distinct request code
    public static void scheduleSnooze(Context context, AlarmEntity alarm, int minutes) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long triggerAt = System.currentTimeMillis() + minutes * 60_000L;
        Intent intent = new Intent();
        intent.setClassName(context.getPackageName(), "cl.drsmile.alarmacalendarica.receiver.AlarmReceiver");
        intent.putExtra(EXTRA_ALARM_ID, alarm.id);
        int snoozeRequest = alarm.requestCode ^ 0xDEADBEEF; // distinct request code for snooze
        PendingIntent pi = PendingIntent.getBroadcast(context, snoozeRequest, intent, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        if (am != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // On Android S+ check whether app may schedule exact alarms
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                    } else {
                        // fallback to non-exact scheduling
                        am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                }
            } catch (SecurityException se) {
                // fallback to non-exact set
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        }
    }

    public static long computeNextTrigger(Context context, AlarmEntity alarm) {
        return computeNextTriggerFrom(context, alarm, System.currentTimeMillis());
    }

    /**
     * Compute next trigger after a given millis (exclusive).
     */
    public static long computeNextTriggerFrom(Context context, AlarmEntity alarm, long afterMillis) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(afterMillis);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(afterMillis);
        cal.set(Calendar.HOUR_OF_DAY, alarm.hour);
        cal.set(Calendar.MINUTE, alarm.minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        HolidayChecker checker = new HolidayChecker(context);

        // if no repeat days (one-shot)
        if (alarm.daysOfWeekMask == 0) {
            if (cal.getTimeInMillis() <= now.getTimeInMillis()) {
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            if (alarm.onlyWorkdays) {
                while (!isWorkdayAndNotHoliday(cal, checker, alarm)) {
                    cal.add(Calendar.DAY_OF_MONTH, 1);
                }
            }
            return cal.getTimeInMillis();
        }

        // repeat
        for (int i = 0; i < 21; i++) {
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            int maskBit = 1 << (dayOfWeek - 1);
            if ((alarm.daysOfWeekMask & maskBit) != 0) {
                if (cal.getTimeInMillis() > now.getTimeInMillis()) {
                    if (alarm.onlyWorkdays) {
                        if (isWorkdayAndNotHoliday(cal, checker, alarm)) return cal.getTimeInMillis();
                    } else {
                        return cal.getTimeInMillis();
                    }
                }
            }
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return cal.getTimeInMillis();
    }

    /**
     * Compute next trigger ignoring holidays (only weekends considered). Safe to call on main thread for previews.
     */
    public static long computeNextTriggerIgnoringHolidays(Context context, AlarmEntity alarm, long afterMillis) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(afterMillis);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(afterMillis);
        cal.set(Calendar.HOUR_OF_DAY, alarm.hour);
        cal.set(Calendar.MINUTE, alarm.minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if (alarm.daysOfWeekMask == 0) {
            if (cal.getTimeInMillis() <= now.getTimeInMillis()) cal.add(Calendar.DAY_OF_MONTH, 1);
            if (alarm.onlyWorkdays) {
                // respect saturdayWorkday/sundayWorkday flags when skipping weekends
                while (true) {
                    int dow = cal.get(Calendar.DAY_OF_WEEK);
                    if (dow == Calendar.SATURDAY) {
                        if (alarm.saturdayWorkday) break;
                        cal.add(Calendar.DAY_OF_MONTH, 1);
                        continue;
                    }
                    if (dow == Calendar.SUNDAY) {
                        if (alarm.sundayWorkday) break;
                        cal.add(Calendar.DAY_OF_MONTH, 1);
                        continue;
                    }
                    // weekday -> ok
                    break;
                }
            }
            return cal.getTimeInMillis();
        }

        for (int i = 0; i < 21; i++) {
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            int maskBit = 1 << (dayOfWeek - 1);
            if ((alarm.daysOfWeekMask & maskBit) != 0) {
                if (cal.getTimeInMillis() > now.getTimeInMillis()) {
                    if (alarm.onlyWorkdays) {
                        boolean weekendDay = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY);
                        if (weekendDay) {
                            // if saturday/sunday allowed, consider them workdays
                            if ((dayOfWeek == Calendar.SATURDAY && alarm.saturdayWorkday) || (dayOfWeek == Calendar.SUNDAY && alarm.sundayWorkday)) {
                                return cal.getTimeInMillis();
                            }
                        } else {
                            return cal.getTimeInMillis();
                        }
                    } else {
                        return cal.getTimeInMillis();
                    }
                }
            }
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return cal.getTimeInMillis();
    }

    private static boolean isWorkdayAndNotHoliday(Calendar cal, HolidayChecker checker, AlarmEntity alarm) {
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        boolean weekend = (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY);
        if (weekend) {
            if (dow == Calendar.SATURDAY && alarm.saturdayWorkday) {
                return !checker.isHoliday(cal.getTime());
            }
            if (dow == Calendar.SUNDAY && alarm.sundayWorkday) {
                return !checker.isHoliday(cal.getTime());
            }
            return false;
        }
        return !checker.isHoliday(cal.getTime());
    }
}





