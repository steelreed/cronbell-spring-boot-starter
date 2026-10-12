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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The monitored checks, by slug and by the key the job runtime knows them by: the target
 * class and method of a {@code @Scheduled} task, the trigger of a Quartz job.
 */
public final class CheckRegistry {

	private static final Log logger = LogFactory.getLog(CheckRegistry.class);

	private final Map<Object, MonitoredCheck> byKey = new ConcurrentHashMap<>();

	private final Map<String, Object> keysBySlug = new ConcurrentHashMap<>();

	/**
	 * Adds a check unless its key is known already.
	 * @param key the runtime key
	 * @param check the check
	 * @return {@code true} if added; {@code false} if the key is known or another job
	 * already uses the slug
	 */
	public synchronized boolean add(Object key, MonitoredCheck check) {
		if (this.byKey.containsKey(key)) {
			return false;
		}
		Object existing = this.keysBySlug.putIfAbsent(check.slug(), key);
		if (existing != null) {
			logger.warn("Cronbell: " + check.source() + " is not monitored: its slug '" + check.slug()
					+ "' is already used by another job. Give one of them @CronbellCheck(\"another-slug\").");
			return false;
		}
		this.byKey.put(key, check);
		return true;
	}

	/**
	 * Whether a key is known.
	 * @param key the runtime key
	 * @return {@code true} if known
	 */
	public boolean contains(Object key) {
		return this.byKey.containsKey(key);
	}

	/**
	 * Finds the check of a job.
	 * @param key the runtime key
	 * @return the check, or {@code null} if the job is not monitored
	 */
	public MonitoredCheck find(Object key) {
		return this.byKey.get(key);
	}

	/**
	 * All monitored checks.
	 * @return a snapshot
	 */
	public List<MonitoredCheck> all() {
		return new ArrayList<>(this.byKey.values());
	}

}
