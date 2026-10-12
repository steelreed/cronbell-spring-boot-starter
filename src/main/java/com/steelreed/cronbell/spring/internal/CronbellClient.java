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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP calls to the Cronbell server with the JDK {@link HttpClient}: the management API
 * (DESIGN.md §6) and the ping API (§5). Calls block; the {@link Reporter} makes them on
 * its own thread.
 */
public class CronbellClient {

	static final String USER_AGENT = "cronbell-spring-boot-starter/" + version();

	private final HttpClient http;

	private final String baseUrl;

	private final String apiKey;

	private final Duration readTimeout;

	public CronbellClient(String baseUrl, String apiKey, Duration connectTimeout, Duration readTimeout) {
		this.baseUrl = stripTrailingSlash(baseUrl.trim());
		this.apiKey = apiKey.trim();
		this.readTimeout = readTimeout;
		this.http = HttpClient.newBuilder()
			.connectTimeout(connectTimeout)
			.version(HttpClient.Version.HTTP_1_1)
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
	}

	/**
	 * The server base URL, without trailing slash.
	 * @return the URL
	 */
	public String baseUrl() {
		return this.baseUrl;
	}

	/**
	 * Creates or updates a check ({@code PUT /api/v1/checks/{slug}}).
	 * @param check the definition
	 * @return the ping URL of the check
	 * @throws ApiException if the server answers with an error status
	 * @throws IOException if the server cannot be reached
	 * @throws InterruptedException if interrupted while waiting
	 */
	public URI upsert(CheckDefinition check) throws IOException, InterruptedException {
		HttpRequest request = management(check.slug()).header("Content-Type", "application/json")
			.PUT(HttpRequest.BodyPublishers.ofString(check.toJson(), StandardCharsets.UTF_8))
			.build();
		return pingUrlOf(send(request));
	}

	/**
	 * Reads an existing check ({@code GET /api/v1/checks/{slug}}).
	 * @param slug the slug
	 * @return the ping URL of the check
	 * @throws ApiException if the server answers with an error status, {@code 404} when
	 * the check does not exist
	 * @throws IOException if the server cannot be reached
	 * @throws InterruptedException if interrupted while waiting
	 */
	public URI get(String slug) throws IOException, InterruptedException {
		return pingUrlOf(send(management(slug).GET().build()));
	}

	/**
	 * Sends a ping.
	 * @param pingUrl the check's ping URL
	 * @param kind {@code start}, {@code fail} or empty for success
	 * @param rid the run id
	 * @param body the log excerpt, may be {@code null}
	 * @return the HTTP status
	 * @throws IOException if the server cannot be reached
	 * @throws InterruptedException if interrupted while waiting
	 */
	public int ping(URI pingUrl, String kind, String rid, String body) throws IOException, InterruptedException {
		String url = stripTrailingSlash(pingUrl.toString()) + (kind.isEmpty() ? "" : "/" + kind) + "?rid="
				+ URLEncoder.encode(rid, StandardCharsets.UTF_8);
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(this.readTimeout)
			.header("User-Agent", USER_AGENT)
			.header("Content-Type", "text/plain; charset=utf-8")
			.POST((body != null) ? HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
					: HttpRequest.BodyPublishers.noBody())
			.build();
		return this.http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	private HttpRequest.Builder management(String slug) {
		URI uri = URI.create(this.baseUrl + "/api/v1/checks/" + URLEncoder.encode(slug, StandardCharsets.UTF_8));
		return HttpRequest.newBuilder(uri)
			.timeout(this.readTimeout)
			.header("Authorization", "Bearer " + this.apiKey)
			.header("Accept", "application/json")
			.header("User-Agent", USER_AGENT);
	}

	private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
		HttpResponse<String> response = this.http.send(request,
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		int status = response.statusCode();
		if (status < 200 || status >= 300) {
			throw new ApiException(status, detailOf(response.body()));
		}
		return response;
	}

	private URI pingUrlOf(HttpResponse<String> response) throws IOException {
		Object parsed;
		try {
			parsed = Json.parse(response.body());
		}
		catch (IllegalArgumentException ex) {
			throw new IOException("Cronbell answered with something that is not JSON", ex);
		}
		if (parsed instanceof Map<?, ?> check) {
			if (check.get("pingUrl") instanceof String url && !url.isBlank()) {
				return URI.create(url);
			}
			if (check.get("pingKey") instanceof String key && !key.isBlank()) {
				return URI.create(this.baseUrl + "/ping/" + key);
			}
		}
		throw new IOException("Cronbell's answer has no ping URL");
	}

	private static String detailOf(String body) {
		if (body == null || body.isBlank()) {
			return "";
		}
		try {
			if (Json.parse(body) instanceof Map<?, ?> problem && problem.get("detail") instanceof String detail) {
				return detail;
			}
		}
		catch (IllegalArgumentException ex) {
			// not a problem document
		}
		return (body.length() > 200) ? body.substring(0, 200) : body;
	}

	private static String stripTrailingSlash(String url) {
		String result = url;
		while (result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}

	private static String version() {
		String version = CronbellClient.class.getPackage().getImplementationVersion();
		return (version != null) ? version : "dev";
	}

	/**
	 * The server answered with an error status.
	 */
	public static class ApiException extends IOException {

		private static final long serialVersionUID = 1L;

		private final int status;

		public ApiException(int status, String detail) {
			super("HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
			this.status = status;
		}

		public int status() {
			return this.status;
		}

	}

}
