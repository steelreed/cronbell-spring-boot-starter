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

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import com.steelreed.cronbell.spring.internal.CronbellClient.ApiException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Registers checks and sends pings on one background thread, so a slow or unreachable
 * server never slows down or breaks a job.
 *
 * <ul>
 * <li>Job threads only enqueue: {@link #start}/{@link #finish} take microseconds.</li>
 * <li>At most {@code capacity} pings wait; when the queue is full, or the server cannot
 * be reached, pings are dropped and a WARN is logged at most once a minute.</li>
 * <li>Registration failures are retried in the background (5 s doubling to 5 min); a
 * {@code 4xx} answer is not retried.</li>
 * </ul>
 */
public class Reporter implements AutoCloseable {

	private static final Log logger = LogFactory.getLog(Reporter.class);

	static final Duration FIRST_RETRY = Duration.ofSeconds(5);

	static final Duration MAX_RETRY = Duration.ofMinutes(5);

	static final Duration LOG_INTERVAL = Duration.ofMinutes(1);

	private final CronbellClient client;

	private final boolean upsert;

	private final int capacity;

	private final LongSupplier nanoClock;

	private final ScheduledThreadPoolExecutor executor;

	private final AtomicInteger queued = new AtomicInteger();

	private final AtomicLong dropped = new AtomicLong();

	private final AtomicLong sent = new AtomicLong();

	private final AtomicBoolean keyRejected = new AtomicBoolean();

	private final RateLimitedLog pingWarnings;

	private final RateLimitedLog registrationWarnings;

	private volatile String lastError;

	private volatile Instant lastErrorAt;

	public Reporter(CronbellClient client, boolean upsert, int capacity) {
		this(client, upsert, capacity, System::nanoTime, logger::warn);
	}

	Reporter(CronbellClient client, boolean upsert, int capacity, LongSupplier nanoClock,
			java.util.function.Consumer<String> warnings) {
		this.client = client;
		this.upsert = upsert;
		this.capacity = Math.max(1, capacity);
		this.nanoClock = nanoClock;
		this.pingWarnings = new RateLimitedLog(warnings, nanoClock, LOG_INTERVAL);
		this.registrationWarnings = new RateLimitedLog(warnings, nanoClock, LOG_INTERVAL);
		this.executor = new ScheduledThreadPoolExecutor(1, daemonThreads());
		this.executor.setRemoveOnCancelPolicy(true);
		this.executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
		this.executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
	}

	// ---- registration -------------------------------------------------------------

	/**
	 * Registers a check in the background. Its pings are sent once it is registered.
	 * @param check the check
	 */
	public void register(MonitoredCheck check) {
		schedule(() -> registerNow(check), Duration.ZERO);
	}

	private void registerNow(MonitoredCheck check) {
		if (this.keyRejected.get() || check.state() == MonitoredCheck.State.REGISTERED) {
			return;
		}
		try {
			URI pingUrl = this.upsert ? this.client.upsert(check.definition()) : this.client.get(check.slug());
			check.registered(pingUrl);
			if (logger.isDebugEnabled()) {
				logger.debug("Cronbell: registered " + check.slug() + " (" + check.definition().describeSchedule()
						+ ") for " + check.source());
			}
		}
		catch (ApiException ex) {
			registrationRefused(check, ex);
		}
		catch (IOException | RuntimeException ex) {
			check.failedAttempts++;
			Duration delay = backoff(check.failedAttempts);
			recordError("registration of " + check.slug() + " failed: " + describe(ex));
			this.registrationWarnings.log(() -> "Cronbell: cannot register checks at " + this.client.baseUrl() + " ("
					+ describe(ex) + "). Jobs run as usual; retrying in the background.");
			schedule(() -> registerNow(check), delay);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private void registrationRefused(MonitoredCheck check, ApiException ex) {
		recordError("registration of " + check.slug() + " failed: " + ex.getMessage());
		if (ex.status() == 401 || ex.status() == 403) {
			check.unregistered();
			if (this.keyRejected.compareAndSet(false, true)) {
				logger.warn("Cronbell: the server refused the API key (" + ex.getMessage()
						+ "). No job is monitored; check cronbell.api-key.");
			}
			return;
		}
		if (ex.status() >= 500 || ex.status() == 429) {
			check.failedAttempts++;
			Duration delay = backoff(check.failedAttempts);
			this.registrationWarnings.log(() -> "Cronbell: cannot register checks at " + this.client.baseUrl() + " ("
					+ ex.getMessage() + "). Jobs run as usual; retrying in the background.");
			schedule(() -> registerNow(check), delay);
			return;
		}
		check.rejected();
		if (ex.status() == 404 && !this.upsert) {
			logger.warn("Cronbell: " + check.source() + " is not monitored: check '" + check.slug()
					+ "' does not exist on the server and cronbell.register is false.");
		}
		else {
			logger.warn("Cronbell: " + check.source() + " is not monitored: the server refused check '" + check.slug()
					+ "' (" + check.definition().describeSchedule() + "): " + ex.getMessage());
		}
	}

	static Duration backoff(int failedAttempts) {
		int exponent = Math.min(Math.max(failedAttempts - 1, 0), 16);
		Duration delay = FIRST_RETRY.multipliedBy(1L << exponent);
		return (delay.compareTo(MAX_RETRY) > 0) ? MAX_RETRY : delay;
	}

	// ---- runs ---------------------------------------------------------------------

	/**
	 * Begins a run. Sends nothing yet.
	 * @param check the job's check
	 * @return the run
	 */
	public Run begin(MonitoredCheck check) {
		return new Run(check, this.nanoClock.getAsLong());
	}

	/**
	 * Sends the {@code start} ping of a run.
	 * @param run the run
	 */
	public void start(Run run) {
		run.markStarted();
		enqueuePing(run.check(), "start", run.rid(), null);
	}

	/**
	 * Sends the {@code success} or {@code fail} ping of a run, unless it was skipped.
	 * @param run the run
	 * @param error what the job threw, or {@code null} on success
	 */
	public void finish(Run run, Throwable error) {
		if (run.skipped()) {
			return;
		}
		long millis = TimeUnit.NANOSECONDS.toMillis(this.nanoClock.getAsLong() - run.startNanos());
		if (error == null) {
			enqueuePing(run.check(), "", run.rid(), PingBodies.success(millis));
		}
		else {
			// The stack trace is formatted on the reporter thread, not the job's.
			enqueuePing(run.check(), "fail", run.rid(), new FailureBody(error, millis));
		}
	}

	private void enqueuePing(MonitoredCheck check, String kind, String rid, Object body) {
		if (check.state() == MonitoredCheck.State.REJECTED) {
			return;
		}
		if (this.queued.incrementAndGet() > this.capacity) {
			this.queued.decrementAndGet();
			this.dropped.incrementAndGet();
			this.pingWarnings.log(() -> "Cronbell: " + this.capacity
					+ " pings are waiting to be sent, dropping new ones. Jobs run as usual. Is " + this.client.baseUrl()
					+ " slow or down?");
			return;
		}
		try {
			this.executor.execute(() -> {
				this.queued.decrementAndGet();
				sendPing(check, kind, rid, body);
			});
		}
		catch (RejectedExecutionException ex) {
			this.queued.decrementAndGet();
		}
	}

	private void sendPing(MonitoredCheck check, String kind, String rid, Object body) {
		URI pingUrl = check.pingUrl();
		if (pingUrl == null) {
			this.dropped.incrementAndGet();
			return;
		}
		try {
			int status = this.client.ping(pingUrl, kind, rid, (body != null) ? body.toString() : null);
			if (status >= 200 && status < 300) {
				this.sent.incrementAndGet();
				return;
			}
			this.dropped.incrementAndGet();
			recordError("ping of " + check.slug() + " answered HTTP " + status);
			if (status == 404 && this.upsert) {
				// The check was deleted on the server: create it again.
				check.unregistered();
				register(check);
			}
			this.pingWarnings
				.log(() -> "Cronbell: a ping of " + check.slug() + " was answered with HTTP " + status + ".");
		}
		catch (IOException | RuntimeException ex) {
			this.dropped.incrementAndGet();
			recordError("ping of " + check.slug() + " failed: " + describe(ex));
			this.pingWarnings.log(() -> "Cronbell: cannot reach " + this.client.baseUrl() + " (" + describe(ex)
					+ "), dropping pings. Jobs run as usual.");
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	// ---- plumbing -----------------------------------------------------------------

	private void schedule(Runnable task, Duration delay) {
		try {
			this.executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (RejectedExecutionException ex) {
			// shutting down
		}
	}

	private void recordError(String error) {
		this.lastError = error;
		this.lastErrorAt = Instant.now();
	}

	private static String describe(Throwable ex) {
		String message = ex.getMessage();
		return ex.getClass().getSimpleName() + ((message != null && !message.isBlank()) ? ": " + message : "");
	}

	private static ThreadFactory daemonThreads() {
		return (runnable) -> {
			Thread thread = new Thread(runnable, "cronbell-reporter");
			thread.setDaemon(true);
			return thread;
		};
	}

	/**
	 * Pings dropped since start: queue full, server unreachable or check not registered.
	 */
	public long droppedPings() {
		return this.dropped.get();
	}

	/** Pings the server accepted. */
	public long sentPings() {
		return this.sent.get();
	}

	/** The last error, or {@code null}. */
	public String lastError() {
		return this.lastError;
	}

	/** When the last error happened, or {@code null}. */
	public Instant lastErrorAt() {
		return this.lastErrorAt;
	}

	/** The server base URL. */
	public String baseUrl() {
		return this.client.baseUrl();
	}

	/**
	 * Stops the reporter thread, giving queued pings a second to go out.
	 */
	@Override
	public void close() {
		this.executor.shutdown();
		try {
			if (!this.executor.awaitTermination(1, TimeUnit.SECONDS)) {
				this.executor.shutdownNow();
			}
		}
		catch (InterruptedException ex) {
			this.executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	/** Formats the failure body lazily, on the reporter thread. */
	private static final class FailureBody {

		private final Throwable error;

		private final long millis;

		FailureBody(Throwable error, long millis) {
			this.error = error;
			this.millis = millis;
		}

		@Override
		public String toString() {
			return PingBodies.failure(this.error, this.millis);
		}

	}

}
