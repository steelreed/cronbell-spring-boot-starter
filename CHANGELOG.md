# Changelog

All notable changes are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.0-beta.1] - 2026-10-12

### Added

- Auto-configuration that discovers `@Scheduled` jobs (cron with zone, fixed rate, fixed delay),
  Quartz triggers and ShedLock-locked jobs, registers them with Cronbell and reports every run
  (`start`, then `success` or `fail` with the exception).
- `@CronbellCheck` for stable slugs, names, grace, maximum runtime, or to leave a job out.
- Properties `cronbell.url`, `api-key`, `enabled`, `environment`, `include`, `exclude`, `grace`,
  `register`, `queue-capacity` and `timeout.*`.
- Reporting on one background thread with a bounded queue: a slow or unreachable server never
  delays a job; warnings are rate limited to one a minute.
- `/actuator/info` entry when Actuator is present.
- Works on Spring Boot 3.3 to 4.1 and Java 17+, with `ThreadPoolTaskScheduler` and
  `SimpleAsyncTaskScheduler`.
