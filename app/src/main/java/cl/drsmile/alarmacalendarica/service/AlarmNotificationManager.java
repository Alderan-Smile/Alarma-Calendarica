package cl.drsmile.alarmacalendarica.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.text.format.DateFormat;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import cl.drsmile.alarmacalendarica.conn.AlarmRepository;
import cl.drsmile.alarmacalendarica.db.AlarmEntity;
import cl.drsmile.alarmacalendarica.db.AppDatabase;
import cl.drsmile.alarmacalendarica.scheduler.AlarmScheduler;

public class AlarmNotificationManager {
    private static final String CHANNEL_ID_INFO = "alarm_info_channel";
    private static final int NOTIF_ID_NEXT = 100;
    private static final Executor io = Executors.newSingleThreadExecutor();

    private static void ensureChannel(Context ctx) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID_INFO, "Alarm Info", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Información sobre la próxima alarma");
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    public static void updateNextAlarmNotification(Context ctx) {
        if (ctx == null) return;
        ensureChannel(ctx);
        io.execute(() -> {
            try {
                List<AlarmEntity> alarms = AppDatabase.getInstance(ctx).alarmDao().getAll();
                long now = System.currentTimeMillis();
                long bestTime = Long.MAX_VALUE;
                AlarmEntity best = null;
                if (alarms != null) {
                    for (AlarmEntity a : alarms) {
                        if (!a.enabled) continue;
                        long next = AlarmScheduler.computeNextTriggerFrom(ctx, a, now);
                        if (next <= 0) continue;
                        if (next < bestTime) { bestTime = next; best = a; }
                    }
                }

                NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, CHANNEL_ID_INFO)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setOngoing(true)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setPriority(NotificationCompat.PRIORITY_LOW);

                if (best != null) {
                    String title = best.label == null || best.label.isEmpty() ? "Siguiente alarma" : best.label;
                    String when = DateFormat.getMediumDateFormat(ctx).format(bestTime) + " " + DateFormat.getTimeFormat(ctx).format(bestTime);
                    nb.setContentTitle(title);
                    nb.setContentText(String.format(Locale.getDefault(), "Se activará: %s", when));
                    try {
                        android.content.Intent open = new android.content.Intent(ctx, cl.drsmile.alarmacalendarica.ui.AlarmEditActivity.class);
                        open.putExtra("alarm_id", best.id);
                        open.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        int req = (int) (best.id & 0x7fffffff);
                        android.app.PendingIntent piOpen = android.app.PendingIntent.getActivity(ctx, req, open, android.app.PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT>=23?android.app.PendingIntent.FLAG_IMMUTABLE:0));
                        nb.setContentIntent(piOpen);
                    } catch (Exception ignored) {}
                } else {
                    nb.setContentTitle("Sin alarmas programadas");
                    nb.setContentText("No hay alarmas habilitadas");
                }

                try {
                    // On Android 13+ ensure we have POST_NOTIFICATIONS permission
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            // cannot post notifications without permission
                            return;
                        }
                    }
                    NotificationManagerCompat.from(ctx).notify(NOTIF_ID_NEXT, nb.build());
                } catch (SecurityException se) {
                    se.printStackTrace();
                }
            } catch (Exception e) {
                // best-effort
                e.printStackTrace();
            }
        });
    }

    public static void cancelNextAlarmNotification(Context ctx) {
        if (ctx == null) return;
        try { NotificationManagerCompat.from(ctx).cancel(NOTIF_ID_NEXT); } catch (SecurityException ignored) {}
    }

    public static void stopCurrentAlarm(Context ctx) {
        // send stop intent to AlarmService
        try {
            android.content.Intent stop = new android.content.Intent(ctx, cl.drsmile.alarmacalendarica.service.AlarmService.class);
            stop.setAction(cl.drsmile.alarmacalendarica.service.AlarmService.ACTION_STOP);
            ctx.startService(stop);
        } catch (Exception ignored) {}
    }
}
