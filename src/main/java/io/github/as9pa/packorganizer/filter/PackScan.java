package io.github.as9pa.packorganizer.filter;

import io.github.as9pa.packorganizer.Log;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Queues one resolution scan per pack on a small background pool: cache hit, or scan then write-through.
 * The cache file is flushed by a task queued behind the scans, so a cold start writes it a few times at most.
 */
public final class PackScan {
    private static final Object LOCK = new Object();
    private static PackCache cache;
    private static final AtomicBoolean FLUSH_QUEUED = new AtomicBoolean();
    private static ExecutorService pool;

    private PackScan() {}

    /** Points the cache at {@code cacheDir/packs.json}. First call wins; later calls are ignored. */
    public static void configure(File cacheDir) {
        synchronized (LOCK) {
            if (cache == null) {
                cache = new PackCache(new File(cacheDir, "packs.json"));
            }
        }
    }

    public static void submit(final File pack, final ResolutionSink sink) {
        final PackCache c;
        synchronized (LOCK) {
            c = cache;
        }
        if (c == null) {
            throw new IllegalStateException("PackScan.configure was not called");
        }
        pool().execute(new Runnable() {
            @Override
            public void run() {
                PackResolution r;
                try {
                    String key = PackCache.key(pack);
                    r = c.lookup(key);
                    if (r == null) {
                        r = ResolutionScanner.scan(pack);
                        c.put(key, r);
                        scheduleFlush(c);
                    }
                } catch (RuntimeException e) {
                    Log.LOG.debug("packorganizer: scan failed for {}: {}", pack.getName(), e.toString());
                    r = PackResolution.UNKNOWN;
                }
                sink.acceptResolution(r);
            }
        });
    }

    private static void scheduleFlush(final PackCache c) {
        if (FLUSH_QUEUED.compareAndSet(false, true)) {
            pool().execute(new Runnable() {
                @Override
                public void run() {
                    FLUSH_QUEUED.set(false);
                    c.flush();
                }
            });
        }
    }

    /** clamp(cores - 1, 1, 4). */
    static int threadCount(int cores) {
        return Math.max(1, Math.min(4, cores - 1));
    }

    private static ExecutorService pool() {
        synchronized (LOCK) {
            if (pool == null) {
                int threads = threadCount(Runtime.getRuntime().availableProcessors());
                pool = Executors.newFixedThreadPool(threads, new ThreadFactory() {
                    private final AtomicInteger n = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "packorganizer-scan-" + n.incrementAndGet());
                        t.setDaemon(true);
                        t.setPriority(Thread.MIN_PRIORITY);
                        return t;
                    }
                });
            }
            return pool;
        }
    }

    /** Tests only. */
    static void reset() {
        synchronized (LOCK) {
            cache = null;
        }
        FLUSH_QUEUED.set(false);
    }
}
