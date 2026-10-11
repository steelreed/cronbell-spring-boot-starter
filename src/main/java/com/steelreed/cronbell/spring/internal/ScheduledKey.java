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

import java.lang.reflect.Method;

import org.springframework.util.ClassUtils;

/**
 * How a {@code @Scheduled} run is recognised: the user class of the target bean and the
 * invoked method, as both {@code ScheduledMethodRunnable} and
 * {@code ScheduledTaskObservationContext} expose them.
 *
 * @param targetClass the target's user class
 * @param method the invoked method
 */
public record ScheduledKey(Class<?> targetClass, Method method) {

	/**
	 * The key of a scheduled method of a target bean.
	 * @param target the bean, possibly a proxy
	 * @param method the invoked method
	 * @return the key
	 */
	public static ScheduledKey of(Object target, Method method) {
		return new ScheduledKey(ClassUtils.getUserClass(target), method);
	}

}
