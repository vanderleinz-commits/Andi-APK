package id.vanderleinz.lumo;

import android.app.Notification;
import android.app.ActivityOptions;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.media.session.PlaybackState;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.ImageView;
import android.util.Log;
import android.app.KeyguardManager;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;


public final class FloatingPetService extends Service {
    static final String ACTION_REMINDER = "id.vanderleinz.andi.REMINDER";
    private static final long REMINDER_DISPLAY_MS = 10_000;
    static final String ACTION_STATE = "id.vanderleinz.andi.OVERLAY_STATE";
    static final String ACTION_START_FOCUS = "id.vanderleinz.andi.START_FOCUS";
    static final String ACTION_CANCEL_FOCUS = "id.vanderleinz.andi.CANCEL_FOCUS";
    private static final long FOCUS_LENGTH_MS = 25 * 60_000L;
    private static long focusEnd;
    private static final String ACTION_HIDE = "id.vanderleinz.andi.HIDE";
    private static final String CHANNEL = "floating_andi";
    private static final int NOTIFICATION_ID = 10;
    private static final int FOCUS_DONE_ID = 11;
    private static final String FOCUS_CHANNEL = "focus_andi";
    private static boolean showing;

    private Context overlayContext;
    private WindowManager windows;
    private WindowManager.LayoutParams params;
    private SharedPreferences position;
    private PetView pet;
    private LinearLayout bubble;
    private TextView alertView;
    private LinearLayout alertPanel;
    private ImageView alertIcon;
    private TextView musicView;
    private TextView artistView;
    private PetTouch petTouch;
    private LinearLayout musicPanel;
    private LinearLayout quickPanel;
    private boolean quickOpen;
    private Button shuffle;
    private Button repeat;
    private boolean temporaryReminder;
    private boolean reminderShowing;
    private final Runnable endReminder = this::endReminder;
    private boolean capturing;
    private DiscView disc;
    private boolean bubbleOnRight;
    private boolean bubbleAdded;
    private boolean keyboardVisible;
    private boolean tucked;
    private Alert currentAlert;
    private final Map<String, QuietCount> quietCounts = new LinkedHashMap<>();

    private static final class QuietCount {
        final String label;
        int count;

        QuietCount(String label) {
            this.label = label;
        }
    }
    private final ArrayDeque<Alert> pendingAlerts = new ArrayDeque<>();

    private static final class Alert {
        final Drawable icon;
        final String source;
        final String sender;
        final String message;
        final PendingIntent destination;

        Alert(Drawable icon, String source, String sender, String message, PendingIntent destination) {
            this.icon = icon;
            this.source = source;
            this.sender = sender;
            this.message = message;
            this.destination = destination;
        }
    }
    private Button previousTrack;
    private Button playPause;
    private Button nextTrack;
    private boolean hasMusic;
    private boolean alertActive;
    private boolean musicHidden = true;
    private WindowManager.LayoutParams bubbleParams;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences.OnSharedPreferenceChangeListener previewSettingsChanged =
            (settings, key) -> {
                if (key == null || key.startsWith(NotificationPreferences.APP_PREFIX)) clearAlert();
                if (NotificationPreferences.AUTO_TUCK.equals(key)) updateTuckedState();
                if (NotificationPreferences.QUIET.equals(key)) {
                    clearAlert();
                    if (settings.getBoolean(NotificationPreferences.QUIET, false)) quietCounts.clear();
                    else showQuietSummary();
                }
            };
    private final Runnable expireAlert = this::advanceAlert;
    private final Runnable finishFocus = () -> {
        changeFocus(false);
        showAlert(null, "Andi", "Fokus selesai", "Waktunya istirahat.", null);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
                FOCUS_CHANNEL, "Sesi fokus Andi", NotificationManager.IMPORTANCE_DEFAULT));
        manager.notify(FOCUS_DONE_ID, new Notification.Builder(this, FOCUS_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Fokus selesai")
                .setContentText("Waktunya istirahat.")
                .setAutoCancel(true)
                .build());
    };
    private final BroadcastReceiver screenState = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) clearAlert();
            else if (focusEnd != 0) {
                main.removeCallbacks(finishFocus);
                main.postDelayed(finishFocus, focusRemaining());
            }
        }
    };

    static boolean isFocusing() {
        return focusEnd != 0;
    }

    static long focusRemaining() {
        return Math.max(0, focusEnd - SystemClock.elapsedRealtime());
    }

    private void changeFocus(boolean start) {
        main.removeCallbacks(finishFocus);
        focusEnd = start ? SystemClock.elapsedRealtime() + FOCUS_LENGTH_MS : 0;
        if (start) {
            clearAlert();
            main.postDelayed(finishFocus, FOCUS_LENGTH_MS);
        }
        updateTuckedState();
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, createNotification());
        publishState(pet != null);
    }

    static boolean isShowing() {
        return showing;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        position = getSharedPreferences("pet_position", MODE_PRIVATE);
        try {
            torchManager = getSystemService(android.hardware.camera2.CameraManager.class);
            if (torchManager != null) torchManager.registerTorchCallback(torchStatus, main);
        } catch (RuntimeException error) {
            torchManager = null;
        }
        NotificationPreferences.get(this).registerOnSharedPreferenceChangeListener(previewSettingsChanged);
        IntentFilter screenOff = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        screenOff.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenState, screenOff, RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenState, screenOff);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_HIDE.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        boolean reminder = intent != null && ACTION_REMINDER.equals(intent.getAction());
        if (reminder && pet == null) temporaryReminder = true;
        else if (!reminder && temporaryReminder) {
            temporaryReminder = false;
            publishState(true);
        }
        try {
            Notification notification = createNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            if (Settings.canDrawOverlays(this)) {
                if (pet == null) showPet();
                if (reminder) showReminder(intent.getStringExtra("title"));
                if (intent != null && ACTION_START_FOCUS.equals(intent.getAction()) && focusEnd == 0) {
                    changeFocus(true);
                } else if (intent != null && ACTION_CANCEL_FOCUS.equals(intent.getAction())) {
                    changeFocus(false);
                }
            } else {
                stopSelf();
            }
        } catch (WindowManager.BadTokenException | WindowManager.InvalidDisplayException
                 | IllegalStateException | SecurityException exception) {
            Toast.makeText(this, "Andi belum bisa melayang. Periksa izin tampil di atas aplikasi lain.",
                    Toast.LENGTH_LONG).show();
            stopSelf();
        }
        return pet != null && !temporaryReminder ? START_STICKY : START_NOT_STICKY;
    }

    private void showPet() {
        overlayContext = this;
        if (Build.VERSION.SDK_INT >= 30) {
            Display display = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            if (display == null) throw new IllegalStateException("No default display");
            overlayContext = createDisplayContext(display)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        }
        windows = overlayContext.getSystemService(WindowManager.class);
        int size = Math.round(104 * overlayContext.getResources().getDisplayMetrics().density);
        params = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSLUCENT);
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        restorePosition();

        PetView companion = new PetView(overlayContext);
        companion.setContentDescription("Andi. Ketuk untuk menu cepat. Ketuk dua kali untuk musik. Tahan tiga detik untuk menyembunyikan Andi.");
        companion.setOnClickListener(view -> {
            companion.react();
            if (!temporaryReminder) {
                quickOpen = !quickOpen;
                showPanel(quickOpen ? 1 : 0);
            }
        });
        companion.setOnLongClickListener(view -> {
            stopSelf();
            return true;
        });
        petTouch = new PetTouch();
        companion.setOnTouchListener(petTouch);
        companion.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                int keyboardBottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
                int navigationBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                boolean obstructed = insets.isVisible(WindowInsets.Type.ime())
                        && keyboardBottom > navigationBottom + dp(48);
                if (keyboardVisible != obstructed) {
                    keyboardVisible = obstructed;
                    main.post(this::updateTuckedState);
                }
            }
            return view.onApplyWindowInsets(insets);
        });
        windows.addView(companion, params);
        pet = companion;
        createBubble();
        companion.requestApplyInsets();
        PhoneEvents.attach(this);
        publishState(!temporaryReminder);
    }

    private void createBubble() {
        bubble = new LinearLayout(overlayContext);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(8), dp(2), dp(8), dp(2));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(16));
        background.setColor(0xff202c45);
        background.setStroke(dp(1), 0xffbc8969);
        bubble.setBackground(background);
        alertPanel = new LinearLayout(overlayContext);
        alertPanel.setGravity(Gravity.CENTER_VERTICAL);
        alertPanel.setMinimumHeight(dp(42));
        alertPanel.setVisibility(View.GONE);
        alertPanel.setOnClickListener(view -> activateAlert());
        alertPanel.setOnTouchListener(new AlertSwipe());
        bubble.addView(alertPanel);
        alertIcon = new ImageView(overlayContext);
        alertIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        alertPanel.addView(alertIcon, new LinearLayout.LayoutParams(dp(27), dp(27)));
        alertView = newBubbleText(3);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(0, -2, 1);
        messageParams.leftMargin = dp(9);
        alertPanel.addView(alertView, messageParams);
        musicPanel = new LinearLayout(overlayContext);
        musicPanel.setOrientation(LinearLayout.VERTICAL);
        musicPanel.setVisibility(View.GONE);
        bubble.addView(musicPanel);
        LinearLayout row = new LinearLayout(overlayContext);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(39));
        musicPanel.addView(row);
        disc = new DiscView(overlayContext);
        LinearLayout.LayoutParams discParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        discParams.rightMargin = dp(6);
        row.addView(disc, discParams);
        LinearLayout musicDetails = new LinearLayout(overlayContext);
        musicDetails.setOrientation(LinearLayout.VERTICAL);
        row.addView(musicDetails, new LinearLayout.LayoutParams(0, -2, 1));
        musicView = newBubbleText(2);
        musicView.setTextSize(12);
        musicView.setPadding(0, 0, 0, 0);
        musicDetails.addView(musicView);
        artistView = newBubbleText(1);
        artistView.setTextSize(11);
        artistView.setTextColor(0xffd6b7a4);
        artistView.setPadding(0, 0, 0, 0);
        musicDetails.addView(artistView);
        LinearLayout controls = new LinearLayout(overlayContext);
        musicPanel.addView(controls);
        previousTrack = addPlaybackButton(controls, "⏮", "Lagu sebelumnya", PlaybackState.ACTION_SKIP_TO_PREVIOUS);
        playPause = addPlaybackButton(controls, "▶", "Putar",
                PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE);
        nextTrack = addPlaybackButton(controls, "⏭", "Lagu berikutnya", PlaybackState.ACTION_SKIP_TO_NEXT);
        LinearLayout modes = new LinearLayout(overlayContext);
        musicPanel.addView(modes);
        shuffle = newMusicButton("Acak", "Acak lagu");
        repeat = newMusicButton("Ulang", "Ulangi lagu");
        modes.addView(shuffle, new LinearLayout.LayoutParams(0, dp(34), 1));
        modes.addView(repeat, new LinearLayout.LayoutParams(0, dp(34), 1));
        shuffle.setOnClickListener(view -> PhoneEvents.changePlaybackMode(true));
        repeat.setOnClickListener(view -> PhoneEvents.changePlaybackMode(false));
        setPlaybackEnabled(shuffle, false);
        setPlaybackEnabled(repeat, false);

        quickPanel = new LinearLayout(overlayContext);
        quickPanel.setOrientation(LinearLayout.VERTICAL);
        quickPanel.setVisibility(View.GONE);
        bubble.addView(quickPanel);
        LinearLayout firstRow = new LinearLayout(overlayContext);
        LinearLayout secondRow = new LinearLayout(overlayContext);
        quickPanel.addView(firstRow);
        quickPanel.addView(secondRow);
        addShortcut(firstRow, "Kalender", () -> openCalendar());
        addShortcut(firstRow, "Home", () -> launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)));
        addShortcut(firstRow, "Senter", this::toggleTorch);
        addShortcut(secondRow, "Daya", () -> runSystemAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG));
        addShortcut(secondRow, "Kunci", () -> runSystemAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN));
        addShortcut(secondRow, "Screenshot", () -> runSystemAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT));

        bubbleParams = new WindowManager.LayoutParams(dp(246), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.LEFT;
    }

    private void addShortcut(LinearLayout row, String label, Runnable action) {
        Button button = newMusicButton(label, label);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setMaxLines(1);
        row.addView(button, new LinearLayout.LayoutParams(0, dp(44), 1));
        button.setOnClickListener(view -> {
            quickOpen = false;
            showPanel(0);
            action.run();
        });
    }

    private void openCalendar() {
        launch(new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_CALENDAR)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private void launch(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException error) {
            Toast.makeText(this, "Aksi ini tidak tersedia.", Toast.LENGTH_SHORT).show();
        }
    }

    private void runSystemAction(int action) {
        boolean needsAndroid9 = action == android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                || action == android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT;
        if (needsAndroid9 && Build.VERSION.SDK_INT < 28) {
            Toast.makeText(this, "Aksi ini memerlukan Android 9.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!QuickActionsService.isConnected()) {
            Toast.makeText(this, "Aktifkan Andi · Tombol cepat di Aksesibilitas.", Toast.LENGTH_LONG).show();
            launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        if (action == android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) {
            capturing = true;
            updateTuckedState();
            main.postDelayed(() -> {
                if (!QuickActionsService.run(action)) showUnavailable();
                main.postDelayed(() -> {
                    capturing = false;
                    updateTuckedState();
                }, 900);
            }, 250);
        } else if (!QuickActionsService.run(action)) {
            showUnavailable();
        }
    }

    private void showUnavailable() {
        Toast.makeText(this, "Aksi ini tidak tersedia di layar sekarang.", Toast.LENGTH_SHORT).show();
    }

    private final Map<String, Boolean> torchStates = new LinkedHashMap<>();
    private android.hardware.camera2.CameraManager torchManager;
    private final android.hardware.camera2.CameraManager.TorchCallback torchStatus =
            new android.hardware.camera2.CameraManager.TorchCallback() {
                @Override public void onTorchModeChanged(String id, boolean enabled) {
                    torchStates.put(id, enabled);
                }
            };

    private void toggleTorch() {
        android.hardware.camera2.CameraManager camera = torchManager;
        if (camera == null) {
            showUnavailable();
            return;
        }
        try {
            for (String id : camera.getCameraIdList()) {
                android.hardware.camera2.CameraCharacteristics info = camera.getCameraCharacteristics(id);
                if (!Boolean.TRUE.equals(info.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE))) continue;
                if (info.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                        != android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) continue;
                boolean enabled = !Boolean.TRUE.equals(torchStates.get(id));
                camera.setTorchMode(id, enabled);
                torchStates.put(id, enabled);
                return;
            }
            showUnavailable();
        } catch (android.hardware.camera2.CameraAccessException | RuntimeException error) {
            showUnavailable();
        }
    }

    private void showReminder(String title) {
        if (title == null || bubble == null) return;
        quickOpen = false;
        main.removeCallbacks(expireAlert);
        reminderShowing = true;
        updateTuckedState();
        main.removeCallbacks(expireAlert);
        pendingAlerts.addFirst(new Alert(null, "Andi", "Pengingat", title, null));
        advanceAlert();
        main.removeCallbacks(endReminder);
        main.postDelayed(endReminder, REMINDER_DISPLAY_MS);
    }

    private void endReminder() {
        reminderShowing = false;
        if (temporaryReminder) {
            stopSelf();
            return;
        }
        if (currentAlert != null && "Andi".equals(currentAlert.source) && "Pengingat".equals(currentAlert.sender)) {
            dismissCurrentAlert();
        }
        updateTuckedState();
    }

    void updateMusicModes(boolean shuffleAvailable, boolean repeatAvailable, int shuffleMode, int repeatMode) {
        if (shuffle == null || repeat == null) return;
        setPlaybackEnabled(shuffle, shuffleAvailable);
        setPlaybackEnabled(repeat, repeatAvailable);
        shuffle.setText(shuffleMode > 0 ? "Acak ✓" : "Acak");
        repeat.setText(repeatMode == 1 ? "Ulang 1" : repeatMode > 1 ? "Ulang ✓" : "Ulang");
    }

    private int dp(int value) {
        return Math.round(value * overlayContext.getResources().getDisplayMetrics().density);
    }

    private Button newMusicButton(String icon, String label) {
        Button button = new Button(overlayContext, null, android.R.attr.borderlessButtonStyle);
        button.setText(icon);
        button.setTextSize(16);
        button.setTextColor(0xffffe5c7);
        button.setContentDescription(label);
        button.setPadding(0, 0, 0, 0);
        button.setMinimumWidth(0);
        button.setMinWidth(0);
        return button;
    }

    private Button addPlaybackButton(LinearLayout row, String icon, String label, long action) {
        Button button = newMusicButton(icon, label);
        button.setOnClickListener(view -> PhoneEvents.control(action));
        row.addView(button, new LinearLayout.LayoutParams(0, dp(38), 1));
        return button;
    }

    private void setPlaybackEnabled(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.4f);
    }

    private TextView newBubbleText(int lines) {
        TextView view = new TextView(overlayContext);
        view.setTextColor(0xffffe5c7);
        view.setTextSize(14);
        view.setPadding(0, dp(7), 0, dp(7));
        view.setMaxLines(lines);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    void countQuietNotification(String packageName, String source) {
        QuietCount count = quietCounts.computeIfAbsent(packageName, key -> new QuietCount(source));
        if (count.count < 9999) count.count++;
    }

    private void showQuietSummary() {
        if (quietCounts.isEmpty()) return;
        StringBuilder message = new StringBuilder();
        int remaining = quietCounts.size();
        for (QuietCount count : quietCounts.values()) {
            if (quietCounts.size() - remaining == 3) break;
            if (message.length() > 0) message.append(" · ");
            message.append(count.count).append(" ").append(count.label);
            remaining--;
        }
        if (remaining > 0) message.append(" · +").append(remaining).append(" aplikasi");
        quietCounts.clear();
        showAlert(null, "Andi", "Selama Mode Tenang", message.toString(), null);
    }

    private void updateTuckedState() {
        if (pet == null) return;
        boolean automatic = NotificationPreferences.get(this).getBoolean(NotificationPreferences.AUTO_TUCK, false);
        boolean next = capturing || (!reminderShowing && (focusEnd != 0 || (automatic && keyboardVisible)));
        if (tucked == next) return;
        tucked = next;
        params.alpha = tucked ? 0f : 1f;
        if (tucked) {
            petTouch.cancelHold();
            petTouch.cancelTap();
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        try {
            windows.updateViewLayout(pet, params);
        } catch (IllegalArgumentException | SecurityException error) {
            Log.w("AndiOverlay", "Could not update Andi visibility", error);
        }
        if (tucked) {
            // Return any current card to the front of the queue until it can be read.
            main.removeCallbacks(expireAlert);
            if (currentAlert != null) pendingAlerts.addFirst(currentAlert);
            currentAlert = null;
            alertActive = false;
            showPanel(0);
        } else if (!pendingAlerts.isEmpty()) {
            advanceAlert();
        } else {
            showPanel(0);
        }
    }

    private void activateAlert() {
        Alert alert = currentAlert;
        if (alert == null || alert.destination == null || !alert.destination.isActivity()) return;
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                alert.destination.send(this, 0, null, null, null, null, options.toBundle());
            } else {
                alert.destination.send();
            }
            dismissCurrentAlert();
        } catch (PendingIntent.CanceledException | SecurityException error) {
            Toast.makeText(this, "Buka pesan dari panel notifikasi Android.", Toast.LENGTH_SHORT).show();
        }
    }

    private void dismissCurrentAlert() {
        main.removeCallbacks(expireAlert);
        advanceAlert();
    }

    void showAlert(Drawable appIcon, String source, String sender, String message, PendingIntent destination) {
        if (bubble == null || temporaryReminder) return;
        if (pendingAlerts.size() >= 8) pendingAlerts.removeFirst();
        pendingAlerts.addLast(new Alert(appIcon, source, sender, message, destination));
        if (!alertActive && !tucked) advanceAlert();
    }

    private void advanceAlert() {
        if (tucked) return;
        if (getSystemService(KeyguardManager.class).isKeyguardLocked()) {
            clearAlert();
            return;
        }
        Alert alert = pendingAlerts.pollFirst();
        if (alert == null) {
            clearAlert();
            return;
        }
        currentAlert = alert;
        alertView.setMaxLines(alert.destination == null && "Andi".equals(alert.source) ? 5 : 3);
        alertPanel.setClickable(alert.destination != null && alert.destination.isActivity());
        if (alert.icon == null) alertIcon.setImageResource(R.drawable.ic_notification);
        else alertIcon.setImageDrawable(alert.icon);
        alertIcon.setContentDescription(alert.source);
        android.text.SpannableStringBuilder preview = new android.text.SpannableStringBuilder();
        if (!alert.sender.isEmpty()) {
            preview.append(alert.sender);
            preview.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0, preview.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!alert.message.isEmpty()) preview.append("\n");
        }
        preview.append(alert.message);
        alertView.setText(preview);
        alertView.setVisibility(preview.length() == 0 ? View.GONE : View.VISIBLE);
        alertActive = true;
        pet.react();
        showPanel(1);
        if (!reminderShowing || !"Pengingat".equals(alert.sender) || !"Andi".equals(alert.source)) {
            main.postDelayed(expireAlert, 3500);
        }
    }

    void clearAlert() {
        main.removeCallbacks(expireAlert);
        pendingAlerts.clear();
        currentAlert = null;
        if (bubble == null || !alertActive) return;
        alertActive = false;
        alertView.setText("");
        alertIcon.setImageDrawable(null);
        showPanel(-1);
    }

    void showMusic(CharSequence title, CharSequence artist, Bitmap art,
                   boolean playing, long actions) {
        if (bubble == null) return;
        hasMusic = title != null;
        disc.setArtwork(art);
        disc.setPlaying(playing);
        playPause.setText(playing ? "Ⅱ" : "▶");
        playPause.setContentDescription(playing ? "Jeda" : "Putar");
        long toggle = playing ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
        setPlaybackEnabled(previousTrack, (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0);
        setPlaybackEnabled(playPause, (actions & (toggle | PlaybackState.ACTION_PLAY_PAUSE)) != 0);
        setPlaybackEnabled(nextTrack, (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0);
        if (title != null) {
            musicView.setText(title);
            artistView.setText(artist);
            artistView.setVisibility(android.text.TextUtils.isEmpty(artist) ? View.GONE : View.VISIBLE);
        }
        showPanel(0);
    }

    private void showPanel(int direction) {
        boolean showQuick = !alertActive && quickOpen && !temporaryReminder;
        boolean showMusic = !alertActive && !showQuick && !temporaryReminder && hasMusic && !musicHidden;
        quickPanel.setVisibility(showQuick ? View.VISIBLE : View.GONE);
        alertPanel.setVisibility(alertActive ? View.VISIBLE : View.GONE);
        musicPanel.setVisibility(showMusic ? View.VISIBLE : View.GONE);
        updateBubble();
        if (direction == 0) return;
        View active = alertActive ? alertPanel : showQuick ? quickPanel : showMusic ? musicPanel : null;
        for (View panel : new View[]{alertPanel, musicPanel, quickPanel}) {
            panel.animate().cancel();
            panel.setTranslationX(0);
            panel.setAlpha(1);
        }
        if (active != null) {
            active.setTranslationX(dp(16) * direction * (bubbleOnRight ? 1 : -1));
            active.setAlpha(0.4f);
            active.animate().translationX(0).alpha(1f).setDuration(220).start();
        }
    }

    private void updateBubble() {
        if (bubble == null) return;
        boolean visible = !tucked && (alertActive || (!temporaryReminder && (quickOpen || (hasMusic && !musicHidden))));
        if (!visible) {
            bubble.setVisibility(View.GONE);
            return;
        }
        Point bounds = movementBounds();
        int width = bounds.x + params.width;
        int height = bounds.y + params.height;
        int edge = dp(4);
        int gap = dp(2);
        int left = params.x - gap;
        int right = params.x + params.width + gap;
        int spaceLeft = left - edge;
        int spaceRight = width - right - edge;
        bubbleOnRight = spaceRight >= spaceLeft;
        int available = Math.max(spaceLeft, spaceRight);
        boolean compact = alertActive && alertView.getVisibility() == View.GONE;
        int preferredWidth = dp(compact ? 46 : quickOpen && !alertActive ? 234 : 246);
        boolean beside = available >= dp(compact ? 40 : quickOpen && !alertActive ? 208 : 112);
        if (!alertActive && !musicHidden) {
            // Keep controls usable in the smallest supported side layout.
            disc.setVisibility(beside && available < dp(148) ? View.GONE : View.VISIBLE);
        }
        bubbleParams.width = Math.min(preferredWidth, beside ? available : Math.max(1, width - 2 * edge));
        bubble.measure(View.MeasureSpec.makeMeasureSpec(bubbleParams.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
        int bubbleHeight = bubble.getMeasuredHeight();
        int centeredY = params.y + (params.height - bubbleHeight) / 2;
        if (beside) {
            bubbleParams.x = bubbleOnRight ? right : left - bubbleParams.width;
            bubbleParams.y = clamp(centeredY, height - bubbleHeight);
        } else {
            bubbleParams.x = clamp(params.x + (params.width - bubbleParams.width) / 2, width - bubbleParams.width);
            int above = params.y - bubbleHeight - gap;
            bubbleParams.y = clamp(above >= edge ? above : params.y + params.height + gap, height - bubbleHeight);
        }
        try {
            if (bubbleAdded) windows.updateViewLayout(bubble, bubbleParams);
            else {
                windows.addView(bubble, bubbleParams);
                bubbleAdded = true;
            }
            bubble.setVisibility(View.VISIBLE);
        } catch (WindowManager.BadTokenException | WindowManager.InvalidDisplayException
                 | IllegalArgumentException | SecurityException error) {
            bubble.setVisibility(View.GONE);
            Log.w("AndiOverlay", "Could not display the bubble", error);
        }
    }

    private Notification createNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Andi di layar",
                NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(channel);

        int pendingFlags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                pendingFlags);
        PendingIntent hide = PendingIntent.getService(this, 1,
                new Intent(this, FloatingPetService.class).setAction(ACTION_HIDE), pendingFlags);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Andi sedang melayang")
                .setContentText(focusEnd != 0 ? "Sesi fokus 25 menit sedang berjalan." : "Geser Andi, atau tahan untuk menyembunyikannya.")
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_notification, "Sembunyikan", hide).build())
                .setOngoing(true);
        if (focusEnd != 0) {
            PendingIntent cancel = PendingIntent.getService(this, 2,
                    new Intent(this, FloatingPetService.class).setAction(ACTION_CANCEL_FOCUS), pendingFlags);
            builder.addAction(new Notification.Action.Builder(R.drawable.ic_notification, "Akhiri fokus", cancel).build());
        }
        return builder.build();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (pet != null) pet.post(() -> {
            if (pet == null) return;
            restorePosition();
            windows.updateViewLayout(pet, params);
            updateBubble();
        });
    }

    private Point movementBounds() {
        int width;
        int height;
        if (Build.VERSION.SDK_INT >= 30) {
            WindowMetrics metrics = windows.getCurrentWindowMetrics();
            android.graphics.Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            width = metrics.getBounds().width() - insets.left - insets.right;
            height = metrics.getBounds().height() - insets.top - insets.bottom;
        } else {
            Point area = new Point();
            windows.getDefaultDisplay().getSize(area);
            width = area.x;
            height = area.y;
        }
        return new Point(Math.max(0, width - params.width), Math.max(0, height - params.height));
    }

    private void restorePosition() {
        Point bounds = movementBounds();
        params.x = Math.round(bounds.x * position.getFloat("x", 0.96f));
        params.y = Math.round(bounds.y * position.getFloat("y", 0.67f));
    }

    private void savePosition() {
        Point bounds = movementBounds();
        SharedPreferences.Editor edit = position.edit();
        if (bounds.x > 0) edit.putFloat("x", (float) params.x / bounds.x);
        if (bounds.y > 0) edit.putFloat("y", (float) params.y / bounds.y);
        edit.apply();
    }

    private int clamp(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }

    private void publishState(boolean active) {
        showing = active;
        sendBroadcast(new Intent(ACTION_STATE).setPackage(getPackageName()));
    }

    @Override
    public void onDestroy() {
        PhoneEvents.detach(this);
        if (torchManager != null) torchManager.unregisterTorchCallback(torchStatus);
        NotificationPreferences.get(this).unregisterOnSharedPreferenceChangeListener(previewSettingsChanged);
        if (petTouch != null) petTouch.cancelHold();
        unregisterReceiver(screenState);
        main.removeCallbacksAndMessages(null);
        focusEnd = 0;
        if (bubble != null && bubbleAdded) windows.removeViewImmediate(bubble);
        bubble = null;
        if (pet != null) {
            windows.removeViewImmediate(pet);
            pet = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        publishState(false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final class AlertSwipe implements View.OnTouchListener {
        private float startX;
        private Alert touched;

        @Override public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getRawX();
                    touched = currentAlert;
                    view.animate().cancel();
                    view.setAlpha(1f);
                    view.setTranslationX(0);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (touched == currentAlert) view.setTranslationX(event.getRawX() - startX);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (touched != currentAlert) return true;
                    float distance = event.getRawX() - startX;
                    view.setTranslationX(0);
                    if (Math.abs(distance) >= dp(36)) dismissCurrentAlert();
                    else if (Math.abs(distance) <= dp(12)) view.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (touched == currentAlert) view.setTranslationX(0);
                    return true;
                default:
                    return true;
            }
        }
    }

    private final class PetTouch implements View.OnTouchListener {
        private final int slop = ViewConfiguration.get(FloatingPetService.this).getScaledTouchSlop();
        private float downX;
        private float downY;
        private int initialX;
        private int initialY;
        private boolean tracking;
        private boolean dragging;
        private boolean held;
        private boolean tapPending;
        private final Runnable singleTap = () -> {
            tapPending = false;
            if (pet != null) pet.performClick();
        };

        private void finishTap() {
            if (tapPending) {
                main.removeCallbacks(singleTap);
                tapPending = false;
                if (!temporaryReminder) {
                    quickOpen = false;
                    if (hasMusic) {
                        musicHidden = !musicHidden;
                        showPanel(musicHidden ? 0 : 1);
                    } else {
                        showPanel(0);
                        Toast.makeText(FloatingPetService.this, "Belum ada musik aktif.", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                tapPending = true;
                main.postDelayed(singleTap, ViewConfiguration.getDoubleTapTimeout());
            }
        }
        private final Runnable hold = () -> {
            if (pet != null && tracking && !dragging) {
                held = true;
                cancelTap();
                pet.performLongClick();
            }
        };

        private void cancelTap() {
            main.removeCallbacks(singleTap);
            tapPending = false;
        }

        private void cancelHold() {
            main.removeCallbacks(hold);
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (pet == null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    initialX = params.x;
                    initialY = params.y;
                    tracking = true;
                    dragging = false;
                    held = false;
                    if (tapPending) {
                        main.removeCallbacks(singleTap);
                        main.postDelayed(singleTap, ViewConfiguration.getDoubleTapTimeout());
                    }
                    cancelHold();
                    main.postDelayed(hold, 3000);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!tracking || held) break;
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (!dragging && dx * dx + dy * dy > slop * slop) {
                        cancelHold();
                        cancelTap();
                        dragging = true;
                        pet.setDragging(true);
                    }
                    if (dragging) {
                        Point bounds = movementBounds();
                        params.x = clamp(initialX + Math.round(dx), bounds.x);
                        params.y = clamp(initialY + Math.round(dy), bounds.y);
                        windows.updateViewLayout(view, params);
                        updateBubble();
                    }
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    cancelHold();
                    if (dragging) {
                        savePosition();
                        pet.setDragging(false);
                    } else if (tracking && !held && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        finishTap();
                    }
                    tracking = false;
                    dragging = false;
                    break;
                default:
                    break;
            }
            return true;
        }
    }
}
