package org.gymlog.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit

/**
 * Home-screen widget showing the active workout (live duration and set count) or the next split day.
 * It renders a small snapshot published by the app, so it never opens the database itself.
 */
class GymWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, render(context, read(context)))
    }

    /** What the widget shows. [startedAt] is non-null while a workout is running. */
    data class Snapshot(val title: String, val subtitle: String, val startedAt: Long?)

    companion object {
        fun snapshot(data: TrainingData): Snapshot {
            val active = data.active
            val weekSets = data.weekSets.values.sum()
            return if (active != null) {
                val sets = data.sets.count { it.workoutId == active.id && it.parentSetId == null }
                Snapshot(active.dayName ?: "Workout", setCountText(sets), active.startedAt)
            } else {
                Snapshot(data.nextDay?.let { "Next: ${it.name}" } ?: "Gym Log", "This week: ${setCountText(weekSets)}", null)
            }
        }

        /** Stores the snapshot and redraws every placed widget. Cheap enough to call after each change. */
        fun publish(context: Context, snapshot: Snapshot) {
            if (read(context) == snapshot) return
            context.getSharedPreferences("widget", Context.MODE_PRIVATE).edit {
                putString("title", snapshot.title)
                putString("subtitle", snapshot.subtitle)
                putLong("started_at", snapshot.startedAt ?: 0)
            }
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, GymWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, render(context, snapshot))
        }

        private fun read(context: Context): Snapshot {
            val prefs = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
            return Snapshot(prefs.getString("title", null) ?: "Gym Log", prefs.getString("subtitle", null) ?: "Tap to start",
                prefs.getLong("started_at", 0).takeIf { it > 0 })
        }

        private fun render(context: Context, snapshot: Snapshot): RemoteViews = RemoteViews(context.packageName, R.layout.widget_gym).apply {
            setTextViewText(R.id.widget_title, snapshot.title)
            setTextViewText(R.id.widget_subtitle, snapshot.subtitle)
            if (snapshot.startedAt != null) {
                val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - snapshot.startedAt)
                setChronometer(R.id.widget_timer, base, null, true)
                setViewVisibility(R.id.widget_timer, View.VISIBLE)
            } else {
                setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime(), null, false)
                setViewVisibility(R.id.widget_timer, View.GONE)
            }
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE)
            setOnClickPendingIntent(R.id.widget_root, open)
        }
    }
}
