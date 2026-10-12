# Contributing

Thanks for your interest. Please open an issue before working on anything larger than a small fix.

## Build and test

Requires JDK 17 or newer (the code is compiled for Java 17).

```bash
./mvnw verify               # Spring Boot 3.5, also checks formatting
./mvnw verify -Pboot33      # Spring Boot 3.3
./mvnw verify -Pboot4       # Spring Boot 4.1
./mvnw spring-javaformat:apply
```

The `minimal-classpath` test execution runs without Actuator, Quartz and ShedLock: optional
libraries must only be loaded when they are present.

## Rules for changes

- A monitoring problem must never slow down or break a user's job. Anything on the job thread must
  be constant time and must not throw.
- Keep dependencies to what Spring Boot already brings. Optional integrations are `optional` in the
  POM, live in their own package (`internal.quartz`, `internal.shedlock`, `internal.actuator`) and
  are guarded with `@ConditionalOnClass(name = ...)` on their own configuration class.
  `ArchitectureTests` (ArchUnit) fails the build when another class names one of these libraries,
  even as a string, or when a public type exposes an `internal` type.
- Everything under `com.steelreed.cronbell.spring.internal` is not public API and may change in any
  release.
- One change per pull request, with tests that fail without it. Add a CHANGELOG line when users
  will notice.

## Sign your commits (DCO)

Every commit needs a `Signed-off-by` line ([Developer Certificate of Origin](https://developercertificate.org/)):

```bash
git commit -s -m "fix: ..."
```

## Using AI tools

You are welcome to use AI assistants. You remain the author: read, understand and test every line
you submit, and never paste secrets or private data into an AI tool. Unreviewed bulk pull requests
are closed.

## License

By contributing, you agree that your contribution is licensed under the Apache-2.0 License.
Security problems go through the
[security policy](https://github.com/steelreed/.github/blob/main/SECURITY.md), never a public issue.
