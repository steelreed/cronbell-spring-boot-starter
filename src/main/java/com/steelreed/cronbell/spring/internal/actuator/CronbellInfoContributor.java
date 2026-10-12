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

package com.steelreed.cronbell.spring.internal.actuator;

import java.util.LinkedHashMap;
import java.util.Map;

import com.steelreed.cronbell.spring.internal.CronbellMonitor;
import com.steelreed.cronbell.spring.internal.MonitoredCheck;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

/**
 * Adds a {@code cronbell} entry to {@code /actuator/info}: the server, the monitored
 * checks, pings sent and dropped, and the last error. Never the API key.
 */
public class CronbellInfoContributor implements InfoContributor {

	private final CronbellMonitor monitor;

	public CronbellInfoContributor(CronbellMonitor monitor) {
		this.monitor = monitor;
	}

	@Override
	public void contribute(Info.Builder builder) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("url", this.monitor.reporter().baseUrl());
		var checks = this.monitor.registry().all();
		details.put("checks", checks.size());
		details.put("registered",
				checks.stream().filter((check) -> check.state() == MonitoredCheck.State.REGISTERED).count());
		details.put("pingsSent", this.monitor.reporter().sentPings());
		details.put("pingsDropped", this.monitor.reporter().droppedPings());
		String lastError = this.monitor.reporter().lastError();
		if (lastError != null) {
			details.put("lastError", lastError);
			details.put("lastErrorAt", String.valueOf(this.monitor.reporter().lastErrorAt()));
		}
		builder.withDetail("cronbell", details);
	}

}
