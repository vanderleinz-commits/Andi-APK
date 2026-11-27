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
    private static final int NAVY = 0xff142139;
    private static final int PAPER = 0xfff3ecde;
    private static final int CREAM = 0xfffff9ed;
    private static final int MUTED = 0xff48556b;
    private static final int COCOA = 0xffedaf83;
    private static final int MINT = 0xff77e3cf;
    private static final int SKY = 0xffa4b9d4;
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
        scroll.setBackgroundColor(PAPER);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(26));
        scroll.addView(content);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int width = Math.min(dp(500), Math.max(1, getResources().getDisplayMetrics().widthPixels - dp(36)));
        content.addView(panel, new LinearLayout.LayoutParams(width, -2));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = heading("andi.", 34);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        status = label("", 13, NAVY);
        status.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        header.addView(status, new LinearLayout.LayoutParams(-2, -2));
        add(panel, header, -2, 0);
        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(NAVY);
        add(panel, divider, dp(2), 7);

        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(14), dp(8), dp(9), dp(10));
        hero.setBackground(surface(SKY, 12));
        hero.setMinimumHeight(dp(130));
        LinearLayout introduction = new LinearLayout(this);
        introduction.setOrientation(LinearLayout.VERTICAL);
        introduction.addView(heading("Hai, aku Andi.", 29));
        introduction.addView(label("Teman kecil di layar kamu.", 14, NAVY));
        hero.addView(introduction, new LinearLayout.LayoutParams(0, -2, 1));
        PetView preview = new PetView(this);
        preview.setContentDescription("Karakter Andi. Ketuk untuk melihat ekspresinya.");
        preview.setOnClickListener(view -> preview.react());
        hero.addView(preview, new LinearLayout.LayoutParams(dp(108), dp(108)));
        add(panel, hero, -2, 15);

        toggle = action("", MINT, this::toggleOverlay);
        toggle.setGravity(Gravity.CENTER);
        add(panel, toggle, -2, 11);
        add(panel, heading("Pengaturan", 20), -2, 16);

        SharedPreferences settings = NotificationPreferences.get(this);
        add(panel, action("Izin & panduan instalasi", COCOA, this::showPermissions), -2, 6);
        add(panel, action("Pilih pintasan menu", CREAM, () -> chooseShortcuts(settings)), -2, 7);
        Button characters = action("", CREAM, () -> {});
        renderCharacter(characters, preview, NotificationPreferences.character(settings));
        characters.setOnClickListener(view -> chooseCharacter(settings, characters, preview));
        add(panel, characters, -2, 7);
        Switch glasses = setting("Kacamata Andi", settings, NotificationPreferences.GLASSES, false);
        preview.setGlasses(glasses.isChecked());
        glasses.setOnCheckedChangeListener((button, checked) -> {
            settings.edit().putBoolean(NotificationPreferences.GLASSES, checked).apply();
            preview.setGlasses(checked);
        });
        add(panel, glasses, -2, 7);
        add(panel, setting("Animasi bubble", settings, NotificationPreferences.BUBBLE_MOTION, true), -2, 7);

        add(panel, heading("Notifikasi", 20), -2, 16);
        add(panel, setting("Mode Tenang", settings, NotificationPreferences.QUIET, false), -2, 6);
        add(panel, action("Notifikasi per aplikasi", CREAM, () -> chooseNotificationApp(settings)), -2, 7);
        add(panel, setting("Menepi saat keyboard terbuka", settings, NotificationPreferences.AUTO_TUCK, false), -2, 7);

        add(panel, heading("Produktivitas", 20), -2, 16);
        add(panel, action("Kalender cepat", COCOA, this::showCalendar), -2, 6);
        focus = action("", CREAM, this::toggleFocus);
        add(panel, focus, -2, 7);

        TextView hint = label("Ketuk sekali untuk pintasan. Ketuk dua kali untuk musik. Tahan tiga detik untuk menyembunyikan Andi.", 13, MUTED);
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

    private void chooseCharacter(SharedPreferences settings, Button picker, PetView preview) {
        PetStyle selected = NotificationPreferences.character(settings);
        boolean glasses = settings.getBoolean(NotificationPreferences.GLASSES, false);
        ScrollView scroll = new ScrollView(this);
        LinearLayout catalog = new LinearLayout(this);
        catalog.setOrientation(LinearLayout.VERTICAL);
        catalog.setPadding(dp(14), dp(8), dp(14), dp(12));
        scroll.addView(catalog);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Pilih karakter Andi")
                .setView(scroll)
                .setNegativeButton("Tutup", null)
                .create();
        PetStyle[] styles = PetStyle.values();
        LinearLayout row = null;
        for (int i = 0; i < styles.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                add(catalog, row, -2, i == 0 ? 0 : 8);
            }
            PetStyle style = styles[i];
            boolean active = style == selected;
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setPadding(dp(7), dp(4), dp(10), dp(10));
            tile.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x25142139), surface(active ? MINT : CREAM, 8), null));
            tile.setFocusable(true);
            tile.setContentDescription(style.label + (active ? ", sedang dipakai" : ""));
            tile.setAccessibilityDelegate(new android.view.View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(android.view.View host,
                        android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName(Button.class.getName());
                    info.setSelected(active);
                }
            });

            PetView thumbnail = new PetView(this);
            thumbnail.setStaticPreview();
            thumbnail.setGlasses(glasses);
            thumbnail.setCharacter(style);
            thumbnail.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            tile.addView(thumbnail, new LinearLayout.LayoutParams(-1, dp(83)));
            TextView name = label(style.label, 14, NAVY);
            name.setGravity(Gravity.CENTER);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            name.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            tile.addView(name, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i % 2 == 1) cell.setMarginStart(dp(8));
            row.addView(tile, cell);
            tile.setOnClickListener(view -> {
                settings.edit().putString(NotificationPreferences.CHARACTER, style.id).apply();
                renderCharacter(picker, preview, style);
                dialog.dismiss();
            });
        }
        dialog.show();
    }

    private void renderCharacter(Button picker, PetView preview, PetStyle style) {
        picker.setText("Karakter Andi · " + style.label);
        preview.setCharacter(style);
        preview.setContentDescription(style.label + ". Ketuk untuk melihat ekspresinya.");
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
        status.setText(showing ? "Melayang" : allowed ? "Istirahat" : "Izin overlay diperlukan");
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
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView heading(String title, int size) {
        TextView view = label(title, size, NAVY);
        view.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        view.setLetterSpacing(-.045f);
        return view;
    }

    private Button action(String title, int color, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setTextColor(NAVY);
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(48));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x25142139), surface(color, 8), null));
        button.setPadding(dp(13), dp(8), dp(13), dp(10));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private Switch setting(String title, SharedPreferences settings, String key, boolean enabledByDefault) {
        Switch toggle = new Switch(this);
        toggle.setText(title);
        toggle.setTextSize(15);
        toggle.setTextColor(NAVY);
        toggle.setMinHeight(dp(48));
        toggle.setBackground(surface(CREAM, 8));
        toggle.setPadding(dp(13), dp(8), dp(13), dp(10));
        toggle.setChecked(settings.getBoolean(key, enabledByDefault));
        toggle.setOnCheckedChangeListener((view, enabled) -> settings.edit().putBoolean(key, enabled).apply());
        return toggle;
    }

    private android.graphics.drawable.LayerDrawable surface(int color, int radius) {
        GradientDrawable offset = shape(NAVY, radius);
        GradientDrawable face = shape(color, radius);
        face.setStroke(dp(2), NAVY);
        android.graphics.drawable.LayerDrawable background = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{offset, face});
        background.setLayerInset(0, dp(3), dp(3), 0, 0);
        background.setLayerInset(1, 0, 0, dp(3), dp(3));
        return background;
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
