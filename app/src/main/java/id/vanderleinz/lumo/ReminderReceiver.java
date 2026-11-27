package id.vanderleinz.lumo;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.UUID;

public final class ReminderReceiver extends BroadcastReceiver {
    private static final String ACTION_FIRE = "id.vanderleinz.andi.FIRE_REMINDER";
    private static final String CHANNEL = "andi_reminders";

    static final class Reminder {
        final String id;
        final String title;
        final long time;

        Reminder(String id, String title, long time) {
            this.id = id;
            this.title = title;
            this.time = time;
        }
    }

    private static SharedPreferences store(Context context) {
        return context.getSharedPreferences("andi_reminders", Context.MODE_PRIVATE);
    }

    static List<Reminder> list(Context context) {
        SharedPreferences prefs = store(context);
        List<Reminder> reminders = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith("time.") || !(entry.getValue() instanceof Long)) continue;
            String id = entry.getKey().substring(5);
            reminders.add(new Reminder(id, prefs.getString("title." + id, "Pengingat"), (Long) entry.getValue()));
        }
        reminders.sort(Comparator.comparingLong(reminder -> reminder.time));
        return reminders;
    }

    static boolean canScheduleExact(Context context) {
        return Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    static boolean add(Context context, String title, long time) {
        Reminder reminder = new Reminder(UUID.randomUUID().toString(), title, time);
        store(context).edit().putLong("time." + reminder.id, time)
                .putString("title." + reminder.id, title).apply();
        return schedule(context, reminder);
    }

    static void delete(Context context, String id) {
        context.getSystemService(AlarmManager.class).cancel(alarm(context, id));
        store(context).edit().remove("time." + id).remove("title." + id).apply();
    }

    private static PendingIntent alarm(Context context, String id) {
        Intent event = new Intent(context, ReminderReceiver.class).setAction(ACTION_FIRE)
                .setData(Uri.parse("andi-reminder:" + id));
        return PendingIntent.getBroadcast(context, 0, event,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static boolean schedule(Context context, Reminder reminder) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        PendingIntent event = alarm(context, reminder.id);
        if (canScheduleExact(context)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.time, event);
                return true;
            } catch (SecurityException error) {
                Log.w("AndiCalendar", "Exact alarm access changed", error);
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.time, event);
        return false;
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(intent.getAction())) {
            for (Reminder reminder : list(context)) schedule(context, reminder);
            return;
        }
        if (!ACTION_FIRE.equals(intent.getAction()) || intent.getData() == null) return;
        String id = intent.getData().getSchemeSpecificPart();
        SharedPreferences prefs = store(context);
        long due = prefs.getLong("time." + id, 0);
        if (due == 0) return;
        String title = prefs.getString("title." + id, "Pengingat");
        if (due > System.currentTimeMillis()) {
            schedule(context, new Reminder(id, title, due));
            return;
        }
        delete(context, id);
        notifyReminder(context, id, title);
        if (!Settings.canDrawOverlays(context)
                || !context.getSystemService(PowerManager.class).isInteractive()
                || context.getSystemService(KeyguardManager.class).isKeyguardLocked()) return;
        try {
            Intent popup = new Intent(context, FloatingPetService.class)
                    .setAction(FloatingPetService.ACTION_REMINDER).putExtra("title", title);
            context.startForegroundService(popup);
        } catch (IllegalStateException | SecurityException error) {
            Log.i("AndiCalendar", "Overlay unavailable for reminder", error);
        }
    }

    private void notifyReminder(Context context, String id, String title) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!manager.areNotificationsEnabled()) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Pengingat Andi",
                NotificationManager.IMPORTANCE_HIGH));
        PendingIntent open = PendingIntent.getActivity(context, 24,
                new Intent(context, MainActivity.class).setAction(MainActivity.ACTION_CALENDAR)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        manager.notify(id, 1, new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle("Pengingat Andi")
                .setContentText(title).setStyle(new Notification.BigTextStyle().bigText(title))
                .setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(open).build());
    }
}
