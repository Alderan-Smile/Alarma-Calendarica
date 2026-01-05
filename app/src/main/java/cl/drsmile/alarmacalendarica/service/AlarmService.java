package cl.drsmile.alarmacalendarica.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import cl.drsmile.alarmacalendarica.scheduler.AlarmScheduler;

public class AlarmService extends Service {
    private static final String TAG = "AlarmService";
    public static final String ACTION_STOP = "cl.drsmile.alarmacalendarica.action.STOP_ALARM";
    public static final String ACTION_SNOOZE = "cl.drsmile.alarmacalendarica.action.SNOOZE_ALARM";
    public static final String ACTION_PAUSE = "cl.drsmile.alarmacalendarica.action.PAUSE_ALARM";
    public static final String ACTION_RESUME = "cl.drsmile.alarmacalendarica.action.RESUME_ALARM";
    private static final String CHANNEL_ID = "alarm_channel";

    private MediaPlayer player;
    private PowerManager.WakeLock wakeLock;
    private boolean paused = false;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private AudioManager.OnAudioFocusChangeListener afListener;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Alarm Service", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Channel for alarm playback");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long alarmId = -1;
        if (intent != null) {
            alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1);
            if (ACTION_STOP.equals(intent.getAction())) {
                final long idToStop = alarmId;
                // stop playback now immediately
                stopPlaybackAndCleanup();
                // update oneShot flag in background, then stop service
                new Thread(() -> {
                    try {
                        cl.drsmile.alarmacalendarica.db.AlarmEntity a = cl.drsmile.alarmacalendarica.db.AppDatabase.getInstance(AlarmService.this).alarmDao().getById(idToStop);
                        if (a != null) {
                            if (a.oneShot) {
                                a.enabled = false;
                                cl.drsmile.alarmacalendarica.db.AppDatabase.getInstance(AlarmService.this).alarmDao().update(a);
                            }
                        }
                    } catch (Exception e) { Log.e(TAG, "Error updating oneShot state", e); }
                    // update next alarm notification
                    cl.drsmile.alarmacalendarica.service.AlarmNotificationManager.updateNextAlarmNotification(AlarmService.this);
                    stopSelf();
                }).start();
                return START_STICKY;
            } else if (ACTION_SNOOZE.equals(intent.getAction())) {
                final long id = alarmId;
                // schedule snooze and stop playback now
                stopPlaybackAndCleanup();
                new Thread(() -> {
                    try {
                        cl.drsmile.alarmacalendarica.db.AlarmEntity a = cl.drsmile.alarmacalendarica.db.AppDatabase.getInstance(this).alarmDao().getById(id);
                        if (a != null) AlarmScheduler.scheduleSnooze(this, a, 10);
                    } catch (Exception e) { Log.e(TAG, "Failed to schedule snooze", e); }
                    stopSelf();
                }).start();
                return START_STICKY;
            } else if (ACTION_PAUSE.equals(intent.getAction())) {
                // pause playback
                try { if (player != null && player.isPlaying()) { player.pause(); paused = true; } } catch (Exception ignored) {}
                // update notification to show Resume action
                updateNotificationForPaused(alarmId);
                return START_NOT_STICKY;
            } else if (ACTION_RESUME.equals(intent.getAction())) {
                try { if (player != null && paused) { player.start(); paused = false; } }
                catch (Exception ignored) {}
                updateNotificationForRinging(alarmId);
                return START_NOT_STICKY;
            }
        }

        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Alarma")
                .setContentText("Sonando...")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true);

        // action to snooze (10 minutes)
        Intent snooze = new Intent(this, AlarmService.class);
        snooze.setAction(ACTION_SNOOZE);
        snooze.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);
        final int REQ_SNOOZE = 0x1000;
        PendingIntent piSnooze = PendingIntent.getService(this, REQ_SNOOZE, snooze, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        nb.addAction(new NotificationCompat.Action(0, "Posponer 10m", piSnooze));

        // action to stop alarm
        Intent stop = new Intent(this, AlarmService.class);
        stop.setAction(ACTION_STOP);
        stop.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);
        final int REQ_STOP = 0x1001;
        PendingIntent piStop = PendingIntent.getService(this, REQ_STOP, stop, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        nb.addAction(new NotificationCompat.Action(0, "Detener", piStop));

        // action to pause/resume
        Intent pause = new Intent(this, AlarmService.class);
        pause.setAction(ACTION_PAUSE);
        pause.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);
        final int REQ_PAUSE = 0x1002;
        PendingIntent piPause = PendingIntent.getService(this, REQ_PAUSE, pause, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        nb.addAction(new NotificationCompat.Action(0, "Pausar", piPause));

        Notification n = nb.build();
        startForeground(1, n);

        // acquire wake lock to ensure CPU wakes; hold until user stops the alarm
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alarm:wakelock");
                wakeLock.acquire();
            }
        } catch (Exception e) { Log.w(TAG, "WakeLock failed", e); }

        // start playback selection in background: fetch alarm by id and use its soundUri if present
        final long alarmIdFinal = alarmId;
        new Thread(() -> {
            try {
                cl.drsmile.alarmacalendarica.db.AlarmEntity a = cl.drsmile.alarmacalendarica.db.AppDatabase.getInstance(this).alarmDao().getById(alarmIdFinal);
                // update next alarm notification when an alarm starts ringing
                cl.drsmile.alarmacalendarica.service.AlarmNotificationManager.updateNextAlarmNotification(this);
                // compute selectedUri (final) from alarm or defaults
                Uri tmpUri = null;
                if (a != null && a.soundUri != null) {
                    try { tmpUri = Uri.parse(a.soundUri); } catch (Exception ignored) {}
                }
                if (tmpUri == null) {
                    try { tmpUri = Uri.parse("content://settings/system/alarm_alert"); }
                    catch (Exception ignored) { tmpUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM); }
                }
                final Uri selectedUri = tmpUri;

                if (selectedUri != null) {
                    try {
                        // request audio focus and prepare to hold it while alarm rings
                        audioManager = (AudioManager) AlarmService.this.getSystemService(Context.AUDIO_SERVICE);
                        afListener = focusChange -> {
                            // We intentionally do not stop playback on focus loss; try to keep sounding
                            try {
                                if (player != null) {
                                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                                        // on permanent loss, keep playing but at full volume; do not stop
                                        player.setVolume(1.0f, 1.0f);
                                    } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                                        // keep playing
                                    } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                                        // keep playing (do not duck)
                                        player.setVolume(1.0f, 1.0f);
                                    } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                                        try { player.setVolume(1.0f, 1.0f); } catch (Exception ignored) {}
                                    }
                                }
                            } catch (Exception ignored) {}
                        };
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                                        .setOnAudioFocusChangeListener(afListener)
                                        .build();
                                audioManager.requestAudioFocus(focusRequest);
                            } else {
                                audioManager.requestAudioFocus(afListener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN);
                            }
                        } catch (Exception ignored) {}
                        player = new MediaPlayer();
                        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                        player.setDataSource(this, selectedUri);
                        player.setLooping(true);
                        // ensure completion restarts (defensive)
                        player.setOnCompletionListener(mp -> {
                            try { mp.seekTo(0); mp.start(); } catch (Exception ignored) {}
                        });
                        player.setOnErrorListener((mp, what, extra) -> {
                            Log.w(TAG, "MediaPlayer error what=" + what + " extra=" + extra + ", attempting restart");
                            try {
                                mp.reset();
                                mp.setDataSource(AlarmService.this, selectedUri);
                                mp.prepare();
                                mp.start();
                                return true;
                            } catch (Exception ex) {
                                Log.e(TAG, "Failed to restart player", ex);
                                try {
                                    Uri defaultUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);
                                    mp.reset();
                                    mp.setDataSource(AlarmService.this, defaultUri);
                                    mp.prepare();
                                    mp.start();
                                    return true;
                                } catch (Exception ex2) { Log.e(TAG, "Fallback restart failed", ex2); }
                            }
                            return false;
                        });
                        player.prepare();
                        player.start();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to play selected alarm sound, fallback to default", e);
                        try {
                            Uri defaultUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);
                            player = new MediaPlayer();
                            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                            player.setDataSource(this, defaultUri);
                            player.setLooping(true);
                            player.prepare();
                            player.start();
                        } catch (Exception ex) { Log.e(TAG, "Failed to play default alarm sound", ex); }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error while obtaining alarm or playing sound", e);
            }
        }).start();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (player != null) {
                if (player.isPlaying()) player.stop();
                player.release();
                player = null;
            }
        } catch (Exception ignored) {}
        // abandon audio focus when stopping
        try {
            if (audioManager != null) {
                if (focusRequest != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    audioManager.abandonAudioFocusRequest(focusRequest);
                } else if (afListener != null) {
                    audioManager.abandonAudioFocus(afListener);
                }
            }
        } catch (Exception ignored) {}
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {}
        // update next alarm info now that this alarm stopped
        try { cl.drsmile.alarmacalendarica.service.AlarmNotificationManager.updateNextAlarmNotification(this); } catch (Exception ignored) {}
        stopForeground(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void updateNotificationForPaused(long alarmId) {
        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Alarma (Pausada)")
                .setContentText("Pausada")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true);
        Intent resume = new Intent(this, AlarmService.class);
        resume.setAction(ACTION_RESUME);
        resume.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);
        final int REQ_RESUME = 0x1003;
        PendingIntent piResume = PendingIntent.getService(this, REQ_RESUME, resume, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        nb.addAction(new NotificationCompat.Action(0, "Reanudar", piResume));
        nb.addAction(new NotificationCompat.Action(0, "Detener", PendingIntent.getService(this, 0, new Intent(this, AlarmService.class).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0))));
        startForeground(1, nb.build());
    }

    private void updateNotificationForRinging(long alarmId) {
        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Alarma")
                .setContentText("Sonando...")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true);
        Intent snooze = new Intent(this, AlarmService.class);
        snooze.setAction(ACTION_SNOOZE);
        snooze.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);
        final int REQ_SNOOZE = 0x1000;
        PendingIntent piSnooze = PendingIntent.getService(this, REQ_SNOOZE, snooze, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        nb.addAction(new NotificationCompat.Action(0, "Posponer 10m", piSnooze));
        Intent stop = new Intent(this, AlarmService.class);
        stop.setAction(ACTION_STOP);
        stop.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);
        final int REQ_STOP = 0x1001;
        PendingIntent piStop = PendingIntent.getService(this, REQ_STOP, stop, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        nb.addAction(new NotificationCompat.Action(0, "Detener", piStop));
        Intent pause = new Intent(this, AlarmService.class);
        pause.setAction(ACTION_PAUSE);
        pause.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);
        final int REQ_PAUSE = 0x1002;
        PendingIntent piPause = PendingIntent.getService(this, REQ_PAUSE, pause, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        nb.addAction(new NotificationCompat.Action(0, "Pausar", piPause));
        startForeground(1, nb.build());
    }

    private void stopPlaybackAndCleanup() {
        try {
            if (player != null) {
                try { if (player.isPlaying()) player.stop(); } catch (Exception ignored) {}
                try { player.release(); } catch (Exception ignored) {}
                player = null;
            }
        } catch (Exception ignored) {}
        // abandon audio focus
        try {
            if (audioManager != null) {
                if (focusRequest != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    audioManager.abandonAudioFocusRequest(focusRequest);
                } else if (afListener != null) {
                    audioManager.abandonAudioFocus(afListener);
                }
            }
        } catch (Exception ignored) {}
        // release wakelock
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        // update next alarm info now that this alarm stopped
        try { cl.drsmile.alarmacalendarica.service.AlarmNotificationManager.updateNextAlarmNotification(this); } catch (Exception ignored) {}
        // ensure notification removed
        try { stopForeground(true); } catch (Exception ignored) {}
    }
}

