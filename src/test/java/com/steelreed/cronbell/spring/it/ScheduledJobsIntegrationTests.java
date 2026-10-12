package com.steelreed.cronbell.spring.it;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.steelreed.cronbell.spring.CronbellCheck;
import com.steelreed.cronbell.spring.internal.CronbellMonitor;
import com.steelreed.cronbell.spring.internal.actuator.CronbellInfoContributor;
import com.steelreed.cronbell.spring.test.Await;
import com.steelreed.cronbell.spring.test.FakeCronbellServer;
import com.steelreed.cronbell.spring.test.FakeCronbellServer.Request;
import com.steelreed.cronbell.spring.test.TestApps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ScheduledJobsIntegrationTests {

	private static final Duration WAIT = Duration.ofSeconds(10);

	private FakeCronbellServer server;

	private ConfigurableApplicationContext context;

	@BeforeEach
	void startServer() throws IOException {
		this.server = new FakeCronbellServer();
		Jobs.reset();
	}

	@AfterEach
	void stop() {
		if (this.context != null) {
			this.context.close();
		}
		this.server.close();
	}

	@Test
	void registersEveryScheduledMethodAtStartupWithTheApiKey() {
		this.context = TestApps.start(PoolApp.class, TestApps.NO_QUARTZ, "cronbell.url=" + this.server.url());
		Await.until(WAIT, () -> this.server.upserts("jobs.every-second").size() == 1
				&& this.server.upserts("jobs.fast").size() == 1 && this.server.upserts("jobs.delayed").size() == 1
				&& this.server.upserts("billing-close").size() == 1, "all checks registered");

		Request cron = this.server.upserts("jobs.every-second").get(0);
		assertThat(cron.authorization()).isEqualTo("Bearer cb_test_key");
		assertThat(cron.body())
			.isEqualTo("{\"name\":\"Jobs.everySecond\",\"cron\":\"*/1 * * * * *\",\"timeZone\":\"Asia/Ho_Chi_Minh\"}");
		assertThat(this.server.upserts("jobs.fast").get(0).body())
			.isEqualTo("{\"name\":\"Jobs.fast\",\"periodSeconds\":60}");
		assertThat(this.server.upserts("jobs.delayed").get(0).body())
			.isEqualTo("{\"name\":\"Jobs.delayed\",\"periodSeconds\":60}");
		assertThat(this.server.upserts("billing-close").get(0).body())
			.isEqualTo("{\"name\":\"Close the month\",\"periodSeconds\":3600,\"graceSeconds\":300}");
		assertThat(this.server.requests((r) -> r.path().contains("not-monitored"))).isEmpty();
	}

	@Test
	void reportsStartAndSuccessWithTheSameRunId() {
		this.context = TestApps.start(PoolApp.class, TestApps.NO_QUARTZ, "cronbell.url=" + this.server.url());
		Await.until(WAIT, () -> this.server.pings("jobs.fast", "").size() >= 3, "three successes");

		List<Request> starts = this.server.pings("jobs.fast", "start");
		List<Request> successes = this.server.pings("jobs.fast", "");
		assertThat(successes).allSatisfy((success) -> {
			assertThat(success.rid()).matches("[0-9a-f-]{36}");
			assertThat(success.body()).matches("Completed in \\d+ ms");
			assertThat(starts).extracting(Request::rid).contains(success.rid());
		});
		assertThat(successes).extracting(Request::rid).doesNotHaveDuplicates();
		assertThat(this.server.pings("jobs.fast", "fail")).isEmpty();
	}

	@Test
	void reportsFailureWithTheExceptionAndTheJobStillThrows() {
		this.context = TestApps.start(PoolApp.class, TestApps.NO_QUARTZ, "cronbell.url=" + this.server.url());
		Await.until(WAIT, () -> !this.server.pings("jobs.failing", "fail").isEmpty(), "a failure");

		Request fail = this.server.pings("jobs.failing", "fail").get(0);
		assertThat(fail.body()).startsWith("java.lang.IllegalStateException: boom\n\tat ");
		assertThat(fail.body()).contains("Jobs.failing").endsWith(" ms");
		assertThat(this.server.pings("jobs.failing", "start")).extracting(Request::rid).contains(fail.rid());
		// The scheduler's error handler still sees the job's own exception.
		Await.until(WAIT, () -> !PoolApp.errors.isEmpty(), "the error handler ran");
		assertThat(PoolApp.errors.get(0)).isInstanceOf(IllegalStateException.class).hasMessage("boom");
	}

	@Test
	void serverDownTheApplicationStartsAndJobsRun(CapturedOutput output) throws IOException {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}
		this.context = TestApps.start(PoolApp.class, TestApps.NO_QUARTZ, "cronbell.url=http://127.0.0.1:" + closedPort,
				"cronbell.timeout.connect=200ms");
		int before = Jobs.fastRuns.get();
		Await.until(WAIT, () -> Jobs.fastRuns.get() >= before + 10, "jobs keep running");
		Await.until(WAIT, () -> output.getOut().contains("retrying in the background"), "one warning");
		assertThat(output.getOut().split("cannot register checks", -1)).hasSizeLessThanOrEqualTo(2);
		assertThat(this.context.isRunning()).isTrue();
	}

	@Test
	void slowServerDoesNotDelayJobs() {
		this.server.delay(Duration.ofSeconds(3));
		long started = System.nanoTime();
		this.context = TestApps.start(PoolApp.class, TestApps.NO_QUARTZ, "cronbell.url=" + this.server.url(),
				"cronbell.queue-capacity=5");
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
		int before = Jobs.fastRuns.get();
		long windowStart = System.nanoTime();
		Await.until(WAIT, () -> Jobs.fastRuns.get() >= before + 15, "fifteen runs");
		// fixedRate = 100 ms: fifteen runs take about 1.5 s whatever the server does.
		assertThat(Duration.ofNanos(System.nanoTime() - windowStart)).isLessThan(Duration.ofMillis(2500));
		assertThat(this.context.getBean(CronbellMonitor.class).reporter().droppedPings()).isPositive();
	}

	@Test
	void worksWithSimpleAsyncTaskScheduler() {
		this.context = TestApps.start(AsyncApp.class, TestApps.NO_QUARTZ, "cronbell.url=" + this.server.url());
		Await.until(WAIT, () -> this.server.pings("jobs.fast", "").size() >= 2, "successes");
		Await.until(WAIT, () -> !this.server.pings("jobs.failing", "fail").isEmpty(), "a failure");
	}

	@Test
	void actuatorInfoShowsTheChecks() {
		this.context = TestApps.start(PoolApp.class, TestApps.NO_QUARTZ, "cronbell.url=" + this.server.url());
		Await.until(WAIT, () -> this.server.pings("jobs.fast", "").size() >= 1, "a success");
		Info.Builder builder = new Info.Builder();
		this.context.getBean(CronbellInfoContributor.class).contribute(builder);
		assertThat(builder.build().getDetails().get("cronbell")).asString()
			.contains("checks=5")
			.contains("url=" + this.server.url())
			.doesNotContain("cb_test_key");
	}

	@Test
	void offWithoutUrlAndSaysWhyOnce(CapturedOutput output) {
		this.context = TestApps.start(PoolApp.class, TestApps.NO_QUARTZ);
		assertThat(this.context.getBeansOfType(CronbellMonitor.class)).isEmpty();
		assertThat(output.getOut()).containsOnlyOnce("Cronbell monitoring is off: cronbell.url is not set.");
		int before = Jobs.fastRuns.get();
		Await.until(WAIT, () -> Jobs.fastRuns.get() > before, "jobs run");
	}

	@Test
	void offWhenDisabled(CapturedOutput output) {
		this.context = TestApps.start(PoolApp.class, TestApps.NO_QUARTZ, "cronbell.url=" + this.server.url(),
				"cronbell.enabled=false");
		assertThat(this.context.getBeansOfType(CronbellMonitor.class)).isEmpty();
		assertThat(output.getOut()).contains("Cronbell monitoring is off: cronbell.enabled is false.");
		assertThat(this.server.requests()).isEmpty();
	}

	@Test
	void environmentPrefixAndExclude() {
		this.context = TestApps.start(PoolApp.class, TestApps.NO_QUARTZ, "cronbell.url=" + this.server.url(),
				"cronbell.environment=staging", "cronbell.exclude=jobs.failing,jobs.delayed");
		// Checks are registered one by one on the reporter thread, in no fixed order.
		Await.until(WAIT, () -> this.server.upserts("staging.jobs.fast").size() == 1
				&& this.server.upserts("staging.billing-close").size() == 1, "prefixed checks");
		assertThat(this.server.requests((r) -> r.path().contains("failing") || r.path().contains("delayed"))).isEmpty();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@EnableScheduling
	@Import(Jobs.class)
	static class PoolApp {

		static final List<Throwable> errors = new CopyOnWriteArrayList<>();

		@Bean
		TaskScheduler taskScheduler() {
			errors.clear();
			ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
			scheduler.setPoolSize(4);
			scheduler.setErrorHandler(errors::add);
			return scheduler;
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@EnableScheduling
	@Import(Jobs.class)
	static class AsyncApp {

		@Bean
		TaskScheduler taskScheduler() {
			SimpleAsyncTaskScheduler scheduler = new SimpleAsyncTaskScheduler();
			if (Runtime.version().feature() >= 21) {
				scheduler.setVirtualThreads(true);
			}
			return scheduler;
		}

	}

	@org.springframework.stereotype.Component("jobs")
	static class Jobs {

		static final AtomicInteger fastRuns = new AtomicInteger();

		static void reset() {
			fastRuns.set(0);
		}

		@Scheduled(cron = "*/1 * * * * *", zone = "Asia/Ho_Chi_Minh")
		void everySecond() {
		}

		@Scheduled(fixedRate = 100)
		void fast() {
			fastRuns.incrementAndGet();
		}

		@Scheduled(fixedDelay = 300, initialDelay = 100)
		void delayed() {
		}

		@Scheduled(fixedRate = 300)
		void failing() {
			throw new IllegalStateException("boom");
		}

		@Scheduled(fixedRate = 3_600_000, initialDelay = 3_600_000)
		@CronbellCheck(value = "billing-close", name = "Close the month", grace = "5m")
		void closeMonth() {
		}

		@Scheduled(fixedRate = 3_600_000, initialDelay = 3_600_000)
		@CronbellCheck(enabled = false)
		void notMonitored() {
		}

	}

}
