package id.vanderleinz.lumo;

import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LyricsPanel {
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final String LOADING = "Mencari lirik…";
    private final Handler main;
    private final ScrollView scroll;
    private final TextView text;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable refresh = this::refresh;
    private Future<?> request;
    private int generation;
    private String title = "";
    private String artist = "";
    private PlaybackState state;
    private long duration;
    private boolean enabled;
    private boolean visible;
    private boolean requested;
    private Lyrics current;

    LyricsPanel(Handler main, ScrollView scroll, TextView text) {
        this.main = main;
        this.scroll = scroll;
        this.text = text;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        scroll.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (!enabled) reset();
        refresh();
    }

    boolean isEnabled() {
        return enabled;
    }

    void setCardVisible(boolean visible) {
        if (this.visible == visible) return;
        this.visible = visible;
        refresh();
    }

    void updateTrack(CharSequence title, CharSequence artist, long duration, PlaybackState state) {
        String nextTitle = title == null ? "" : title.toString();
        String nextArtist = artist == null ? "" : artist.toString();
        if (!this.title.equals(nextTitle) || !this.artist.equals(nextArtist)
                || this.duration != duration) {
            reset();
            this.title = nextTitle;
            this.artist = nextArtist;
            this.duration = duration;
        }
        this.state = state;
        refresh();
    }

    void close() {
        reset();
        worker.shutdownNow();
    }

    private void reset() {
        generation++;
        if (request != null) request.cancel(true);
        request = null;
        requested = false;
        current = null;
        main.removeCallbacks(refresh);
        text.setText("");
        scroll.scrollTo(0, 0);
    }

    private void refresh() {
        main.removeCallbacks(refresh);
        if (!enabled || !visible) return;
        if (title.isEmpty() || artist.isEmpty()) {
            setText("Lirik memerlukan judul dan nama artis.");
            return;
        }
        if (!requested) {
            requestLyrics();
            return;
        }
        if (current == null) return;
        if (current.lines.isEmpty() || state == null || state.getPosition() < 0) {
            setText(current.plain.isEmpty() ? "Lirik belum tersedia." : current.plain);
            return;
        }
        long position = state.getPosition();
        boolean playing = state.getState() == PlaybackState.STATE_PLAYING;
        if (playing && state.getLastPositionUpdateTime() > 0) {
            position += (long) (Math.max(0, SystemClock.elapsedRealtime() -
                    state.getLastPositionUpdateTime()) * state.getPlaybackSpeed());
        }
        int index = -1;
        while (index + 1 < current.lines.size() && current.lines.get(index + 1).time <= position) index++;
        int first = Math.max(0, index);
        String line = current.lines.get(first).text;
        if (first + 1 < current.lines.size()) line += "\n" + current.lines.get(first + 1).text;
        if (setText(line)) scroll.scrollTo(0, 0);
        if (playing) main.postDelayed(refresh, 300);
    }

    private boolean setText(String value) {
        if (android.text.TextUtils.equals(text.getText(), value)) return false;
        text.setText(value);
        return true;
    }

    private void requestLyrics() {
        requested = true;
        setText(LOADING);
        String track = title;
        String performer = artist;
        long expectedDuration = duration;
        int token = generation;
        request = worker.submit(() -> {
            Lyrics result = null;
            String failure = "Lirik belum tersedia.";
            try {
                result = lookup(track, performer, expectedDuration);
            } catch (Exception error) {
                failure = "Lirik gagal dimuat. Coba buka lagi.";
            }
            Lyrics found = result;
            String message = failure;
            main.post(() -> {
                if (token != generation) return;
                current = found;
                if (found == null) setText(message);
                else refresh();
            });
        });
    }

    private static Lyrics lookup(String title, String artist, long duration) throws Exception {
        Uri endpoint = Uri.parse("https://lrclib.net/api/search").buildUpon()
                .appendQueryParameter("track_name", title)
                .appendQueryParameter("artist_name", artist).build();
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint.toString()).openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Andi/0.4.1");
            if (connection.getResponseCode() == 404) return null;
            if (connection.getResponseCode() != 200) throw new IOException("Lyrics service unavailable");
            JSONArray results;
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int size;
                while ((size = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted() || bytes.size() + size > 1_000_000) {
                        throw new IOException("Lyrics response interrupted or too large");
                    }
                    bytes.write(buffer, 0, size);
                }
                results = new JSONArray(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
            JSONObject best = null;
            double distance = Double.MAX_VALUE;
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.optJSONObject(i);
                if (item == null || !normalize(title).equals(normalize(item.optString("trackName")))
                        || !normalize(artist).equals(normalize(item.optString("artistName")))) continue;
                String synced = value(item, "syncedLyrics");
                String plain = value(item, "plainLyrics");
                if (synced.isEmpty() && plain.isEmpty()) continue;
                double seconds = item.optDouble("duration", 0);
                double difference = duration > 0 && seconds > 0 ? Math.abs(seconds - duration / 1000.0) : 0;
                if (difference < distance || (difference == distance && best != null
                        && value(best, "syncedLyrics").isEmpty() && !synced.isEmpty())) {
                    best = item;
                    distance = difference;
                }
            }
            if (best == null) return null;
            String synced = value(best, "syncedLyrics");
            return new Lyrics(value(best, "plainLyrics"), distance <= 5 ? parse(synced) : new ArrayList<>());
        } finally {
            connection.disconnect();
        }
    }

    private static String value(JSONObject entry, String key) {
        return entry.isNull(key) ? "" : entry.optString(key, "");
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);
    }

    private static List<Line> parse(String lyrics) {
        List<Line> result = new ArrayList<>();
        for (String row : lyrics.split("\\r?\\n")) {
            Matcher marker = TIME.matcher(row);
            String line = marker.replaceAll("").trim();
            marker.reset();
            while (marker.find()) {
                int minutes = Integer.parseInt(marker.group(1));
                int seconds = Integer.parseInt(marker.group(2));
                if (seconds > 59) continue;
                String fraction = marker.group(3);
                int millis = fraction == null ? 0 : Integer.parseInt((fraction + "00").substring(0, 3));
                result.add(new Line(minutes * 60_000L + seconds * 1000L + millis, line));
            }
        }
        result.sort(Comparator.comparingLong(line -> line.time));
        return result;
    }

    private static final class Lyrics {
        final String plain;
        final List<Line> lines;

        Lyrics(String plain, List<Line> lines) {
            this.plain = plain;
            this.lines = lines;
        }
    }

    private static final class Line {
        final long time;
        final String text;

        Line(long time, String text) {
            this.time = time;
            this.text = text;
        }
    }
}
