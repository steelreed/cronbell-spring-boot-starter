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

package com.steelreed.cronbell.spring.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A local stand-in for the Cronbell server (JDK {@link HttpServer}): answers the check
 * upsert and the pings, and records every request.
 */
public final class FakeCronbellServer implements AutoCloseable {

	/** A request the server received. */
	public record Request(String method, String path, String query, String authorization, String body) {

		/** The {@code rid} query parameter, or {@code null}. */
		public String rid() {
			if (this.query == null) {
				return null;
			}
			for (String pair : this.query.split("&")) {
				if (pair.startsWith("rid=")) {
					return pair.substring(4);
				}
			}
			return null;
		}

	}

	private final HttpServer server;

	private final ExecutorService executor = Executors.newFixedThreadPool(4);

	private final List<Request> requests = new CopyOnWriteArrayList<>();

	private final Map<String, String> keysBySlug = new ConcurrentHashMap<>();

	private volatile Duration delay = Duration.ZERO;

	public FakeCronbellServer() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/", this::handle);
		this.server.setExecutor(this.executor);
		this.server.start();
	}

	public String url() {
		return "http://127.0.0.1:" + this.server.getAddress().getPort();
	}

	/** Makes every answer wait this long. */
	public void delay(Duration delay) {
		this.delay = delay;
	}

	public List<Request> requests() {
		return List.copyOf(this.requests);
	}

	public List<Request> requests(Predicate<Request> filter) {
		return this.requests.stream().filter(filter).collect(Collectors.toList());
	}

	/** The upserts of a slug. */
	public List<Request> upserts(String slug) {
		return requests((r) -> r.method().equals("PUT") && r.path().equals("/api/v1/checks/" + slug));
	}

	/**
	 * The pings of a slug of the given kind: {@code start}, {@code fail} or {@code ""}.
	 */
	public List<Request> pings(String slug, String kind) {
		String key = this.keysBySlug.get(slug);
		if (key == null) {
			return List.of();
		}
		String path = "/ping/" + key + (kind.isEmpty() ? "" : "/" + kind);
		return requests((r) -> r.method().equals("POST") && r.path().equals(path));
	}

	private void handle(HttpExchange exchange) throws IOException {
		try {
			byte[] bytes;
			try (InputStream in = exchange.getRequestBody()) {
				bytes = in.readAllBytes();
			}
			String path = exchange.getRequestURI().getPath();
			this.requests.add(new Request(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(),
					exchange.getRequestHeaders().getFirst("Authorization"), new String(bytes, StandardCharsets.UTF_8)));
			if (!this.delay.isZero()) {
				Thread.sleep(this.delay.toMillis());
			}
			if (path.startsWith("/api/v1/checks/")) {
				String slug = path.substring("/api/v1/checks/".length());
				if (!"Bearer cb_test_key".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
					send(exchange, 401, "{\"detail\":\"missing or unknown API key\"}");
					return;
				}
				if (exchange.getRequestMethod().equals("GET") && !this.keysBySlug.containsKey(slug)) {
					send(exchange, 404, "{\"detail\":\"no check " + slug + "\"}");
					return;
				}
				String key = this.keysBySlug.computeIfAbsent(slug, (s) -> UUID.randomUUID().toString());
				send(exchange, 200, "{\"slug\":\"" + slug + "\",\"pingKey\":\"" + key + "\",\"pingUrl\":\"" + url()
						+ "/ping/" + key + "\",\"status\":\"NEW\"}");
				return;
			}
			if (path.startsWith("/ping/")) {
				send(exchange, 200, "OK");
				return;
			}
			send(exchange, 404, "");
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		finally {
			exchange.close();
		}
	}

	private static void send(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, (bytes.length == 0) ? -1 : bytes.length);
		if (bytes.length > 0) {
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		}
	}

	@Override
	public void close() {
		this.server.stop(0);
		this.executor.shutdownNow();
	}

}
