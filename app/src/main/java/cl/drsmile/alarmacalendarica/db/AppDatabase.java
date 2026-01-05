package cl.drsmile.alarmacalendarica.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {CountryEntity.class, HolidayEntity.class, AlarmEntity.class}, version = 6, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    private static final String DB_NAME = "alarma_calendarica.db";
    private static volatile AppDatabase INSTANCE;

    public abstract CountryDao countryDao();
    public abstract HolidayDao holidayDao();
    public abstract AlarmDao alarmDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, DB_NAME)
                            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    // Migration 2 -> 3: add soundUri column to alarms
    static final androidx.room.migration.Migration MIGRATION_2_3 = new androidx.room.migration.Migration(2, 3) {
        @Override
        public void migrate(androidx.sqlite.db.SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE alarms ADD COLUMN soundUri TEXT");
        }
    };

    // Migration 3 -> 4: add saturdayWorkday and sundayWorkday booleans
    static final androidx.room.migration.Migration MIGRATION_3_4 = new androidx.room.migration.Migration(3, 4) {
        @Override
        public void migrate(androidx.sqlite.db.SupportSQLiteDatabase database) {
            // add boolean columns as NOT NULL with default 0 to match AlarmEntity primitive booleans
            database.execSQL("ALTER TABLE alarms ADD COLUMN saturdayWorkday INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE alarms ADD COLUMN sundayWorkday INTEGER NOT NULL DEFAULT 0");
        }
    };

    // Migration 4 -> 5: add oneShot boolean column
    static final androidx.room.migration.Migration MIGRATION_4_5 = new androidx.room.migration.Migration(4, 5) {
        @Override
        public void migrate(androidx.sqlite.db.SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE alarms ADD COLUMN oneShot INTEGER NOT NULL DEFAULT 0");
        }
    };

    // Migration 5 -> 6: add isCustom boolean column to holidays table
    static final androidx.room.migration.Migration MIGRATION_5_6 = new androidx.room.migration.Migration(5, 6) {
        @Override
        public void migrate(androidx.sqlite.db.SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE holidays ADD COLUMN isCustom INTEGER NOT NULL DEFAULT 0");
        }
    };
}