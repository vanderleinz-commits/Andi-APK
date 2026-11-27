package id.vanderleinz.lumo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MainActivity extends Activity {
    static final String ACTION_LYRICS = "id.vanderleinz.andi.LYRICS";
    static final String ACTION_CALENDAR = "id.vanderleinz.andi.CALENDAR";
    private static final int NAVY = 0xff10192b;
    private static final int WHITE = 0xffedf3ff;
    private static final int MUTED = 0xffb5c2df;
    private static final int COCOA = 0xffe3baa0;
    private Button toggle;
    private TextView status;
    private Button focus;
    private Button quickAccess;
    private Button phoneAccess;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tickFocus = this::renderFocus;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            renderState();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(NAVY);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        content.setPadding(dp(22), dp(22), dp(22), dp(22));
        scroll.addView(content);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        int width = Math.min(dp(460), Math.max(0, getResources().getDisplayMetrics().widthPixels - dp(44)));
        content.addView(panel, new LinearLayout.LayoutParams(width, -2));

        TextView title = label("Andi", 38, WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        add(panel, title, -2, 0);
        add(panel, label("Teman kecil di layar kamu", 15, MUTED), -2, 2);

        PetView preview = new PetView(this);
        preview.setBackground(shape(0xff192644, 28));
        preview.setContentDescription("Andi. Ketuk untuk melihat ekspresinya.");
        preview.setOnClickListener(view -> preview.react());
        add(panel, preview, dp(174), 24);

        status = label("", 14, MUTED);
        status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(panel, status, -2, 16);

        toggle = new Button(this);
        toggle.setAllCaps(false);
        toggle.setTextColor(NAVY);
        toggle.setTextSize(16);
        toggle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        toggle.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x3810192b), shape(COCOA, 18), null));
        toggle.setOnClickListener(view -> toggleOverlay());
        add(panel, toggle, dp(54), 14);

        phoneAccess = new Button(this);
        phoneAccess.setAllCaps(false);
        phoneAccess.setText("Akses notifikasi dan musik");
        phoneAccess.setOnClickListener(view -> explainAccess(false));
        add(panel, phoneAccess, dp(54), 8);
        add(panel, label("Opsional. Bubble bisa menampilkan pratinjau pesan saat HP terbuka.", 13, MUTED), -2, 6);

        SharedPreferences notificationSettings = NotificationPreferences.get(this);
        Switch quiet = new Switch(this);
        quiet.setText("Mode Tenang");
        quiet.setTextColor(WHITE);
        quiet.setChecked(notificationSettings.getBoolean(NotificationPreferences.QUIET, false));
        quiet.setOnCheckedChangeListener((button, checked) -> notificationSettings.edit()
                .putBoolean(NotificationPreferences.QUIET, checked).apply());
        add(panel, quiet, dp(48), 14);
        add(panel, label("Jeda bubble notifikasi sambil tetap menampilkan Andi dan musik.", 13, MUTED), -2, 2);

        Button perApp = new Button(this);
        perApp.setAllCaps(false);
        perApp.setText("Notifikasi per aplikasi");
        perApp.setOnClickListener(view -> chooseNotificationApp(notificationSettings));
        add(panel, perApp, dp(48), 8);

        Switch autoTuck = new Switch(this);
        autoTuck.setText("Menepi saat keyboard terbuka");
        autoTuck.setTextColor(WHITE);
        autoTuck.setChecked(notificationSettings.getBoolean(NotificationPreferences.AUTO_TUCK, false));
        autoTuck.setOnCheckedChangeListener((button, checked) -> notificationSettings.edit()
                .putBoolean(NotificationPreferences.AUTO_TUCK, checked).apply());
        add(panel, autoTuck, dp(48), 8);

        focus = new Button(this);
        focus.setAllCaps(false);
        focus.setOnClickListener(view -> toggleFocus());
        add(panel, focus, dp(48), 8);

        Button calendar = new Button(this);
        calendar.setAllCaps(false);
        calendar.setText("Kalender cepat");
        calendar.setOnClickListener(view -> showCalendar());
        add(panel, calendar, dp(48), 8);

        Button shortcuts = new Button(this);
        shortcuts.setAllCaps(false);
        shortcuts.setText("Pilih pintasan menu");
        shortcuts.setOnClickListener(view -> chooseShortcuts(notificationSettings));
        add(panel, shortcuts, dp(48), 8);

        quickAccess = new Button(this);
        quickAccess.setAllCaps(false);
        quickAccess.setText("Akses daya, kunci, dan screenshot");
        quickAccess.setOnClickListener(view -> explainAccess(true));
        add(panel, quickAccess, dp(48), 8);

        TextView hint = label("Ketuk untuk menu cepat • Ketuk dua kali untuk musik\nTahan tiga detik untuk menyembunyikan Andi", 14, MUTED);
        hint.setLineSpacing(dp(3), 1);
        add(panel, hint, -2, 15);
        setContentView(scroll);

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
            scroll.requestApplyInsets();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(FloatingPetService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderState();
        openRequestedPanel();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openRequestedPanel();
    }

    private void openRequestedPanel() {
        String action = getIntent().getAction();
        if (ACTION_CALENDAR.equals(action)) {
            getIntent().setAction(null);
            showCalendar();
        } else if (ACTION_LYRICS.equals(action)) {
            getIntent().setAction(null);
            new AlertDialog.Builder(this).setTitle("Tampilkan lirik")
                    .setMessage("Untuk mencari lirik, Andi mengirim judul dan nama artis lagu aktif ke LRCLIB selama panel lirik ditampilkan.")
                    .setNegativeButton("Nanti", null)
                    .setPositiveButton("Tampilkan", (dialog, which) ->
                            NotificationPreferences.get(this).edit().putBoolean(NotificationPreferences.LYRICS_CONSENT, true).apply())
                    .show();
        }
    }

    private void showCalendar() {
        java.util.List<ReminderReceiver.Reminder> reminders = ReminderReceiver.list(this);
        String[] rows = new String[reminders.size() + 1];
        rows[0] = "Tambah pengingat";
        java.text.DateFormat formatter = java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
        for (int index = 0; index < reminders.size(); index++) {
            ReminderReceiver.Reminder reminder = reminders.get(index);
            rows[index + 1] = formatter.format(new java.util.Date(reminder.time)) + " · " + reminder.title;
        }
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("Kalender cepat")
                .setItems(rows, (window, index) -> {
                    if (index == 0) pickReminderDate();
                    else {
                        ReminderReceiver.Reminder reminder = reminders.get(index - 1);
                        new AlertDialog.Builder(this).setTitle(reminder.title).setMessage(rows[index])
                                .setNegativeButton("Kembali", (confirm, which) -> showCalendar())
                                .setPositiveButton("Hapus", (confirm, which) -> {
                                    ReminderReceiver.delete(this, reminder.id);
                                    showCalendar();
                                }).show();
                    }
                }).setNegativeButton("Tutup", null);
        if (!ReminderReceiver.canScheduleExact(this) && Build.VERSION.SDK_INT >= 31) {
            dialog.setNeutralButton("Izinkan alarm tepat waktu", (window, which) -> {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())));
                } catch (android.content.ActivityNotFoundException error) {
                    Toast.makeText(this, "Buka Alarms & reminders di Setelan Android.", Toast.LENGTH_LONG).show();
                }
            });
        }
        dialog.show();
    }

    private void pickReminderDate() {
        java.util.Calendar date = java.util.Calendar.getInstance();
        android.app.DatePickerDialog days = new android.app.DatePickerDialog(this, (pick, year, month, day) -> {
            date.set(year, month, day);
            new android.app.TimePickerDialog(this, (clock, hour, minute) -> {
                date.set(java.util.Calendar.HOUR_OF_DAY, hour);
                date.set(java.util.Calendar.MINUTE, minute);
                date.set(java.util.Calendar.SECOND, 0);
                date.set(java.util.Calendar.MILLISECOND, 0);
                if (date.getTimeInMillis() <= System.currentTimeMillis()) {
                    Toast.makeText(this, "Pilih waktu yang masih akan datang.", Toast.LENGTH_SHORT).show();
                    return;
                }
                saveReminder(date.getTimeInMillis());
            }, date.get(java.util.Calendar.HOUR_OF_DAY), date.get(java.util.Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(this)).show();
        }, date.get(java.util.Calendar.YEAR), date.get(java.util.Calendar.MONTH), date.get(java.util.Calendar.DAY_OF_MONTH));
        days.getDatePicker().setMinDate(System.currentTimeMillis());
        days.show();
    }

    private void saveReminder(long time) {
        android.widget.EditText title = new android.widget.EditText(this);
        title.setHint("Nama acara");
        title.setSingleLine(true);
        title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Pengingat baru").setView(title)
                .setNegativeButton("Batal", null).setPositiveButton("Simpan", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String event = title.getText().toString().trim();
            if (event.isEmpty()) {
                title.setError("Isi nama acara.");
                return;
            }
            if (time <= System.currentTimeMillis()) {
                Toast.makeText(this, "Pilih waktu yang masih akan datang.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                pickReminderDate();
                return;
            }
            boolean exact = ReminderReceiver.add(this, event, time);
            Toast.makeText(this, exact ? "Pengingat tersimpan." :
                    "Pengingat tersimpan. Android bisa menundanya jika izin alarm tepat waktu mati.",
                    Toast.LENGTH_LONG).show();
            dialog.dismiss();
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
            }
        }));
        dialog.show();
    }

    @Override
    protected void onStop() {
        ui.removeCallbacks(tickFocus);
        unregisterReceiver(stateReceiver);
        super.onStop();
    }

    private void chooseShortcuts(SharedPreferences settings) {
        int selected = settings.getInt(NotificationPreferences.SHORTCUTS, NotificationPreferences.ALL_SHORTCUTS);
        boolean[] checked = new boolean[NotificationPreferences.SHORTCUT_LABELS.length];
        for (int i = 0; i < checked.length; i++) checked[i] = (selected & (1 << i)) != 0;
        new AlertDialog.Builder(this).setTitle("Pintasan menu Andi")
                .setMultiChoiceItems(NotificationPreferences.SHORTCUT_LABELS, checked,
                        (dialog, index, enabled) -> checked[index] = enabled)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (dialog, which) -> {
                    int enabled = 0;
                    for (int i = 0; i < checked.length; i++) if (checked[i]) enabled |= 1 << i;
                    settings.edit().putInt(NotificationPreferences.SHORTCUTS, enabled).apply();
                }).show();
    }

    private void explainAccess(boolean systemActions) {
        String action = systemActions ? Settings.ACTION_ACCESSIBILITY_SETTINGS :
                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS;
        String permission = systemActions ? "Aksi sistem" : "Akses notifikasi dan musik";
        String explanation = systemActions
                ? "Aktifkan Andi · Tombol cepat agar tombol Daya, Kunci, dan Screenshot bisa menjalankan aksi sistem."
                : "Pilih Andi di setelan Akses notifikasi agar kartu notifikasi dan musik dapat ditampilkan.";
        AlertDialog.Builder guide = new AlertDialog.Builder(this).setTitle(permission)
                .setMessage(explanation + (Build.VERSION.SDK_INT >= 33
                        ? "\n\nJika Android menampilkan Restricted setting, buka Info aplikasi Andi, ketuk menu ⋮, lalu pilih Allow restricted settings. Setelah itu kembali ke pengaturan ini."
                        : ""))
                .setNegativeButton("Nanti", null)
                .setPositiveButton("Buka setelan", (dialog, which) -> openSetting(new Intent(action)));
        if (Build.VERSION.SDK_INT >= 33) {
            guide.setNeutralButton("Info aplikasi", (dialog, which) -> openSetting(
                    new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        }
        guide.show();
    }

    private void openSetting(Intent intent) {
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException error) {
            Toast.makeText(this, "Pengaturan ini bisa dibuka dari Setelan Android.", Toast.LENGTH_SHORT).show();
        }
    }

    private void chooseNotificationApp(SharedPreferences settings) {
        PackageManager packages = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, String> labels = new LinkedHashMap<>();
        for (ResolveInfo entry : packages.queryIntentActivities(launcher, 0)) {
            String packageName = entry.activityInfo.packageName;
            if (!packageName.equals(getPackageName())) {
                labels.putIfAbsent(packageName, entry.loadLabel(packages).toString());
            }
        }
        if (labels.isEmpty()) {
            Toast.makeText(this, "Belum ada aplikasi yang tersedia.", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> appIds = new ArrayList<>(labels.keySet());
        Collator order = Collator.getInstance();
        appIds.sort(Comparator.comparing(labels::get, order));
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String name : labels.values()) counts.merge(name, 1, Integer::sum);
        CharSequence[] items = new CharSequence[appIds.size()];
        for (int index = 0; index < appIds.size(); index++) {
            String appId = appIds.get(index);
            String name = labels.get(appId);
            if (counts.get(name) > 1) name += " (" + appId + ")";
            items[index] = name + " · " + NotificationPreferences.LABELS[
                    NotificationPreferences.appPreview(settings, appId)];
        }
        new AlertDialog.Builder(this).setTitle("Notifikasi per aplikasi")
                .setItems(items, (dialog, index) -> {
                    String appId = appIds.get(index);
                    new AlertDialog.Builder(this).setTitle(labels.get(appId))
                            .setSingleChoiceItems(NotificationPreferences.LABELS,
                                    NotificationPreferences.appPreview(settings, appId), null)
                            .setNegativeButton("Batal", null)
                            .setPositiveButton("Simpan", (options, button) -> {
                                int mode = ((AlertDialog) options).getListView().getCheckedItemPosition();
                                settings.edit().putInt(NotificationPreferences.APP_PREFIX + appId, mode).apply();
                            }).show();
                })
                .setNegativeButton("Tutup", null).show();
    }

    private void toggleFocus() {
        if (!Settings.canDrawOverlays(this)) {
            toggleOverlay();
            return;
        }
        String action = FloatingPetService.isFocusing()
                ? FloatingPetService.ACTION_CANCEL_FOCUS : FloatingPetService.ACTION_START_FOCUS;
        try {
            startForegroundService(new Intent(this, FloatingPetService.class).setAction(action));
        } catch (IllegalStateException | SecurityException error) {
            Toast.makeText(this, "Sesi fokus belum bisa dimulai.", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderFocus() {
        ui.removeCallbacks(tickFocus);
        long remaining = (FloatingPetService.focusRemaining() + 999) / 1000;
        if (remaining == 0) {
            focus.setText("Mulai fokus 25 menit");
        } else {
            focus.setText(String.format(java.util.Locale.getDefault(), "Akhiri fokus · %d:%02d",
                    remaining / 60, remaining % 60));
            ui.postDelayed(tickFocus, 1000);
        }
    }

    private void toggleOverlay() {
        Intent service = new Intent(this, FloatingPetService.class);
        if (FloatingPetService.isShowing()) {
            stopService(service);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.fromParts("package", getPackageName(), null)));
            } catch (android.content.ActivityNotFoundException exception) {
                Toast.makeText(this, "Buka Setelan lalu cari izin tampil di atas aplikasi lain.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        try {
            startForegroundService(service);
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                android.content.SharedPreferences preferences = getPreferences(MODE_PRIVATE);
                if (!preferences.getBoolean("asked_for_notifications", false)) {
                    preferences.edit().putBoolean("asked_for_notifications", true).apply();
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                }
            }
        } catch (IllegalStateException | SecurityException exception) {
            Toast.makeText(this, "Andi belum bisa ditampilkan. Coba lagi.", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderState() {
        renderFocus();
        quickAccess.setText(QuickActionsService.isConnected() ? "Aksi sistem aktif" : "Akses daya, kunci, dan screenshot");
        android.app.NotificationManager notifications = getSystemService(android.app.NotificationManager.class);
        boolean access = Build.VERSION.SDK_INT >= 27
                ? notifications.isNotificationListenerAccessGranted(new android.content.ComponentName(this, PhoneEvents.class))
                : PhoneEvents.isConnected();
        phoneAccess.setText(access ? "Akses notifikasi dan musik aktif" : "Akses notifikasi dan musik");
        boolean showing = FloatingPetService.isShowing();
        boolean allowed = Settings.canDrawOverlays(this);
        status.setText(showing ? "Andi sedang melayang" :
                allowed ? "Andi sedang beristirahat" : "Aktifkan izin untuk menampilkan Andi di atas aplikasi lain");
        toggle.setText(showing ? "Sembunyikan Andi" : allowed ? "Tampilkan Andi" : "Buka pengaturan izin");
    }

    private void add(LinearLayout panel, android.view.View view, int height, int topMargin) {
        LinearLayout.LayoutParams entries = new LinearLayout.LayoutParams(-1, height);
        entries.topMargin = dp(topMargin);
        panel.addView(view, entries);
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
