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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The little JSON the starter needs, so it depends on neither Jackson 2 (Boot 3) nor
 * Jackson 3 (Boot 4).
 */
final class Json {

	private Json() {
	}

	/**
	 * Writes an object with string, number and boolean values; {@code null} values are
	 * left out.
	 */
	static String write(Map<String, ?> object) {
		StringBuilder out = new StringBuilder("{");
		for (Map.Entry<String, ?> entry : object.entrySet()) {
			Object value = entry.getValue();
			if (value == null) {
				continue;
			}
			if (out.length() > 1) {
				out.append(',');
			}
			string(out, entry.getKey());
			out.append(':');
			if (value instanceof Number || value instanceof Boolean) {
				out.append(value);
			}
			else {
				string(out, value.toString());
			}
		}
		return out.append('}').toString();
	}

	private static void string(StringBuilder out, String value) {
		out.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					}
					else {
						out.append(c);
					}
				}
			}
		}
		out.append('"');
	}

	/**
	 * Parses a JSON document into maps, lists, strings, numbers ({@code Long} or
	 * {@code Double}), booleans and {@code null}.
	 * @throws IllegalArgumentException if the text is not JSON
	 */
	static Object parse(String text) {
		Parser parser = new Parser(text);
		parser.skipWhitespace();
		Object value = parser.value();
		parser.skipWhitespace();
		if (parser.pos != text.length()) {
			throw parser.error("trailing characters");
		}
		return value;
	}

	private static final class Parser {

		private final String text;

		private int pos;

		Parser(String text) {
			this.text = text;
		}

		Object value() {
			if (this.pos >= this.text.length()) {
				throw error("unexpected end");
			}
			char c = this.text.charAt(this.pos);
			return switch (c) {
				case '{' -> object();
				case '[' -> array();
				case '"' -> string();
				case 't' -> literal("true", Boolean.TRUE);
				case 'f' -> literal("false", Boolean.FALSE);
				case 'n' -> literal("null", null);
				default -> number();
			};
		}

		private Map<String, Object> object() {
			Map<String, Object> result = new LinkedHashMap<>();
			this.pos++;
			skipWhitespace();
			if (peek() == '}') {
				this.pos++;
				return result;
			}
			while (true) {
				skipWhitespace();
				if (peek() != '"') {
					throw error("expected a key");
				}
				String key = string();
				skipWhitespace();
				expect(':');
				skipWhitespace();
				result.put(key, value());
				skipWhitespace();
				char c = next();
				if (c == '}') {
					return result;
				}
				if (c != ',') {
					throw error("expected ',' or '}'");
				}
			}
		}

		private List<Object> array() {
			List<Object> result = new ArrayList<>();
			this.pos++;
			skipWhitespace();
			if (peek() == ']') {
				this.pos++;
				return result;
			}
			while (true) {
				skipWhitespace();
				result.add(value());
				skipWhitespace();
				char c = next();
				if (c == ']') {
					return result;
				}
				if (c != ',') {
					throw error("expected ',' or ']'");
				}
			}
		}

		private String string() {
			expect('"');
			StringBuilder out = new StringBuilder();
			while (true) {
				char c = next();
				if (c == '"') {
					return out.toString();
				}
				if (c != '\\') {
					out.append(c);
					continue;
				}
				char escaped = next();
				switch (escaped) {
					case 'b' -> out.append('\b');
					case 'f' -> out.append('\f');
					case 'n' -> out.append('\n');
					case 'r' -> out.append('\r');
					case 't' -> out.append('\t');
					case 'u' -> {
						if (this.pos + 4 > this.text.length()) {
							throw error("bad unicode escape");
						}
						out.append((char) Integer.parseInt(this.text.substring(this.pos, this.pos + 4), 16));
						this.pos += 4;
					}
					default -> out.append(escaped);
				}
			}
		}

		private Object number() {
			int start = this.pos;
			while (this.pos < this.text.length() && "+-0123456789.eE".indexOf(this.text.charAt(this.pos)) >= 0) {
				this.pos++;
			}
			String number = this.text.substring(start, this.pos);
			if (number.isEmpty()) {
				throw error("unexpected character");
			}
			try {
				if (number.contains(".") || number.contains("e") || number.contains("E")) {
					return Double.parseDouble(number);
				}
				return Long.parseLong(number);
			}
			catch (NumberFormatException ex) {
				throw error("bad number " + number);
			}
		}

		private Object literal(String word, Object value) {
			if (!this.text.startsWith(word, this.pos)) {
				throw error("unexpected character");
			}
			this.pos += word.length();
			return value;
		}

		void skipWhitespace() {
			while (this.pos < this.text.length() && Character.isWhitespace(this.text.charAt(this.pos))) {
				this.pos++;
			}
		}

		private char peek() {
			if (this.pos >= this.text.length()) {
				throw error("unexpected end");
			}
			return this.text.charAt(this.pos);
		}

		private char next() {
			char c = peek();
			this.pos++;
			return c;
		}

		private void expect(char c) {
			if (next() != c) {
				throw error("expected '" + c + "'");
			}
		}

		IllegalArgumentException error(String message) {
			return new IllegalArgumentException("invalid JSON at " + this.pos + ": " + message);
		}

	}

}
