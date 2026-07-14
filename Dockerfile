# --- Build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --- Run ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080

# Memory budget. Without these the JVM sizes itself off the HOST's memory (max heap ≈ 25% of it),
# picks G1GC, and never returns memory to the OS — which is how a low-traffic app ends up holding
# ~700MB RSS on Railway, where RAM is billed harder than CPU.
#
# Measured on this app (full GC, then NMT): live heap ~64MB, metaspace ~107MB, code cache ~45MB.
# The caps below are set against those real numbers, not guesses.
#
#   Xmx256m            hard heap cap — ~4x the measured live set, so there is room to spike.
#   Xms64m             start small and grow, rather than reserving the cap up front.
#   Min/MaxHeapFreeRatio  let the heap SHRINK back after a collection. Without these the JVM keeps
#                      every page it ever touched, so one traffic spike permanently raises the bill.
#   UseSerialGC        single GC thread. G1 spins up several plus per-region bookkeeping; on a 1-2 vCPU
#                      box with a small heap, serial collection uses noticeably less RSS for no real
#                      latency cost at this traffic level.
#   MaxMetaspaceSize   metaspace is OFF-heap and unbounded by default. Spring+Hibernate measured
#                      ~107MB here, so 192m leaves genuine headroom — do NOT lower this to ~128m, it
#                      would sit right on top of the real usage and risk a metaspace OOM.
#   Xss512k            thread stacks are off-heap too. Default is 1MB × every Tomcat thread.
#   MaxDirectMemorySize  caps NIO off-heap buffers, which otherwise default to the heap size again.
#   ExitOnOutOfMemoryError  die and let Railway restart, instead of thrashing GC forever in a
#                      half-dead state that still bills you for the RAM.
#
# Override in the Railway dashboard by setting JAVA_OPTS if you need to tune without a rebuild.
ENV JAVA_OPTS="-Xms64m -Xmx256m -XX:MinHeapFreeRatio=20 -XX:MaxHeapFreeRatio=40 -XX:+UseSerialGC -XX:MaxMetaspaceSize=192m -Xss512k -XX:MaxDirectMemorySize=64m -XX:+ExitOnOutOfMemoryError"

# `exec` so the JVM stays PID 1 and receives SIGTERM on shutdown/redeploy.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
