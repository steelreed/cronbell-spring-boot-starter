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

package com.steelreed.cronbell.spring.internal.shedlock;

import java.lang.reflect.Method;

import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;

/**
 * ShedLock's {@code @SchedulerLock} on a scheduled method, read by name so that ShedLock
 * stays optional.
 *
 * @param lockAtMostFor the raw {@code lockAtMostFor} attribute, unresolved; empty when
 * unset or unreadable
 */
public record SchedulerLockAnnotation(String lockAtMostFor) {

	static final String SCHEDULER_LOCK = "net.javacrumbs.shedlock.spring.annotation.SchedulerLock";

	/**
	 * The {@code @SchedulerLock} of a method.
	 * @param method the method
	 * @return the annotation, or {@code null} if the method has none
	 */
	public static SchedulerLockAnnotation find(Method method) {
		MergedAnnotation<?> lock = MergedAnnotations.from(method).get(SCHEDULER_LOCK);
		if (!lock.isPresent()) {
			return null;
		}
		String lockAtMostFor;
		try {
			lockAtMostFor = lock.getString("lockAtMostFor");
		}
		catch (RuntimeException ex) {
			lockAtMostFor = "";
		}
		return new SchedulerLockAnnotation(lockAtMostFor);
	}

}
