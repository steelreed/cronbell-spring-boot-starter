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

package com.steelreed.cronbell.spring;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the Cronbell starter. Monitoring is on only when both {@code url} and
 * {@code api-key} are set.
 *
 * @author Nguyen Quang Vinh
 */
@ConfigurationProperties(prefix = "cronbell")
public class CronbellProperties {

	/**
	 * Base URL of the Cronbell server, for example https://cronbell.example.com.
	 */
	private String url;

	/**
	 * API key of the Cronbell project (cb_...). Keep it out of source control.
	 */
	private String apiKey;

	/**
	 * Whether to monitor the application's scheduled jobs.
	 */
	private boolean enabled = true;

	/**
	 * Prefix added to every slug, for example 'prod' or 'staging', so that environments
	 * do not share checks.
	 */
	private String environment;

	/**
	 * Glob patterns ('*', '?') of the slugs to monitor, matched without the environment
	 * prefix. Empty means all.
	 */
	private List<String> include = new ArrayList<>();

	/**
	 * Glob patterns ('*', '?') of the slugs not to monitor. Wins over include.
	 */
	private List<String> exclude = new ArrayList<>();

	/**
	 * How late a run may be before its check goes down. Unset uses the server's default.
	 */
	private Duration grace;

	/**
	 * Whether to create or update the checks on the server at startup. When false, the
	 * checks must already exist.
	 */
	private boolean register = true;

	/**
	 * Maximum number of pings waiting to be sent. Further pings are dropped.
	 */
	private int queueCapacity = 1000;

	private final Timeout timeout = new Timeout();

	public String getUrl() {
		return this.url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getEnvironment() {
		return this.environment;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	public List<String> getInclude() {
		return this.include;
	}

	public void setInclude(List<String> include) {
		this.include = include;
	}

	public List<String> getExclude() {
		return this.exclude;
	}

	public void setExclude(List<String> exclude) {
		this.exclude = exclude;
	}

	public Duration getGrace() {
		return this.grace;
	}

	public void setGrace(Duration grace) {
		this.grace = grace;
	}

	public boolean isRegister() {
		return this.register;
	}

	public void setRegister(boolean register) {
		this.register = register;
	}

	public int getQueueCapacity() {
		return this.queueCapacity;
	}

	public void setQueueCapacity(int queueCapacity) {
		this.queueCapacity = queueCapacity;
	}

	public Timeout getTimeout() {
		return this.timeout;
	}

	/**
	 * HTTP timeouts towards the Cronbell server.
	 */
	public static class Timeout {

		/**
		 * Connect timeout.
		 */
		private Duration connect = Duration.ofSeconds(5);

		/**
		 * Time to wait for a response.
		 */
		private Duration read = Duration.ofSeconds(10);

		public Duration getConnect() {
			return this.connect;
		}

		public void setConnect(Duration connect) {
			this.connect = connect;
		}

		public Duration getRead() {
			return this.read;
		}

		public void setRead(Duration read) {
			this.read = read;
		}

	}

}
