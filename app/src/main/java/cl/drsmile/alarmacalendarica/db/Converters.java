package cl.drsmile.alarmacalendarica.db;

import androidx.room.TypeConverter;
import java.util.Date;

public class Converters {
    @TypeConverter
    public static Long fromDate(Date date) {
        return date == null ? null : date.getTime();
    }

    @TypeConverter
    public static Date toDate(Long ts) {
        return ts == null ? null : new Date(ts);
    }
}