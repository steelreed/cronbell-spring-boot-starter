package com.steelreed.cronbell.spring.internal;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import com.steelreed.cronbell.spring.CronbellCheck;
import com.steelreed.cronbell.spring.CronbellProperties;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.OneTimeTask;
import org.springframework.scheduling.config.Task;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledTaskDiscoveryTests {

	private final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

	private final CronbellProperties properties = new CronbellProperties();

	private final Jobs jobs = new Jobs();

	ScheduledTaskDiscoveryTests() {
		this.beanFactory.registerSingleton("reportJobs", this.jobs);
	}

	@Test
	void cronKeepsExpressionAndZone() {
		CheckDefinition check = only(
				new CronTask(runnable("nightly"), new CronTrigger("0 0 2 * * *", ZoneId.of("Asia/Ho_Chi_Minh"))));
		assertThat(check.slug()).isEqualTo("report-jobs.nightly");
		assertThat(check.name()).isEqualTo("Jobs.nightly");
		assertThat(check.cron()).isEqualTo("0 0 2 * * *");
		assertThat(check.timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
		assertThat(check.periodSeconds()).isNull();
		assertThat(check.toJson())
			.isEqualTo("{\"name\":\"Jobs.nightly\",\"cron\":\"0 0 2 * * *\",\"timeZone\":\"Asia/Ho_Chi_Minh\"}");
	}

	@Test
	void cronWithoutZoneUsesTheJvmZone() {
		CheckDefinition check = only(new CronTask(runnable("hourly"), "0 0 * * * *"));
		assertThat(check.timeZone()).isEqualTo(ZoneId.systemDefault().getId());
	}

	@Test
	void fixedRateBecomesAPeriodAndInitialDelayIsIgnored() {
		CheckDefinition check = only(
				new FixedRateTask(runnable("everyFiveMinutes"), Duration.ofMinutes(5), Duration.ofHours(1)));
		assertThat(check.periodSeconds()).isEqualTo(300);
		assertThat(check.cron()).isNull();
		assertThat(check.timeZone()).isNull();
	}

	@Test
	void fixedDelayBecomesAPeriod() {
		CheckDefinition check = only(
				new FixedDelayTask(runnable("afterEachOther"), Duration.ofSeconds(90), Duration.ZERO));
		assertThat(check.periodSeconds()).isEqualTo(90);
	}

	@Test
	void periodsBelowTheServerMinimumAreRaisedToSixtySeconds() {
		CheckDefinition check = only(new FixedRateTask(runnable("fast"), Duration.ofSeconds(30), Duration.ZERO));
		assertThat(check.periodSeconds()).isEqualTo(60);
	}

	@Test
	void annotationOverridesSlugNameGraceAndMaxRuntime() {
		this.properties.setEnvironment("prod");
		CheckDefinition check = only(new CronTask(runnable("annotated"), "0 0 * * * *"));
		assertThat(check.slug()).isEqualTo("prod.nightly-backup");
		assertThat(check.name()).isEqualTo("Nightly backup");
		assertThat(check.graceSeconds()).isEqualTo(1800);
		assertThat(check.maxRuntimeSeconds()).isEqualTo(7200);
	}

	@Test
	void graceDefaultsToTheProperty() {
		this.properties.setGrace(Duration.ofMinutes(10));
		assertThat(only(new CronTask(runnable("hourly"), "0 0 * * * *")).graceSeconds()).isEqualTo(600);
	}

	@Test
	void disabledExcludedOneTimeAndMultiScheduleMethodsAreLeftOut() {
		this.properties.setExclude(List.of("*.excluded"));
		List<Task> tasks = List.of(new CronTask(runnable("disabled"), "0 0 * * * *"),
				new CronTask(runnable("excluded"), "0 0 * * * *"),
				new OneTimeTask(runnable("once"), Duration.ofSeconds(1)),
				new CronTask(runnable("twice"), "0 0 9 * * *"), new CronTask(runnable("twice"), "0 0 17 * * *"));
		assertThat(discovery().discover(tasks, Set.of())).isEmpty();
	}

	@Test
	void knownTasksAreSkipped() {
		Task task = new CronTask(runnable("hourly"), "0 0 * * * *");
		ScheduledKey key = ScheduledKey.of(this.jobs, method("hourly"));
		assertThat(discovery().discover(List.of(task), Set.of(key))).isEmpty();
	}

	@Test
	void shedLockMaxRuntimeHintAndGuard() {
		ScheduledTaskDiscovery.Discovered discovered = discovery()
			.discover(List.of(new CronTask(runnable("locked"), "0 * * * * *")), Set.of())
			.get(0);
		assertThat(discovered.check().lockGuarded()).isTrue();
		assertThat(discovered.check().definition().maxRuntimeSeconds()).isEqualTo(600);
	}

	private CheckDefinition only(Task task) {
		List<ScheduledTaskDiscovery.Discovered> found = discovery().discover(List.of(task), Set.of());
		assertThat(found).hasSize(1);
		return found.get(0).check().definition();
	}

	private ScheduledTaskDiscovery discovery() {
		return new ScheduledTaskDiscovery(this.beanFactory, this.properties);
	}

	private ScheduledMethodRunnable runnable(String name) {
		return new ScheduledMethodRunnable(this.jobs, method(name));
	}

	private static Method method(String name) {
		try {
			return Jobs.class.getDeclaredMethod(name);
		}
		catch (NoSuchMethodException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static class Jobs {

		@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")
		void nightly() {
		}

		@Scheduled(cron = "0 0 * * * *")
		void hourly() {
		}

		@Scheduled(fixedRate = 5, initialDelay = 60, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
		void everyFiveMinutes() {
		}

		void afterEachOther() {
		}

		void fast() {
		}

		@CronbellCheck(value = "nightly-backup", name = "Nightly backup", grace = "30m", maxRuntime = "PT2H")
		void annotated() {
		}

		@CronbellCheck(enabled = false)
		void disabled() {
		}

		void excluded() {
		}

		void once() {
		}

		void twice() {
		}

		@net.javacrumbs.shedlock.spring.annotation.SchedulerLock(name = "locked", lockAtMostFor = "10m")
		void locked() {
		}

	}

}
