package io.github.as9pa.packorganizer.gui;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Pending preview loads, served newest request first. Rows on screen re-request every frame, so a request not renewed
 * for more than staleMs belongs to a row that scrolled away and is dropped; the row asks again if it comes back. An
 * item is queued at most once: requesting it again renews it and moves it to the front. Times are ms from one
 * non-decreasing clock. Not thread safe; {@link PreviewCache} holds its lock around every call.
 */
final class LoadQueue<T> {
    /** Head is the most recently requested item, tail the least. */
    private final ArrayDeque<T> order = new ArrayDeque<>();
    /** When each queued item was last requested. */
    private final Map<T, Long> requested = new HashMap<>();
    private final long staleMs;

    LoadQueue(long staleMs) {
        this.staleMs = staleMs;
    }

    /** Queues item, or renews it and moves it to the front if it is already queued. */
    void request(T item, long now) {
        if (requested.put(item, now) != null) {
            order.remove(item);
        }
        order.addFirst(item);
    }

    /** Drops the stale requests and takes the newest one left, or null when none is. */
    T next(long now) {
        while (!order.isEmpty() && now - requested.get(order.peekLast()) > staleMs) {
            requested.remove(order.pollLast());
        }
        T item = order.pollFirst();
        if (item != null) {
            requested.remove(item);
        }
        return item;
    }

    /** Forgets item if it is queued. */
    void remove(T item) {
        if (requested.remove(item) != null) {
            order.remove(item);
        }
    }

    void clear() {
        order.clear();
        requested.clear();
    }

    int size() {
        return order.size();
    }

    boolean isEmpty() {
        return order.isEmpty();
    }
}
