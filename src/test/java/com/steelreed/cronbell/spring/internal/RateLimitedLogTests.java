package com.steelreed.cronbell.spring.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitedLogTests {

	@Test
	void oneMessagePerIntervalWithACountOfTheSuppressedOnes() {
		AtomicLong clock = new AtomicLong(1_000);
		List<String> logged = new ArrayList<>();
		RateLimitedLog log = new RateLimitedLog(logged::add, clock::get, Duration.ofMinutes(1));

		assertThat(log.log(() -> "down")).isTrue();
		for (int i = 0; i < 99; i++) {
			clock.addAndGet(Duration.ofMillis(500).toNanos());
			assertThat(log.log(() -> "down")).isFalse();
		}
		assertThat(logged).containsExactly("down");

		clock.addAndGet(Duration.ofSeconds(11).toNanos());
		assertThat(log.log(() -> "still down")).isTrue();
		assertThat(logged).containsExactly("down", "still down (99 similar messages suppressed)");
	}

	@Test
	void messageIsBuiltOnlyWhenLogged() {
		AtomicLong clock = new AtomicLong();
		RateLimitedLog log = new RateLimitedLog((m) -> {
		}, clock::get, Duration.ofMinutes(1));
		log.log(() -> "first");
		log.log(() -> {
			throw new AssertionError("must not be built");
		});
	}

}
