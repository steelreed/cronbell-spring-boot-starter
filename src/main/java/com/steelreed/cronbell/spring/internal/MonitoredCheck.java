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

import java.net.URI;

/**
 * A discovered job and what the server told the starter about it.
 */
public final class MonitoredCheck {

	/** Where a check is in its registration. */
	public enum State {

		/** Not registered yet, or registration is being retried. */
		PENDING,

		/** Registered: pings go to {@link MonitoredCheck#pingUrl()}. */
		REGISTERED,

		/** The server refused it; it is not monitored. */
		REJECTED

	}

	private final CheckDefinition definition;

	private final String source;

	private final boolean lockGuarded;

	private volatile URI pingUrl;

	private volatile State state = State.PENDING;

	/** Registration attempts that failed in a row; used only on the reporter thread. */
	int failedAttempts;

	/**
	 * Creates a check.
	 * @param definition what is sent to the server
	 * @param source where it came from, for logs: {@code @Scheduled Jobs.report()}
	 * @param lockGuarded whether runs may be skipped by ShedLock
	 */
	public MonitoredCheck(CheckDefinition definition, String source, boolean lockGuarded) {
		this.definition = definition;
		this.source = source;
		this.lockGuarded = lockGuarded;
	}

	public CheckDefinition definition() {
		return this.definition;
	}

	public String slug() {
		return this.definition.slug();
	}

	public String source() {
		return this.source;
	}

	public boolean lockGuarded() {
		return this.lockGuarded;
	}

	public URI pingUrl() {
		return this.pingUrl;
	}

	public State state() {
		return this.state;
	}

	void registered(URI pingUrl) {
		this.pingUrl = pingUrl;
		this.state = State.REGISTERED;
		this.failedAttempts = 0;
	}

	void rejected() {
		this.pingUrl = null;
		this.state = State.REJECTED;
	}

	void unregistered() {
		this.pingUrl = null;
		this.state = State.PENDING;
	}

	@Override
	public String toString() {
		return this.definition.slug();
	}

}
