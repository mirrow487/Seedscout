package com.seedscout.search;

import com.seedscout.SeedscoutClient;
import com.seedscout.util.Workers;
import com.seedscout.worldgen.WorldgenSettings;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Tries random seeds on several background threads until stopped. Each thread has its own
 * {@link SeedEvaluator}; the {@link WorldgenSettings} snapshot is shared (read-only) and closed when
 * the last thread has finished. The render thread only reads counters and copies of the result list.
 */
public final class SeedSearch {
	/** Best results kept (by score). */
	public static final int MAX_RESULTS = 100;

	private final WorldgenSettings settings;
	private final SearchPreset preset;
	private final int threads;
	private final ExecutorService executor;
	private final LongAdder tried = new LongAdder();
	private final AtomicInteger running = new AtomicInteger();
	private final List<SeedResult> results = new ArrayList<>();
	private final long startNanos = System.nanoTime();
	private volatile long stopNanos;
	private volatile boolean stopRequested;
	private volatile @Nullable Throwable error;
	private int resultsVersion;

	private SeedSearch(WorldgenSettings settings, SearchPreset preset, int threads) {
		this.settings = settings;
		this.preset = preset;
		this.threads = threads;
		this.executor = Executors.newFixedThreadPool(threads, Workers.factory("Seedscout Search"));
	}

	/** Starts searching. {@code settings} is owned by the search from now on. */
	public static SeedSearch start(WorldgenSettings settings, SearchPreset preset, int threads) {
		SeedSearch search = new SeedSearch(settings, preset, threads);
		search.running.set(threads);
		for (int i = 0; i < threads; i++) {
			search.executor.execute(search::work);
		}
		search.executor.shutdown();
		return search;
	}

	private void work() {
		try {
			SeedEvaluator evaluator = new SeedEvaluator(this.settings, this.preset);
			ThreadLocalRandom random = ThreadLocalRandom.current();
			while (!this.stopRequested) {
				long seed = random.nextLong();
				SeedResult result = evaluator.evaluate(seed);
				this.tried.increment();
				if (result != null) {
					add(result);
				}
			}
		} catch (Throwable t) {
			SeedscoutClient.LOGGER.error("Seed search thread failed", t);
			this.error = t;
			this.stopRequested = true;
		} finally {
			if (this.running.decrementAndGet() == 0) {
				this.stopNanos = System.nanoTime();
				this.settings.close();
			}
		}
	}

	private synchronized void add(SeedResult result) {
		this.results.add(result);
		this.results.sort(Comparator.comparingDouble(SeedResult::score).reversed());
		if (this.results.size() > MAX_RESULTS) {
			this.results.removeLast();
		}
		this.resultsVersion++;
	}

	/** Asks the threads to stop after their current seed. */
	public void stop() {
		this.stopRequested = true;
	}

	public boolean isRunning() {
		return this.running.get() > 0;
	}

	public boolean isStopping() {
		return this.stopRequested && isRunning();
	}

	public long tried() {
		return this.tried.sum();
	}

	/** Seeds per second since the start. */
	public double rate() {
		long end = isRunning() ? System.nanoTime() : this.stopNanos;
		double seconds = (end - this.startNanos) / 1e9;
		return seconds <= 0 ? 0 : tried() / seconds;
	}

	public int threads() {
		return this.threads;
	}

	public synchronized List<SeedResult> results() {
		return List.copyOf(this.results);
	}

	/** Changes whenever the result list changes (cheap check for the screen). */
	public synchronized int resultsVersion() {
		return this.resultsVersion;
	}

	public @Nullable Throwable error() {
		return this.error;
	}
}
