package id.vanderleinz.lumo;

import android.app.Notification;
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
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Display;
import android.view.GestureDetector;
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

import java.util.Collections;
import java.util.List;

public final class FloatingPetService extends Service {
    static final String ACTION_STATE = "id.vanderleinz.andi.OVERLAY_STATE";
    private static final String ACTION_HIDE = "id.vanderleinz.andi.HIDE";
    private static final String CHANNEL = "floating_andi";
    private static final int NOTIFICATION_ID = 10;
    private static boolean showing;

    private Context overlayContext;
    private WindowManager windows;
    private WindowManager.LayoutParams params;
    private SharedPreferences position;
    private PetView pet;
    private LinearLayout bubble;
    private TextView alertView;
    private TextView musicView;
    private TextView queueView;
    private LinearLayout musicPanel;
    private CassetteView cassette;
    private boolean hasMusic;
    private boolean alertActive;
    private boolean queueOpen;
    private List<String> queue = Collections.emptyList();
    private WindowManager.LayoutParams bubbleParams;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable expireAlert = this::clearAlert;
    private final BroadcastReceiver screenState = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            clearAlert();
        }
    };

    static boolean isShowing() {
        return showing;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        position = getSharedPreferences("pet_position", MODE_PRIVATE);
        IntentFilter screenOff = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenState, screenOff, RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenState, screenOff);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_HIDE.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
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
            } else {
                stopSelf();
            }
        } catch (WindowManager.BadTokenException | WindowManager.InvalidDisplayException
                 | IllegalStateException | SecurityException exception) {
            Toast.makeText(this, "Andi belum bisa melayang. Periksa izin tampil di atas aplikasi lain.",
                    Toast.LENGTH_LONG).show();
            stopSelf();
        }
        return START_NOT_STICKY;
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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        restorePosition();

        PetView companion = new PetView(overlayContext);
        companion.setContentDescription("Andi. Ketuk untuk menyapa atau tahan untuk menyembunyikan.");
        companion.setOnClickListener(view -> companion.react());
        companion.setOnLongClickListener(view -> {
            stopSelf();
            return true;
        });
        companion.setOnTouchListener(new PetTouch());
        windows.addView(companion, params);
        pet = companion;
        createBubble();
        PhoneEvents.attach(this);
        publishState(true);
    }

    private void createBubble() {
        bubble = new LinearLayout(overlayContext);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(12), dp(3), dp(12), dp(3));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(16));
        background.setColor(0xff202c45);
        background.setStroke(dp(1), 0xffbc8969);
        bubble.setBackground(background);
        alertView = addBubbleText(3);
        musicPanel = new LinearLayout(overlayContext);
        musicPanel.setOrientation(LinearLayout.VERTICAL);
        musicPanel.setVisibility(View.GONE);
        bubble.addView(musicPanel);
        LinearLayout row = new LinearLayout(overlayContext);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        musicPanel.addView(row);
        cassette = new CassetteView(overlayContext);
        LinearLayout.LayoutParams cassetteParams = new LinearLayout.LayoutParams(dp(51), dp(43));
        cassetteParams.rightMargin = dp(8);
        row.addView(cassette, cassetteParams);
        musicView = newBubbleText(2);
        row.addView(musicView, new LinearLayout.LayoutParams(0, -2, 1));
        queueView = newBubbleText(5);
        musicPanel.addView(queueView);
        row.setOnClickListener(view -> {
            queueOpen = !queueOpen;
            refreshQueue();
            updateBubble();
        });
        bubbleParams = new WindowManager.LayoutParams(dp(208), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.LEFT;
    }

    private int dp(int value) {
        return Math.round(value * overlayContext.getResources().getDisplayMetrics().density);
    }

    private TextView addBubbleText(int lines) {
        TextView view = newBubbleText(lines);
        view.setVisibility(View.GONE);
        bubble.addView(view);
        return view;
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

    void showAlert(String header, String message) {
        if (bubble == null) return;
        alertView.setText(message.isEmpty() ? header : header + "\n" + message);
        boolean wasShowingAlert = alertActive;
        alertActive = true;
        pet.react();
        main.removeCallbacks(expireAlert);
        main.postDelayed(expireAlert, 5500);
        showPanel(wasShowingAlert ? 0 : 1);
    }

    void clearAlert() {
        main.removeCallbacks(expireAlert);
        if (bubble == null || !alertActive) return;
        alertActive = false;
        alertView.setText("");
        showPanel(-1);
    }

    void showMusic(CharSequence title, CharSequence artist, Bitmap art, List<String> tracks) {
        if (bubble == null) return;
        queue = tracks;
        hasMusic = title != null;
        cassette.setArtwork(art);
        if (title == null) queueOpen = false;
        else musicView.setText(title + (artist == null || artist.length() == 0 ? "" : "\n" + artist));
        refreshQueue();
        showPanel(0);
    }

    private void showPanel(int direction) {
        alertView.setVisibility(alertActive ? View.VISIBLE : View.GONE);
        musicPanel.setVisibility(!alertActive && hasMusic ? View.VISIBLE : View.GONE);
        updateBubble();
        View active = alertActive ? alertView : hasMusic ? musicPanel : null;
        if (direction != 0) {
            alertView.animate().cancel();
            musicPanel.animate().cancel();
            if (active != null) {
                active.setTranslationX(dp(22) * direction);
                active.setAlpha(0.4f);
                active.animate().translationX(0).alpha(1f).setDuration(220).start();
            }
        }
    }

    private void refreshQueue() {
        queueView.setText(queue.isEmpty() ? "Aplikasi musik tidak menyediakan antrean."
                : String.join("\n", queue.subList(0, Math.min(4, queue.size()))));
        queueView.setVisibility(queueOpen ? View.VISIBLE : View.GONE);
    }

    private void updateBubble() {
        if (bubble == null) return;
        boolean visible = alertActive || hasMusic;
        if (!visible) {
            if (bubble.isAttachedToWindow()) windows.removeViewImmediate(bubble);
            return;
        }
        Point bounds = movementBounds();
        int width = bounds.x + params.width;
        int height = bounds.y + params.height;
        bubbleParams.width = Math.min(dp(208), width);
        bubble.measure(View.MeasureSpec.makeMeasureSpec(bubbleParams.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
        int bubbleHeight = bubble.getMeasuredHeight();
        bubbleParams.x = clamp(params.x + (params.width - bubbleParams.width) / 2, width - bubbleParams.width);
        int above = params.y + dp(18) - bubbleHeight;
        bubbleParams.y = clamp(above >= 0 ? above : params.y + params.height - dp(17), height - bubbleHeight);
        if (bubble.isAttachedToWindow()) windows.updateViewLayout(bubble, bubbleParams);
        else {
            try {
                windows.addView(bubble, bubbleParams);
            } catch (WindowManager.BadTokenException | WindowManager.InvalidDisplayException | SecurityException denied) {
                stopSelf();
            }
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
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Andi sedang melayang")
                .setContentText("Geser Andi, atau tahan untuk menyembunyikannya.")
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_notification, "Sembunyikan", hide).build())
                .setOngoing(true)
                .build();
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
        unregisterReceiver(screenState);
        main.removeCallbacks(expireAlert);
        if (bubble != null && bubble.isAttachedToWindow()) windows.removeViewImmediate(bubble);
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

    private final class PetTouch implements View.OnTouchListener {
        private final int slop = ViewConfiguration.get(FloatingPetService.this).getScaledTouchSlop();
        private final GestureDetector gestures = new GestureDetector(FloatingPetService.this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent event) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapUp(MotionEvent event) {
                        return pet != null && pet.performClick();
                    }

                    @Override
                    public void onLongPress(MotionEvent event) {
                        if (pet != null) pet.performLongClick();
                    }
                });

        private float downX;
        private float downY;
        private int initialX;
        private int initialY;
        private boolean dragging;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    initialX = params.x;
                    initialY = params.y;
                    dragging = false;
                    gestures.onTouchEvent(event);
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (!dragging && dx * dx + dy * dy > slop * slop) {
                        MotionEvent cancel = MotionEvent.obtain(event);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        gestures.onTouchEvent(cancel);
                        cancel.recycle();
                        dragging = true;
                        pet.setDragging(true);
                    }
                    if (dragging) {
                        Point bounds = movementBounds();
                        params.x = clamp(initialX + Math.round(dx), bounds.x);
                        params.y = clamp(initialY + Math.round(dy), bounds.y);
                        windows.updateViewLayout(view, params);
                        updateBubble();
                    } else {
                        gestures.onTouchEvent(event);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        savePosition();
                        pet.setDragging(false);
                        dragging = false;
                    } else {
                        gestures.onTouchEvent(event);
                    }
                    break;
                default:
                    if (!dragging) gestures.onTouchEvent(event);
            }
            return true;
        }
    }
}
