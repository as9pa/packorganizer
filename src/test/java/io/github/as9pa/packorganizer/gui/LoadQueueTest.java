package io.github.as9pa.packorganizer.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LoadQueueTest {
    private final LoadQueue<String> queue = new LoadQueue<>(500);

    @Test
    public void emptyQueueGivesNothing() {
        assertNull(queue.next(0));
    }

    @Test
    public void newestRequestComesFirst() {
        queue.request("a", 0);
        queue.request("b", 10);
        queue.request("c", 20);
        assertEquals("c", queue.next(30));
        assertEquals("b", queue.next(30));
        assertEquals("a", queue.next(30));
        assertNull(queue.next(30));
    }

    @Test
    public void requestOlderThanTheLimitIsDropped() {
        queue.request("a", 0);
        queue.request("b", 100);
        // At 601, "a" is 601 ms old and "b" 501 ms: both past 500.
        assertNull(queue.next(601));
        assertEquals(0, queue.size());
    }

    @Test
    public void requestAtExactlyTheLimitIsKept() {
        queue.request("a", 0);
        assertEquals("a", queue.next(500));
    }

    @Test
    public void staleRequestsAreSkippedForFreshOnes() {
        queue.request("scrolledPast", 0);
        queue.request("onScreen", 400);
        assertEquals("onScreen", queue.next(600));
        // "scrolledPast" went with the stale sweep, not left behind for later.
        assertNull(queue.next(600));
    }

    @Test
    public void reRequestRenewsAgainstStaleness() {
        queue.request("a", 0);
        queue.request("a", 400);
        queue.request("a", 800);
        assertEquals("a", queue.next(1200));
    }

    @Test
    public void reRequestMovesToTheFront() {
        queue.request("a", 0);
        queue.request("b", 10);
        queue.request("a", 20);
        assertEquals("a", queue.next(30));
        assertEquals("b", queue.next(30));
    }

    @Test
    public void itemIsNeverQueuedTwice() {
        queue.request("a", 0);
        queue.request("a", 10);
        queue.request("b", 20);
        queue.request("a", 30);
        assertEquals(2, queue.size());
        assertEquals("a", queue.next(40));
        assertEquals("b", queue.next(40));
        assertNull(queue.next(40));
    }

    @Test
    public void takenItemCanBeRequestedAgain() {
        queue.request("a", 0);
        assertEquals("a", queue.next(10));
        queue.request("a", 20);
        assertEquals("a", queue.next(30));
    }

    @Test
    public void droppedItemCanBeRequestedAgain() {
        queue.request("a", 0);
        assertNull(queue.next(1000));
        queue.request("a", 1000);
        assertEquals("a", queue.next(1010));
    }

    @Test
    public void removeForgetsTheItem() {
        queue.request("a", 0);
        queue.request("b", 10);
        queue.remove("b");
        queue.remove("missing");
        assertEquals(1, queue.size());
        assertEquals("a", queue.next(20));
        assertNull(queue.next(20));
    }

    @Test
    public void clearEmptiesTheQueue() {
        queue.request("a", 0);
        queue.request("b", 10);
        queue.clear();
        assertEquals(0, queue.size());
        assertNull(queue.next(20));
    }

    @Test
    public void isEmptyFollowsRequestsTakesAndStaleDrops() {
        assertTrue(queue.isEmpty());
        queue.request("a", 0);
        assertFalse(queue.isEmpty());
        assertEquals("a", queue.next(10));
        assertTrue(queue.isEmpty());
        queue.request("b", 0);
        assertNull(queue.next(501));
        assertTrue(queue.isEmpty());
    }
}
