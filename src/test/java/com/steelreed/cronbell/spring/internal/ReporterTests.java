package com.steelreed.cronbell.spring.internal;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.steelreed.cronbell.spring.test.Await;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReporterTests {

	private final CountDownLatch release = new CountDownLatch(1);

	private final List<String> pings = new CopyOnWriteArrayList<>();

	private final List<String> warnings = new CopyOnWriteArrayList<>();

	private final AtomicLong clock = new AtomicLong();

	private Reporter reporter;

	@AfterEach
	void close() {
		this.release.countDown();
		if (this.reporter != null) {
			this.reporter.close();
		}
	}

	/** A client whose pings block until released. */
	private CronbellClient blockingClient() {
		return new CronbellClient("http://127.0.0.1:9", "cb_x", Duration.ofSeconds(1), Duration.ofSeconds(1)) {

			@Override
			public URI upsert(CheckDefinition check) {
				return URI.create("http://127.0.0.1:9/ping/" + check.slug());
			}

			@Override
			public int ping(URI pingUrl, String kind, String rid, String body) throws InterruptedException {
				ReporterTests.this.release.await(10, TimeUnit.SECONDS);
				ReporterTests.this.pings.add(kind);
				return 200;
			}

		};
	}

	private MonitoredCheck registeredCheck() {
		MonitoredCheck check = new MonitoredCheck(
				new CheckDefinition("jobs.run", "Jobs.run", null, null, 60L, null, null), "test", false);
		this.reporter.register(check);
		Await.until(Duration.ofSeconds(5), () -> check.state() == MonitoredCheck.State.REGISTERED, "registered");
		return check;
	}

	@Test
	void fullQueueDropsPingsAndWarnsOncePerMinute() {
		this.reporter = new Reporter(blockingClient(), true, 3, this.clock::get, this.warnings::add);
		MonitoredCheck check = registeredCheck();

		// One ping is taken by the blocked reporter thread, three wait, the rest are
		// dropped.
		for (int i = 0; i < 20; i++) {
			this.reporter.start(this.reporter.begin(check));
			if (i == 0) {
				Await.until(Duration.ofSeconds(5), () -> this.reporter.droppedPings() == 0, "first ping taken");
				sleep(100);
			}
		}
		assertThat(this.reporter.droppedPings()).isEqualTo(16);
		assertThat(this.warnings).hasSize(1);
		assertThat(this.warnings.get(0)).contains("3 pings are waiting");

		this.clock.addAndGet(Duration.ofSeconds(61).toNanos());
		this.reporter.start(this.reporter.begin(check));
		assertThat(this.warnings).hasSize(2);
		assertThat(this.warnings.get(1)).contains("(15 similar messages suppressed)");

		this.release.countDown();
		Await.until(Duration.ofSeconds(5), () -> this.pings.size() == 4, "queued pings sent");
	}

	@Test
	void enqueueingIsFastEvenWhenTheServerHangs() {
		this.reporter = new Reporter(blockingClient(), true, 1000, System::nanoTime, this.warnings::add);
		MonitoredCheck check = registeredCheck();
		int runs = 500;
		long start = System.nanoTime();
		for (int i = 0; i < runs; i++) {
			Run run = this.reporter.begin(check);
			this.reporter.start(run);
			this.reporter.finish(run, (i % 2 == 0) ? null : new IllegalStateException("boom"));
		}
		long perRunNanos = (System.nanoTime() - start) / runs;
		assertThat(Duration.ofNanos(perRunNanos)).isLessThan(Duration.ofMillis(1));
	}

	@Test
	void unreachableServerDropsPingsAndRetriesRegistration() {
		this.reporter = new Reporter(
				new CronbellClient("http://127.0.0.1:9", "cb_x", Duration.ofMillis(200), Duration.ofMillis(200)), true,
				10, System::nanoTime, this.warnings::add);
		MonitoredCheck check = new MonitoredCheck(
				new CheckDefinition("jobs.run", "Jobs.run", null, null, 60L, null, null), "test", false);
		this.reporter.register(check);
		Await.until(Duration.ofSeconds(5), () -> this.reporter.lastError() != null, "registration failed");
		assertThat(check.state()).isEqualTo(MonitoredCheck.State.PENDING);
		assertThat(this.warnings).singleElement().asString().contains("retrying in the background");
		Run run = this.reporter.begin(check);
		this.reporter.start(run);
		this.reporter.finish(run, null);
		Await.until(Duration.ofSeconds(5), () -> this.reporter.droppedPings() == 2, "pings dropped");
	}

	@Test
	void backoffDoublesUpToFiveMinutes() {
		assertThat(Reporter.backoff(1)).isEqualTo(Duration.ofSeconds(5));
		assertThat(Reporter.backoff(2)).isEqualTo(Duration.ofSeconds(10));
		assertThat(Reporter.backoff(4)).isEqualTo(Duration.ofSeconds(40));
		assertThat(Reporter.backoff(30)).isEqualTo(Duration.ofMinutes(5));
	}

	@Test
	void skippedRunsReportNothing() {
		this.release.countDown();
		this.reporter = new Reporter(blockingClient(), true, 10, System::nanoTime, this.warnings::add);
		MonitoredCheck check = registeredCheck();
		Run run = this.reporter.begin(check);
		run.skip();
		this.reporter.finish(run, null);
		Run normal = this.reporter.begin(check);
		this.reporter.finish(normal, null);
		Await.until(Duration.ofSeconds(5), () -> this.pings.size() == 1, "one ping");
		sleep(100);
		assertThat(this.pings).containsExactly("");
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
