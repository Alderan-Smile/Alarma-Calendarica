package cl.drsmile.alarmacalendarica.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import cl.drsmile.alarmacalendarica.scheduler.AlarmScheduler;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        long alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1);
        Log.i(TAG, "onReceive alarmId=" + alarmId);

        Intent svc = new Intent(context, cl.drsmile.alarmacalendarica.service.AlarmService.class);
        svc.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start AlarmService", e);
        }
    }
}
