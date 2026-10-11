/*
 * Copyright 2026 the Cronbell authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.steelreed.cronbell.spring.internal;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One execution of a monitored job, from {@code start} to {@code success} or
 * {@code fail}.
 */
public final class Run {

	private final MonitoredCheck check;

	private final String rid;

	private final long startNanos;

	private volatile boolean started;

	private volatile boolean skipped;

	Run(MonitoredCheck check, long startNanos) {
		this.check = check;
		this.rid = randomUuid();
		this.startNanos = startNanos;
	}

	public MonitoredCheck check() {
		return this.check;
	}

	public String rid() {
		return this.rid;
	}

	long startNanos() {
		return this.startNanos;
	}

	boolean started() {
		return this.started;
	}

	void markStarted() {
		this.started = true;
	}

	/**
	 * Whether the run did not happen because another node held its lock.
	 * @return {@code true} if skipped
	 */
	public boolean skipped() {
		return this.skipped;
	}

	/**
	 * Marks the run as skipped: nothing is reported for it.
	 */
	public void skip() {
		this.skipped = true;
	}

	/** A version 4 UUID without {@code SecureRandom}, which may block on some hosts. */
	private static String randomUuid() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		long most = (random.nextLong() & ~0xF000L) | 0x4000L;
		long least = (random.nextLong() & ~(0xC000000000000000L)) | 0x8000000000000000L;
		return new UUID(most, least).toString();
	}

}
