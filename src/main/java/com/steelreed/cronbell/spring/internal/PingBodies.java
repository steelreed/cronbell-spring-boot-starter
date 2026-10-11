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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Builds the bodies of {@code success} and {@code fail} pings, which the server stores as
 * the run's log excerpt (at most 10 KB there; the starter stays well under it).
 */
public final class PingBodies {

	/** Largest body the starter sends, in UTF-8 bytes. */
	public static final int MAX_BYTES = 4096;

	static final int TOP_FRAMES = 10;

	static final int CAUSE_FRAMES = 3;

	static final int MAX_CAUSES = 5;

	private static final String TRUNCATED = "\n[truncated]";

	private PingBodies() {
	}

	/**
	 * Body of a {@code success} ping.
	 * @param durationMillis the run time
	 * @return the body
	 */
	public static String success(long durationMillis) {
		return "Completed in " + durationMillis + " ms";
	}

	/**
	 * Body of a {@code fail} ping: exception class and message, the top stack frames and
	 * the causes, then the run time; truncated to {@link #MAX_BYTES}.
	 * @param error what the job threw
	 * @param durationMillis the run time
	 * @return the body
	 */
	public static String failure(Throwable error, long durationMillis) {
		Throwable top = unwrap(error);
		StringBuilder out = new StringBuilder(1024);
		Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		appendThrowable(out, top, TOP_FRAMES);
		seen.add(top);
		Throwable cause = top.getCause();
		int causes = 0;
		while (cause != null && seen.add(cause) && causes < MAX_CAUSES) {
			out.append("Caused by: ");
			appendThrowable(out, cause, CAUSE_FRAMES);
			cause = cause.getCause();
			causes++;
		}
		out.append("Failed after ").append(durationMillis).append(" ms");
		return truncate(out.toString(), MAX_BYTES);
	}

	/**
	 * The exception the job threw, without the reflection wrappers around it.
	 * @param error the reported exception
	 * @return the job's exception
	 */
	public static Throwable unwrap(Throwable error) {
		Throwable current = error;
		while ((current instanceof UndeclaredThrowableException || current instanceof InvocationTargetException)
				&& current.getCause() != null) {
			current = current.getCause();
		}
		return current;
	}

	private static void appendThrowable(StringBuilder out, Throwable error, int frames) {
		out.append(error.getClass().getName());
		String message = error.getMessage();
		if (message != null) {
			out.append(": ").append(message);
		}
		out.append('\n');
		StackTraceElement[] trace = error.getStackTrace();
		int shown = Math.min(frames, trace.length);
		for (int i = 0; i < shown; i++) {
			out.append("\tat ").append(trace[i]).append('\n');
		}
		if (trace.length > shown) {
			out.append("\t... ").append(trace.length - shown).append(" more\n");
		}
	}

	/**
	 * Cuts text to at most {@code maxBytes} UTF-8 bytes without splitting a character,
	 * marking the cut.
	 * @param text the text
	 * @param maxBytes the limit
	 * @return the text, or its truncated form
	 */
	static String truncate(String text, int maxBytes) {
		if (text.length() * 3 <= maxBytes || text.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
			return text;
		}
		int budget = maxBytes - TRUNCATED.length();
		CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder()
			.onMalformedInput(CodingErrorAction.REPLACE)
			.onUnmappableCharacter(CodingErrorAction.REPLACE);
		ByteBuffer bytes = ByteBuffer.allocate(budget);
		CharBuffer chars = CharBuffer.wrap(text);
		encoder.encode(chars, bytes, true);
		return text.substring(0, chars.position()) + TRUNCATED;
	}

}
