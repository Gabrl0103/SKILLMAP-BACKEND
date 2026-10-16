# Servicio de IA de SkillMap (perfil cloud) para Render.
# La key se pasa como variable de entorno GEMINI_API_KEY en el panel de Render, nunca en la imagen.

# --- Build: compila el jar con el Maven wrapper ---
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Por si mvnw llega con finales de línea de Windows.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# --- Runtime: solo el JRE y el jar, con un usuario sin privilegios ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S skillmap && adduser -S skillmap -G skillmap
COPY --from=build /app/target/skillmap-api-*.jar app.jar
USER skillmap
# Render da el puerto en PORT (8080 si no se define).
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar --spring.profiles.active=cloud"]
