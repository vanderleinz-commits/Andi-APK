package id.vanderleinz.lumo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
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
import android.view.accessibility.AccessibilityManager;
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
    static final String ACTION_PERMISSIONS = "id.vanderleinz.andi.PERMISSIONS";
    private static final int REQUEST_ANDI_NOTIFICATIONS = 1;
    private static final String ASKED_FOR_NOTIFICATIONS = "asked_for_notifications";
    private static final int NAVY = 0xff10192b;
    private static final int WHITE = 0xffedf3ff;
    private static final int MUTED = 0xffb5c2df;
    private static final int COCOA = 0xffe3baa0;
    private Button toggle;
    private TextView status;
    private Button focus;
    private AlertDialog permissionDialog;
    private Button overlayPermission;
    private Button listenerPermission;
    private Button andiNotifications;
    private Button exactAlarms;
    private Button accessibilityPermission;
    private Button lyricsPermission;
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

        Button permissions = new Button(this);
        permissions.setAllCaps(false);
        permissions.setText("Izin & panduan instalasi");
        permissions.setOnClickListener(view -> showPermissions());
        add(panel, permissions, dp(54), 8);

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
        if (state != null && state.getBoolean("permissions_open")) showPermissions();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("permissions_open", permissionDialog != null && permissionDialog.isShowing());
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
        if (ACTION_PERMISSIONS.equals(action)) {
            getIntent().setAction(null);
            showPermissions();
        } else if (ACTION_CALENDAR.equals(action)) {
            getIntent().setAction(null);
            showCalendar();
        } else if (ACTION_LYRICS.equals(action)) {
            getIntent().setAction(null);
            showLyricsConsent();
        }
    }

    private void showLyricsConsent() {
        SharedPreferences preferences = NotificationPreferences.get(this);
        boolean allowed = preferences.getBoolean(NotificationPreferences.LYRICS_CONSENT, false);
        new AlertDialog.Builder(this).setTitle("Akses pencarian lirik")
                .setMessage("Untuk mencari lirik, Andi mengirim judul dan nama artis lagu aktif ke LRCLIB selama panel lirik ditampilkan.")
                .setNegativeButton("Kembali", null)
                .setPositiveButton(allowed ? "Nonaktifkan" : "Izinkan", (dialog, which) -> {
                    preferences.edit().putBoolean(NotificationPreferences.LYRICS_CONSENT, !allowed).apply();
                    renderPermissionStatus();
                }).show();
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
            dialog.setNeutralButton("Izin pengingat", (window, which) -> showPermissions());
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
                requestAndiNotifications();
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

    @Override
    protected void onDestroy() {
        if (permissionDialog != null) permissionDialog.dismiss();
        super.onDestroy();
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

    private void showPermissions() {
        if (permissionDialog != null && permissionDialog.isShowing()) return;
        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(20), dp(6), dp(20), dp(14));
        scroll.addView(rows);

        overlayPermission = permissionRow(rows, "Diperlukan agar Andi bisa melayang di atas aplikasi lain.",
                () -> openSetting(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, appUri())));
        listenerPermission = permissionRow(rows, "Pilih Andi untuk menampilkan notifikasi masuk dan mengontrol musik.",
                this::openNotificationAccess);
        andiNotifications = permissionRow(rows, "Izinkan Andi mengirim notifikasi pengingat dan statusnya.",
                this::requestAndiNotifications);
        if (Build.VERSION.SDK_INT >= 31) {
            exactAlarms = permissionRow(rows, "Aktifkan agar pengingat kalender bisa berbunyi tepat waktu.",
                    () -> openSetting(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, appUri())));
        }
        accessibilityPermission = permissionRow(rows,
                "Pilih Andi · Tombol cepat untuk aksi Daya, Kunci, dan Screenshot. Kunci dan Screenshot perlu Android 9.",
                () -> openSetting(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        if (Build.VERSION.SDK_INT >= 33) {
            Button appInfo = permissionRow(rows,
                    "Jika akses dibatasi, buka Info aplikasi, ketuk ⋮, pilih Izinkan setelan terbatas, lalu kembali untuk mengaktifkan aksesnya.",
                    () -> openSetting(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri())));
            appInfo.setText("Buka info aplikasi Andi");
        }
        lyricsPermission = permissionRow(rows, "Pilih apakah Andi boleh mencari lirik lagu lewat LRCLIB.",
                this::showLyricsConsent);
        Button install = permissionRow(rows, "Lihat langkah memasang atau memperbarui APK trial.",
                this::showInstallGuide);
        install.setText("Panduan pemasangan APK");

        permissionDialog = new AlertDialog.Builder(this).setTitle("Izin & panduan instalasi")
                .setView(scroll).setPositiveButton("Selesai", null).create();
        permissionDialog.setOnDismissListener(ignored -> permissionDialog = null);
        permissionDialog.show();
        renderPermissionStatus();
    }

    private Button permissionRow(LinearLayout rows, String description, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setMinHeight(dp(44));
        button.setOnClickListener(view -> action.run());
        add(rows, button, -2, 8);

        TextView explanation = new TextView(this);
        explanation.setText(description);
        explanation.setTextSize(13);
        explanation.setPadding(dp(6), 0, dp(6), dp(4));
        add(rows, explanation, -2, 1);
        return button;
    }

    private void renderPermissionStatus() {
        if (permissionDialog == null) return;
        NotificationManager notifications = getSystemService(NotificationManager.class);
        boolean listening = Build.VERSION.SDK_INT >= 27
                ? notifications.isNotificationListenerAccessGranted(new ComponentName(this, PhoneEvents.class))
                : PhoneEvents.isConnected();
        setPermissionStatus(overlayPermission, "Tampil di atas aplikasi", Settings.canDrawOverlays(this));
        setPermissionStatus(listenerPermission, "Akses notifikasi dan musik", listening);
        setPermissionStatus(andiNotifications, "Notifikasi dari Andi", notifications.areNotificationsEnabled());
        if (exactAlarms != null) {
            setPermissionStatus(exactAlarms, "Alarm & pengingat", ReminderReceiver.canScheduleExact(this));
        }
        boolean quickActionsEnabled = getSystemService(AccessibilityManager.class)
                .getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .stream().anyMatch(service -> {
                    android.content.pm.ServiceInfo info = service.getResolveInfo().serviceInfo;
                    return getPackageName().equals(info.packageName) && QuickActionsService.class.getName().equals(info.name);
                });
        setPermissionStatus(accessibilityPermission, "Aksesibilitas tombol cepat", quickActionsEnabled);
        setPermissionStatus(lyricsPermission, "Pencarian lirik",
                NotificationPreferences.get(this).getBoolean(NotificationPreferences.LYRICS_CONSENT, false));
    }

    private void setPermissionStatus(Button button, String title, boolean enabled) {
        button.setText(title + (enabled ? " · Aktif" : " · Belum aktif"));
    }

    private void openNotificationAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            Intent details = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            new ComponentName(this, PhoneEvents.class).flattenToString());
            try {
                startActivity(details);
                return;
            } catch (android.content.ActivityNotFoundException | SecurityException ignored) {
                // Some devices expose only the list of notification listeners.
            }
        }
        openSetting(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void requestAndiNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            SharedPreferences preferences = getPreferences(MODE_PRIVATE);
            if (!preferences.getBoolean(ASKED_FOR_NOTIFICATIONS, false)
                    || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                preferences.edit().putBoolean(ASKED_FOR_NOTIFICATIONS, true).apply();
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_ANDI_NOTIFICATIONS);
                return;
            }
        }
        openSetting(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ANDI_NOTIFICATIONS) renderPermissionStatus();
    }

    private void showInstallGuide() {
        new AlertDialog.Builder(this).setTitle("Pasang atau perbarui Andi")
                .setMessage("1. Masuk ke GitHub dengan akun yang punya akses ke Andi-APK.\n"
                        + "2. Buka build yang berhasil, unduh Andi-debug, lalu ekstrak ZIP.\n"
                        + "3. Buka Andi-debug.apk dan konfirmasi pemasangan di Android. "
                        + "Jika diminta, izinkan pemasangan dari aplikasi yang membuka APK.\n"
                        + "4. Setelah terpasang, buka Andi dan kembali ke Izin & panduan instalasi.\n\n"
                        + "Jika tanda tangan APK berbeda, Android bisa menolak pembaruan. "
                        + "Catat pengingat sebelum menghapus versi lama karena data lokal ikut terhapus.")
                .setNegativeButton("Kembali", null)
                .setPositiveButton("Buka unduhan GitHub", (dialog, which) -> openSetting(new Intent(
                        Intent.ACTION_VIEW, Uri.parse("https://github.com/vanderleinz-commits/Andi-APK/actions")),
                        "Buka halaman Actions Andi-APK di browser."))
                .show();
    }

    private Uri appUri() {
        return Uri.fromParts("package", getPackageName(), null);
    }

    private void openSetting(Intent intent) {
        openSetting(intent, "Buka Setelan Android untuk mengatur izin ini.");
    }

    private void openSetting(Intent intent, String fallback) {
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, fallback, Toast.LENGTH_SHORT).show();
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
            showPermissions();
            return;
        }
        try {
            startForegroundService(service);
        } catch (IllegalStateException | SecurityException exception) {
            Toast.makeText(this, "Andi belum bisa ditampilkan. Coba lagi.", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderState() {
        renderFocus();
        renderPermissionStatus();
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
