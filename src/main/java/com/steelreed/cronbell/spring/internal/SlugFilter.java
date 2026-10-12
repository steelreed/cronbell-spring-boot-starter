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

import java.util.List;
import java.util.regex.Pattern;

/**
 * The {@code cronbell.include} and {@code cronbell.exclude} glob patterns. {@code *}
 * matches any run of characters (dots included), {@code ?} one character; matching
 * ignores case. Exclude wins over include; no include pattern means everything.
 */
public final class SlugFilter {

	private final List<Pattern> include;

	private final List<Pattern> exclude;

	public SlugFilter(List<String> include, List<String> exclude) {
		this.include = compile(include);
		this.exclude = compile(exclude);
	}

	/**
	 * Whether a check with this slug is monitored.
	 * @param slug the slug without environment prefix
	 * @return {@code true} if monitored
	 */
	public boolean accepts(String slug) {
		for (Pattern pattern : this.exclude) {
			if (pattern.matcher(slug).matches()) {
				return false;
			}
		}
		if (this.include.isEmpty()) {
			return true;
		}
		for (Pattern pattern : this.include) {
			if (pattern.matcher(slug).matches()) {
				return true;
			}
		}
		return false;
	}

	private static List<Pattern> compile(List<String> globs) {
		if (globs == null) {
			return List.of();
		}
		return globs.stream().filter((glob) -> glob != null && !glob.isBlank()).map(SlugFilter::toPattern).toList();
	}

	static Pattern toPattern(String glob) {
		StringBuilder regex = new StringBuilder();
		StringBuilder literal = new StringBuilder();
		for (char c : glob.trim().toCharArray()) {
			if (c == '*' || c == '?') {
				if (literal.length() > 0) {
					regex.append(Pattern.quote(literal.toString()));
					literal.setLength(0);
				}
				regex.append((c == '*') ? ".*" : ".");
			}
			else {
				literal.append(c);
			}
		}
		if (literal.length() > 0) {
			regex.append(Pattern.quote(literal.toString()));
		}
		return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

}
