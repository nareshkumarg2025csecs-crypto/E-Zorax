# Multi-stage Docker build for Java 17 Web Application
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy dependency libraries and sources
COPY lib/ lib/
COPY src/ src/
COPY web/ web/

# Compile application
RUN mkdir -p out && \
    javac -cp "lib/*" -d out src/db/*.java src/model/*.java src/service/*.java src/app/*.java

# Runtime Image
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/lib/ /app/lib/
COPY --from=builder /app/out/ /app/out/
COPY --from=builder /app/web/ /app/web/

EXPOSE 8080

CMD ["java", "-cp", "out:lib/*", "app.WebServer"]
