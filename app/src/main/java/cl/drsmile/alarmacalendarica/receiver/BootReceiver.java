package cl.drsmile.alarmacalendarica.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.List;

import cl.drsmile.alarmacalendarica.conn.AlarmRepository;
import cl.drsmile.alarmacalendarica.db.AlarmEntity;
import cl.drsmile.alarmacalendarica.scheduler.AlarmScheduler;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            Log.i(TAG, "Boot completed - rescheduling alarms");
            AlarmRepository repo = new AlarmRepository(context);
            repo.getAll(list -> {
                if (list == null) return;
                for (AlarmEntity a : list) {
                    if (a.enabled) {
                        AlarmScheduler.schedule(context, a);
                    }
                }
            });
        }
    }
}

