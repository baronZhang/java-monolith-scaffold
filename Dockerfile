# =====================================================================
# 多阶段构建 + Spring Boot 分层 jar。
#
# 为什么要分层：一个 Spring Boot jar 里 95% 以上是第三方依赖，它们很少变；我们自己的代码只有几百 KB，但天天变。
# 把 jar 拆成 dependencies / spring-boot-loader / snapshot-dependencies / application 四层分别 COPY，
# 改了业务代码重新构建时，只有最后一层变化，推送和拉取镜像只传这一层。
#
# 构建：docker build -t monolith-app .
# 注意：本机连不上 Docker Hub，这个镜像没有实际构建过，见"单体 6.2 Docker 分层镜像与 Compose"。
# =====================================================================

# ---------- 第一阶段：用完整 JDK 编译打包 ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# 先复制构建脚本和 POM：源码改动不会让这几层失效
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY config config
COPY trace-spring-boot-starter/pom.xml trace-spring-boot-starter/
COPY monolith-app/pom.xml monolith-app/
COPY trace-spring-boot-starter/src trace-spring-boot-starter/src
COPY monolith-app/src monolith-app/src

# 缓存挂载：本地 Maven 仓库在多次构建之间复用，不会因为源码变化重新下载全部依赖。
# 测试和质量检查在 CI 的 ./mvnw verify 里已经跑过，这里只打包
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -DskipTests -Dcheckstyle.skip=true -Dspotless.check.skip=true -Djacoco.skip=true package

# 按层解压（Spring Boot 3.3+ 的 tools 模式），--launcher 表示连启动器一起解压
RUN java -Djarmode=tools -jar monolith-app/target/monolith-app.jar extract --layers --launcher --destination extracted

# ---------- 第二阶段：运行时镜像只需要 JRE ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

# 不用 root 运行：容器被攻破时，攻击者拿到的权限越小越好
RUN groupadd --system app && useradd --system --gid app --no-create-home app

# 按"变化频率从低到高"的顺序复制，越靠后的层越常变
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app
EXPOSE 8080

# 容器里按容器的内存限制计算堆大小（而不是宿主机的内存）；OOM 时直接退出，让编排系统重启，而不是带病运行
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# exec：让 java 成为 1 号进程，直接收到 docker stop 发的 SIGTERM，优雅停机才会生效
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
