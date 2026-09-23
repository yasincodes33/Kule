# Kule backend — çok aşamalı (multi-stage) imaj.
#
# Derleme bağlamı depo kökü olmalıdır (pom.xml ve kaynaklar oradadır):
#   docker build -f docker/Dockerfile -t kule-backend:0.1.0 .
#   docker run --rm -p 8081:8081 --env-file .env kule-backend:0.1.0
#
# Zorunlu ortam değişkenleri için bkz. .env.example.

# ---------------------------------------------------------------------------
# 1) Derleme
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Önce yalnızca bağımlılık tanımı kopyalanır: pom.xml değişmediği sürece bu katman
# önbellekten gelir ve kaynak kodun her değişiminde bağımlılıklar yeniden indirilmez.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# ---------------------------------------------------------------------------
# 2) Çalıştırma
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Uygulama root olmayan bir kullanıcıyla çalışır.
RUN addgroup -S kule && adduser -S kule -G kule

COPY --from=build /build/target/agentsaas-*.jar app.jar
RUN chown kule:kule /app/app.jar

USER kule
EXPOSE 8081

# Konteyner belleğine uyum sağlaması için varsayılan JVM ayarları; JAVA_OPTS ile ezilebilir.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O - http://localhost:8081/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
