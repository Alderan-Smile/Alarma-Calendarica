package cl.drsmile.alarmacalendarica.conn;

import android.content.Context;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import cl.drsmile.alarmacalendarica.db.AlarmDao;
import cl.drsmile.alarmacalendarica.db.AlarmEntity;
import cl.drsmile.alarmacalendarica.db.AppDatabase;

public class AlarmRepository {
    private final AppDatabase db;
    private final AlarmDao dao;
    private final Executor io = Executors.newSingleThreadExecutor();
    private final android.content.Context context;

    public AlarmRepository(Context context) {
        db = AppDatabase.getInstance(context);
        dao = db.alarmDao();
        this.context = context.getApplicationContext();
    }

    public interface Callback<T> { void onComplete(T result); }

    public void insert(final AlarmEntity alarm, final Callback<Long> callback) {
        io.execute(() -> {
            long id = dao.insert(alarm);
            // update next alarm notification after modifying DB
            cl.drsmile.alarmacalendarica.service.AlarmNotificationManager.updateNextAlarmNotification(context);
            callback.onComplete(id);
        });
    }

    public void update(final AlarmEntity alarm) {
        io.execute(() -> {
            dao.update(alarm);
            cl.drsmile.alarmacalendarica.service.AlarmNotificationManager.updateNextAlarmNotification(context);
        });
    }

    public void delete(final AlarmEntity alarm) {
        io.execute(() -> {
            dao.delete(alarm);
            cl.drsmile.alarmacalendarica.service.AlarmNotificationManager.updateNextAlarmNotification(context);
        });
    }

    public void getAll(final Callback<List<AlarmEntity>> callback) {
        io.execute(() -> {
            List<AlarmEntity> list = dao.getAll();
            callback.onComplete(list);
        });
    }

    public void getById(final long id, final Callback<AlarmEntity> callback) {
        io.execute(() -> {
            AlarmEntity a = dao.getById(id);
            callback.onComplete(a);
        });
    }
}
