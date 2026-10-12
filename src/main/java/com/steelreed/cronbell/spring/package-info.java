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

/**
 * Cronbell monitoring for Spring Boot. Adding the starter and setting
 * {@code cronbell.url} and {@code cronbell.api-key} is enough: every {@code @Scheduled}
 * method and Quartz trigger is monitored.
 * {@link com.steelreed.cronbell.spring.CronbellCheck} customises a job, and
 * {@link com.steelreed.cronbell.spring.CronbellProperties} lists the properties.
 * Everything else, under {@code internal}, is not public API.
 */
package com.steelreed.cronbell.spring;
