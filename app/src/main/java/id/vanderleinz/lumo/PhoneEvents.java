package id.vanderleinz.lumo;

import android.app.KeyguardManager;
import android.app.Notification;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PhoneEvents extends NotificationListenerService {
    private static PhoneEvents connected;
    private static FloatingPetService overlay;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<MediaController> controllers = new ArrayList<>();
    private MediaSessionManager manager;
    private MediaController selected;

    private final MediaController.Callback changes = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata value) { publishMusic(); }
        @Override public void onQueueChanged(List<MediaSession.QueueItem> value) { publishMusic(); }
        @Override public void onPlaybackStateChanged(PlaybackState value) { publishMusic(); }
        @Override public void onSessionDestroyed() { publishMusic(); }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::replaceControllers;

    static void attach(FloatingPetService service) {
        overlay = service;
        if (connected != null) connected.publishMusic();
    }

    static void detach(FloatingPetService service) {
        if (overlay == service) overlay = null;
    }

    @Override public void onListenerConnected() {
        connected = this;
        manager = getSystemService(MediaSessionManager.class);
        ComponentName component = new ComponentName(this, PhoneEvents.class);
        try {
            manager.addOnActiveSessionsChangedListener(sessions, component, main);
            replaceControllers(manager.getActiveSessions(component));
        } catch (SecurityException ignored) {
            clearControllers();
        }
    }

    @Override public void onListenerDisconnected() { disconnect(); }
    @Override public void onDestroy() { disconnect(); super.onDestroy(); }

    private void disconnect() {
        if (manager != null) manager.removeOnActiveSessionsChangedListener(sessions);
        manager = null;
        clearControllers();
        if (connected == this) {
            connected = null;
            if (overlay != null) {
                overlay.clearAlert();
                overlay.showMusic(null, null, null, Collections.emptyList(), false, 0);
            }
        }
    }

    private void clearControllers() {
        for (MediaController controller : controllers) controller.unregisterCallback(changes);
        controllers.clear();
    }

    private void replaceControllers(List<MediaController> active) {
        clearControllers();
        if (active != null) {
            controllers.addAll(active);
            for (MediaController controller : controllers) controller.registerCallback(changes, main);
        }
        publishMusic();
    }

    @Override public void onNotificationPosted(StatusBarNotification posted) {
        if (overlay == null || posted.getPackageName().equals(getPackageName()) || posted.isOngoing()) return;
        Notification notification = posted.getNotification();
        if (Notification.CATEGORY_TRANSPORT.equals(notification.category)
                || notification.visibility == Notification.VISIBILITY_SECRET
                || (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0
                || getSystemService(KeyguardManager.class).isKeyguardLocked()) return;
        String source = "aplikasi lain";
        Drawable appIcon = null;
        PackageManager packages = getPackageManager();
        try {
            ApplicationInfo app = packages.getApplicationInfo(posted.getPackageName(), 0);
            source = packages.getApplicationLabel(app).toString();
            appIcon = packages.getApplicationIcon(app);
        } catch (PackageManager.NameNotFoundException ignored) { }
        if (appIcon == null && notification.getSmallIcon() != null) {
            try {
                appIcon = notification.getSmallIcon().loadDrawable(this);
            } catch (RuntimeException ignored) { }
        }
        if (notification.extras == null) {
            overlay.showAlert(appIcon, source, "");
            return;
        }
        CharSequence body = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        if (body == null || body.length() == 0) {
            body = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        }
        overlay.showAlert(appIcon, source, body == null ? "" : boundedPreview(body, 180));
    }

    private String boundedPreview(CharSequence text, int limit) {
        return text.subSequence(0, Math.min(text.length(), limit)).toString();
    }

    private void publishMusic() {
        if (overlay == null || connected != this) return;
        MediaController active = firstSession(PlaybackState.STATE_PLAYING);
        if (active == null && selected != null && controllers.contains(selected)
                && stateOf(selected) == PlaybackState.STATE_PAUSED) active = selected;
        selected = active != null ? active : firstSession(PlaybackState.STATE_PAUSED);
        if (selected != null) {
            MediaController controller = selected;
            PlaybackState state = controller.getPlaybackState();
            MediaMetadata metadata = controller.getMetadata();
            CharSequence title = metadata == null ? null : metadata.getDescription().getTitle();
            if (state != null && title != null && title.length() > 0) {
                publishTrack(controller, state, metadata.getDescription());
                return;
            }
        }
        overlay.showMusic(null, null, null, Collections.emptyList(), false, 0);
    }

    private MediaController firstSession(int targetState) {
        for (MediaController controller : controllers) {
            MediaMetadata metadata = controller.getMetadata();
            if (stateOf(controller) == targetState && metadata != null) {
                CharSequence title = metadata.getDescription().getTitle();
                if (title != null && title.length() != 0) return controller;
            }
        }
        return null;
    }

    private int stateOf(MediaController controller) {
        PlaybackState state = controller.getPlaybackState();
        return state == null ? PlaybackState.STATE_NONE : state.getState();
    }

    private void publishTrack(MediaController controller, PlaybackState state, MediaDescription description) {
        List<String> queue = new ArrayList<>();
        List<MediaSession.QueueItem> items = controller.getQueue();
        if (items != null) {
            for (MediaSession.QueueItem item : items) {
                CharSequence title = item.getDescription().getTitle();
                if (title != null && title.length() > 0) queue.add(title.toString());
                if (queue.size() == 20) break;
            }
        }
        overlay.showMusic(description.getTitle(), description.getSubtitle(), description.getIconBitmap(),
                queue, state.getState() == PlaybackState.STATE_PLAYING, state.getActions());
    }

    static void control(long action) {
        if (connected == null || connected.selected == null) return;
        MediaController controller = connected.selected;
        PlaybackState state = controller.getPlaybackState();
        if (state == null || (state.getActions() & action) == 0) return;
        MediaController.TransportControls controls = controller.getTransportControls();
        if (action == PlaybackState.ACTION_SKIP_TO_PREVIOUS) controls.skipToPrevious();
        else if (action == PlaybackState.ACTION_SKIP_TO_NEXT) controls.skipToNext();
        else {
            boolean playing = state.getState() == PlaybackState.STATE_PLAYING;
            long toggle = playing ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
            if ((state.getActions() & (toggle | PlaybackState.ACTION_PLAY_PAUSE)) == 0) return;
            if (playing) controls.pause();
            else controls.play();
        }
    }
}
