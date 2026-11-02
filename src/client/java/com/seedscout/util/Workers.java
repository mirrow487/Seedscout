package com.seedscout.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Background threads of Seedscout. Daemon threads with low priority, so the game always stays smooth. */
public final class Workers {
	private static final ExecutorService ANALYSIS = Executors.newCachedThreadPool(factory("Seedscout Analysis"));

	private Workers() {
	}

	/** For single-seed work (analysis, map). */
	public static ExecutorService analysis() {
		return ANALYSIS;
	}

	public static ThreadFactory factory(String name) {
		AtomicInteger counter = new AtomicInteger();
		return runnable -> {
			Thread thread = new Thread(runnable, name + " #" + counter.incrementAndGet());
			thread.setDaemon(true);
			thread.setPriority(Thread.MIN_PRIORITY);
			return thread;
		};
	}
}
