package cl.drsmile.alarmacalendarica.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "alarms")
public class AlarmEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    // time in millis for the next scheduled trigger
    public long nextTriggerAt;

    // requestCode / unique identifier for PendingIntent
    public int requestCode;

    // user-specified hour and minute
    public int hour;
    public int minute;

    // repeat pattern: bitmask for days of week (1=Sunday..7=Saturday) or 0 = one-shot
    public int daysOfWeekMask;

    // enable/disable
    public boolean enabled;

    // label for the alarm
    public String label;

    // if true, only schedule on working days (Mon-Fri) and skip public holidays for selected country
    public boolean onlyWorkdays;

    // allow treating Saturday and/or Sunday as workdays when onlyWorkdays is enabled
    public boolean saturdayWorkday;
    public boolean sundayWorkday;

    // optional ringtone URI string selected by user
    public String soundUri;

    // if true, this alarm is one-shot: after it rings and is stopped by the user it will be disabled
    public boolean oneShot;

    public AlarmEntity() {}

}
