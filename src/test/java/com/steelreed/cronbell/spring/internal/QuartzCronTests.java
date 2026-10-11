package com.steelreed.cronbell.spring.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuartzCronTests {

	@Test
	void namesAndWildcardsStayAsTheyAre() {
		assertThat(QuartzMonitor.toSpringCron("0 0/5 * * * ?")).isEqualTo("0 0/5 * * * ?");
		assertThat(QuartzMonitor.toSpringCron("0 0 9 ? * MON-FRI")).isEqualTo("0 0 9 ? * MON-FRI");
	}

	@Test
	void numericDaysOfWeekShiftByOne() {
		// Quartz: 1 = Sunday, 2 = Monday; Spring: 0 = Sunday, 1 = Monday
		assertThat(QuartzMonitor.toSpringCron("0 0 12 ? * 2")).isEqualTo("0 0 12 ? * 1");
		assertThat(QuartzMonitor.toSpringCron("0 0 12 ? * 2-6")).isEqualTo("0 0 12 ? * 1-5");
		assertThat(QuartzMonitor.toSpringCron("0 0 12 ? * 1,7")).isEqualTo("0 0 12 ? * 0,6");
		assertThat(QuartzMonitor.toSpringCron("0 0 12 ? * 6#3")).isEqualTo("0 0 12 ? * 5#3");
		assertThat(QuartzMonitor.toSpringCron("0 0 12 ? * 6L")).isEqualTo("0 0 12 ? * 5L");
		assertThat(QuartzMonitor.toSpringCron("0 0 12 ? * 1/2")).isEqualTo("0 0 12 ? * 0/2");
	}

	@Test
	void yearFieldIsDroppedOnlyWhenItIsAWildcard() {
		assertThat(QuartzMonitor.toSpringCron("0 15 10 * * ? *")).isEqualTo("0 15 10 * * ?");
		assertThat(QuartzMonitor.toSpringCron("0 15 10 * * ? 2027")).isNull();
	}

}
