package id.vanderleinz.lumo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class NotificationDeduplicator {
    private static final int MAX_NOTIFICATIONS = 128;
    private static final long SUMMARY_WINDOW_MS = 12_000;
    private final Map<String, Entry> seen = new LinkedHashMap<>();

    private static final class Entry {
        final String group;
        final boolean summary;
        final String sender;
        final String text;
        final long messageTime;
        final long arrived;

        Entry(String group, boolean summary, String sender, String text, long messageTime, long arrived) {
            this.group = group;
            this.summary = summary;
            this.sender = sender;
            this.text = text;
            this.messageTime = messageTime;
            this.arrived = arrived;
        }
    }

    boolean shouldShow(String key, String group, boolean summary, String sender, String text,
                       long messageTime, long now) {
        Entry previous = seen.get(key);
        if (previous != null && previous.text.equals(text) && previous.sender.equals(sender)
                && previous.messageTime == messageTime) return false;

        Entry incoming = new Entry(group, summary, sender, text, messageTime, now);
        boolean groupDuplicate = false;
        boolean newMessage = !summary && previous != null
                && previous.messageTime != 0 && messageTime != 0 && previous.messageTime != messageTime;
        if (!newMessage && group != null && !text.isEmpty()) {
            for (Map.Entry<String, Entry> existing : seen.entrySet()) {
                Entry candidate = existing.getValue();
                if (!key.equals(existing.getKey()) && summary != candidate.summary
                        && Objects.equals(group, candidate.group)
                        && now - candidate.arrived < SUMMARY_WINDOW_MS
                        && matchesSummary(incoming, candidate)) {
                    groupDuplicate = true;
                    break;
                }
            }
        }
        // Keep suppressed updates too so the same summary cannot reappear under the same key.
        seen.remove(key);
        seen.put(key, incoming);
        if (seen.size() > MAX_NOTIFICATIONS) seen.remove(seen.keySet().iterator().next());
        return !groupDuplicate;
    }

    private boolean matchesSummary(Entry first, Entry second) {
        Entry summary = first.summary ? first : second;
        Entry message = first.summary ? second : first;
        if (summary.messageTime != 0 && message.messageTime != 0) {
            if (summary.messageTime != message.messageTime) return false;
        }
        if (!summary.sender.isEmpty() && !message.sender.isEmpty()
                && !summary.sender.equals(message.sender)) return false;
        return summary.text.equals(message.text)
                || (!message.sender.isEmpty() && summary.text.equals(message.sender + ": " + message.text));
    }

    void remove(String key) {
        seen.remove(key);
    }

    void clear() {
        seen.clear();
    }
}
