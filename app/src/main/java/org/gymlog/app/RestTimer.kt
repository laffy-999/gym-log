package org.gymlog.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.app.Notification
import androidx.core.content.edit

/**
 * Rest countdown shared by the visible app and a background alarm.
 * The end time is persisted, so the countdown survives the process being killed.
 * Both expiry paths run on the main thread and call [expire]; only the first one for a given end time alerts.
 * Without the user-granted exact-alarm permission, Android may deliver the background alert late.
 */
object RestTimer {
    /** True while the activity is started; decides between an in-app signal and a notification. */
    @Volatile var appVisible = false

    private const val KEY_ENDS_AT = "rest_ends_at"
    private const val EXTRA_ENDS_AT = "ends_at"

    fun endsAt(context: Context): Long? = prefs(context).getLong(KEY_ENDS_AT, 0).takeIf { it > 0 }

    fun schedule(context: Context, endsAt: Long) {
        // Synchronous: the alarm receiver may run in a fresh process and must see this end time.
        prefs(context).edit(commit = true) { putLong(KEY_ENDS_AT, endsAt) }
        val alarms = context.getSystemService(AlarmManager::class.java)
        val alarm = pending(context, endsAt)
        if (canAlertExactly(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, alarm)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, alarm)
    }

    fun cancel(context: Context) {
        prefs(context).edit(commit = true) { remove(KEY_ENDS_AT) }
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, 0))
    }

    fun canAlertExactly(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Signals the end of the rest identified by [endsAt]. Returns false if it was already signalled, stopped, or replaced. */
    fun expire(context: Context, endsAt: Long): Boolean {
        if (endsAt <= 0 || prefs(context).getLong(KEY_ENDS_AT, 0) != endsAt) return false
        cancel(context)
        val settings = SettingsStore(context).load()
        if (appVisible) {
            if (settings.restVibrate) vibrate(context)
            if (settings.restSound) beep()
        } else if (canNotify(context)) {
            notify(context, settings)
        } else if (settings.restVibrate) {
            vibrate(context)
        }
        return true
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
    }

    private fun beep() {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 600)
    }

    private fun notify(context: Context, settings: Settings) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val (id, name) = when {
            settings.restSound && settings.restVibrate -> "rest_sound_vibrate" to "Rest timer (sound and vibration)"
            settings.restSound -> "rest_sound" to "Rest timer (sound)"
            settings.restVibrate -> "rest_vibrate" to "Rest timer (vibration)"
            else -> "rest_silent" to "Rest timer (silent)"
        }
        if (manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                if (!settings.restSound) setSound(null, null)
                enableVibration(settings.restVibrate)
                if (settings.restVibrate) vibrationPattern = longArrayOf(0, 300, 150, 300)
            })
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE)
        manager.notify(1, Notification.Builder(context, id)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Rest over")
            .setContentText("Time for your next set")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }

    private fun pending(context: Context, endsAt: Long): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, RestAlarmReceiver::class.java).putExtra(EXTRA_ENDS_AT, endsAt),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun prefs(context: Context) = context.getSharedPreferences("rest_timer", Context.MODE_PRIVATE)

    internal fun endsAtExtra(intent: Intent): Long = intent.getLongExtra(EXTRA_ENDS_AT, 0)
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RestTimer.expire(context, RestTimer.endsAtExtra(intent))
    }
}
