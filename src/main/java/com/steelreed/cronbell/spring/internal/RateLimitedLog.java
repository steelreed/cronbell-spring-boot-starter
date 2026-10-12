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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Lets one message through per interval and counts the ones it holds back, so a
 * monitoring outage cannot flood the application's log.
 */
public final class RateLimitedLog {

	private final Consumer<String> sink;

	private final LongSupplier nanoClock;

	private final long intervalNanos;

	private final AtomicLong nextAllowed;

	private final AtomicLong suppressed = new AtomicLong();

	/**
	 * Creates a limiter.
	 * @param sink where messages go, for example {@code log::warn}
	 * @param nanoClock a monotonic clock in nanoseconds, such as {@code System::nanoTime}
	 * @param interval at most one message per interval
	 */
	public RateLimitedLog(Consumer<String> sink, LongSupplier nanoClock, Duration interval) {
		this.sink = sink;
		this.nanoClock = nanoClock;
		this.intervalNanos = interval.toNanos();
		this.nextAllowed = new AtomicLong(nanoClock.getAsLong());
	}

	/**
	 * Logs the message unless one was logged less than an interval ago.
	 * @param message builds the message, only called when it is logged
	 * @return {@code true} if the message was logged
	 */
	public boolean log(Supplier<String> message) {
		long now = this.nanoClock.getAsLong();
		long next = this.nextAllowed.get();
		if (now - next >= 0 && this.nextAllowed.compareAndSet(next, now + this.intervalNanos)) {
			long held = this.suppressed.getAndSet(0);
			String text = message.get();
			if (held > 0) {
				text += " (" + held + " similar " + ((held == 1) ? "message" : "messages") + " suppressed)";
			}
			this.sink.accept(text);
			return true;
		}
		this.suppressed.incrementAndGet();
		return false;
	}

}
