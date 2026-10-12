package com.steelreed.cronbell.spring.internal;

import java.lang.reflect.UndeclaredThrowableException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PingBodiesTests {

	@Test
	void successNamesTheDuration() {
		assertThat(PingBodies.success(42)).isEqualTo("Completed in 42 ms");
	}

	@Test
	void failureHasClassMessageTopFramesCausesAndDuration() {
		Exception cause = new java.io.IOException("disk full");
		IllegalStateException error = new IllegalStateException("export failed", cause);
		String body = PingBodies.failure(error, 1234);
		assertThat(body).startsWith("java.lang.IllegalStateException: export failed\n\tat ");
		assertThat(body).contains("Caused by: java.io.IOException: disk full");
		assertThat(body).endsWith("Failed after 1234 ms");
		long topFrames = body.lines()
			.takeWhile((line) -> !line.startsWith("Caused by"))
			.filter((l) -> l.startsWith("\tat "))
			.count();
		assertThat(topFrames).isLessThanOrEqualTo(PingBodies.TOP_FRAMES);
	}

	@Test
	void reflectionWrappersAreRemoved() {
		Throwable wrapped = new UndeclaredThrowableException(new Exception("checked"));
		assertThat(PingBodies.failure(wrapped, 1)).startsWith("java.lang.Exception: checked");
	}

	@Test
	void causeLoopsEnd() {
		RuntimeException a = new RuntimeException("a");
		RuntimeException b = new RuntimeException("b", a);
		a.initCause(b);
		assertThat(PingBodies.failure(a, 1)).contains("Caused by: java.lang.RuntimeException: b");
	}

	@Test
	void hugeMessagesAreTruncatedWellUnderTenKilobytes() {
		String body = PingBodies.failure(new RuntimeException("é".repeat(20_000)), 5);
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		assertThat(bytes.length).isLessThanOrEqualTo(PingBodies.MAX_BYTES);
		assertThat(body).endsWith("[truncated]");
		assertThat(body).doesNotContain("�");
	}

	@Test
	void truncationNeverSplitsACharacter() {
		String text = "ab" + "😀".repeat(100);
		String cut = PingBodies.truncate(text, 31);
		assertThat(cut.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(31);
		assertThat(cut).endsWith("[truncated]");
		String kept = cut.substring(0, cut.indexOf('\n'));
		assertThat(kept.codePoints().allMatch((c) -> c == 'a' || c == 'b' || c == 0x1F600)).isTrue();
		assertThat(PingBodies.truncate("short", 31)).isEqualTo("short");
	}

}
