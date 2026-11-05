# Java Modular Monolith Scaffold

> **Practice project.** It has never run in production and has no real users or data volume. Results below come from one local machine.

A modular monolith on Spring Boot 4.1, JDK 25, Spring Modulith, and MyBatis-Plus. The domain is a small e-commerce system: auth, users, products, inventory, and orders. The main flow is idempotent ordering, stock reduction without overselling, then payment or timeout cancellation. It also includes runnable labs on Spring, concurrency, caching, and MySQL.

[中文说明](README.zh-CN.md)

## What it covers

- **Module boundaries as tests:** Spring Modulith and ArchUnit fail the build if one module reaches into another's internals, or if the domain layer depends on the framework.
- **Order flow:** an idempotency key with a unique index, one conditional update to reduce stock, and the order and stock reservation in one local transaction. With 50 units in stock and 200 concurrent requests, exactly 50 orders succeed.
- **Authentication:** JWT with refresh token rotation and replay detection. When 10 threads use the same refresh token at once, one succeeds.
- **Custom starter:** `trace-spring-boot-starter` adds trace IDs and access logs, and shows how auto-configuration works.
- **Labs:** transactions, AOP, bean lifecycle, circular dependencies, thread pools, caching, overselling strategies, and MySQL behavior.

## Problems found while testing

**Redis `sync = true` did not stop a cache stampede.** 100 concurrent requests for one cold key hit the database 100 times. The Spring Data Redis 4.1.1 source showed that `RedisCache.get(key, loader)` has no in-process lock. The fix was a per-key `SingleFlightCacheManager`. A second problem followed: with Lettuce, `put` writes asynchronously by default, so a key was sometimes loaded twice. Adding `immediateWrites()` brought it down to one load in three consecutive runs.

**A test slice silently scanned everything.** The meta-annotations on `@MybatisPlusTest` 3.5.17 refer to Spring Boot 3 package names. The JVM ignores annotation classes it cannot find, so the "mappers only" filter disappeared without an error. The fix was a custom `@MybatisSliceTest`.

## Test status

| Check | Result | Last run |
|---|---|---|
| `./mvnw clean verify` on one machine | 152 tests, 0 failures, 6 skipped (the MySQL labs need `LAB_MYSQL_URL`) | 2026-11-04 |
| [GitHub Actions](https://github.com/baronZhang/java-monolith-scaffold/actions/workflows/ci.yml) `verify` job, with MySQL and Redis | 152 tests, 0 failures, 0 skipped | 2026-11-05 |
| Manual API walkthrough on H2 and on MySQL + Redis | Worked | 2026-10-27 |
| Timeout cancellation and graceful shutdown | Ran as expected | 2026-10-27 |

**Not verified:** the Docker image has not been built, and the CI `image` job runs only when triggered by hand. Graceful shutdown details on Kubernetes have not been tested.

**Known issue:** the `/actuator/modulith` response has 53 extra bytes after the JSON. The cause has not been found, so the integration test asserts on the raw string.

## Quick start

Requires JDK 25. Maven is included through `./mvnw`.

```bash
# all quality gates and tests
./mvnw verify

# run with in-memory H2 and Caffeine, no external services
java -jar monolith-app/target/monolith-app.jar --spring.profiles.active=demo
```

The API is at `http://localhost:8080` under `/api`. The demo accounts are `admin`, `editor`, and `guest`. Their password is in `monolith-app/src/test/java/com/example/monolith/support/DemoAccounts.java`.

## Profiles

| Profile | Effect |
|---|---|
| (default) | H2 in-memory database (MySQL mode) and Caffeine cache |
| `demo` | Enables `/api/debug/*` and cancels unpaid orders after 2 minutes |
| `mysql` | Uses MySQL (`docker compose up -d mysql`, port 3307) |
| `redis` | Uses Redis for cache and token revocation (local port 6379 by default; set `REDIS_PORT=6380` for the compose Redis) |
| `prod` | `mysql` + `redis`; the JWT secret must come from `APP_AUTH_JWT_SECRET` |

## Layout

```text
pom.xml                      parent POM: version management, Enforcer, Spotless, Checkstyle
trace-spring-boot-starter/   hand-written starter for trace IDs and access logs
monolith-app/                the modular monolith
  src/main/java/com/example/monolith/
    shared/                  shared kernel: responses, error codes, permissions, cache, Snowflake IDs
    auth/ user/ product/ inventory/ order/
                             business modules, each split into interfaces, application, domain, infrastructure
  src/main/resources/db/migration/   Flyway scripts
  src/test/java/.../labs/    runnable labs
compose.yaml, Dockerfile     local dependencies and layered image
.github/                     CI workflow
```

## Notes

- Code comments are in Chinese.
- Passwords and secrets in this repository are demo values for local use only.
- The API matches a separate Vue 3 demo frontend, which is not part of this repository.
- This project was developed with Claude Code. The results above come from real test runs.
