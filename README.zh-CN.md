# Java 模块化单体脚手架 demo

[English](README.md)

一个用来验证设计的**模块化单体**（Modular Monolith）脚手架：Spring Boot 4.1 + JDK 25 + Spring Modulith + MyBatis-Plus。
业务域是一个精简的电商：认证、用户、商品、库存、订单，重点是**幂等下单 → 扣库存（防超卖）→ 支付 / 超时取消**这条链路，
以及一组可以直接运行的 Spring / 并发 / 缓存 / MySQL 原理实验。

> 这是练习项目，没有上过生产，没有真实用户和数据量。项目用 Claude Code 开发，测试结果都来自真实运行。

## 快速开始

需要 JDK 25（LTS）。Maven 不用装，用自带的 `./mvnw`。

```bash
# 全部质量门禁 + 测试（单元、切片、模块、集成、原理实验）
./mvnw verify

# 启动（默认 H2 内存库 + Caffeine，不依赖任何外部服务；demo profile 打开演示接口）
java -jar monolith-app/target/monolith-app.jar --spring.profiles.active=demo
```

服务在 `http://localhost:8080`，接口前缀 `/api`。
三个演示账号是 `admin` / `editor` / `guest`，密码在 `monolith-app/src/test/java/com/example/monolith/support/DemoAccounts.java`。

## profile

| profile | 作用 |
|---|---|
| （默认） | H2 内存库（MySQL 兼容模式）+ Caffeine 缓存 |
| `demo` | 开放 `/api/debug/*`，订单 2 分钟不支付自动取消 |
| `mysql` | 数据源换成 MySQL（`docker compose up -d mysql`，端口 3307） |
| `redis` | 缓存和 token 吊销表换成 Redis（默认连本机 6379；用 `docker compose up -d redis` 起的 Redis 时设 `REDIS_PORT=6380`） |
| `prod` | = `mysql` + `redis`，JWT 密钥必须从环境变量 `APP_AUTH_JWT_SECRET` 注入 |

## 目录

```text
pom.xml                      父 POM：版本管理、Enforcer、Spotless、Checkstyle
trace-spring-boot-starter/   手写的 starter：traceId + 访问日志（演示自动配置原理）
monolith-app/                模块化单体应用
  src/main/java/com/example/monolith/
    shared/                  共享内核：统一响应、错误码、权限注解、缓存、雪花 ID、事件重投
    auth/  user/  product/  inventory/  order/    五个业务模块，各自分 interfaces / application / domain / infrastructure
  src/main/resources/db/migration/   Flyway 脚本（common + h2 / mysql）
  src/test/java/.../labs/    原理实验：事务、AOP、Bean 生命周期、循环依赖、线程池、缓存、超卖、MySQL
compose.yaml / Dockerfile    本地依赖与分层镜像
.github/                     CI 流水线
```

## 说明

- 仓库里的密码和密钥都是本地演示用的值。
- 接口和另一个 Vue 3 前端 demo 一致，前端不在这个仓库里。
