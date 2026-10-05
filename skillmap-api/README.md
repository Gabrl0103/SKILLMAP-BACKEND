# skillmap-api

Backend compartido (Java + Spring Boot) para los clientes **Desktop**, **Mobile**
y **Wearable** del proyecto SkillMap. Arquitectura limpia por capas:

```
domain/          → entidades y reglas de negocio puras (sin Spring, sin JPA)
application/     → casos de uso, orquestan el dominio
infrastructure/  → JPA, base de datos, adaptadores técnicos
presentation/    → controladores REST que consumen los 3 clientes
```

## Cómo correrlo
```
./mvnw spring-boot:run -Dspring-boot.run.arguments=--skillmap.desktop.open-browser=false
```
Sin el argumento, al arrancar se abre la app (Edge en modo app o el navegador por defecto).

- **Datos**: H2 en modo archivo en `%APPDATA%\SkillMap\data` (`~/SkillMap/data` fuera de Windows).
  Se cambia con `--skillmap.home=...` o solo la base con `--skillmap.data-dir=...`.
  Consola en `/h2-console` (JDBC URL: `jdbc:h2:file:<carpeta de datos>/skillmap`).
- **Puerto**: 8080; si está ocupado, el siguiente libre (hasta 8089) o uno al azar.
- **Red**: escucha solo en `127.0.0.1` (no accesible desde la red local); `--server.address=0.0.0.0` para abrirlo.
- **CORS**: la API acepta el mismo origen y `skillmap.cors.allowed-origins`
  (por defecto Live Server: `http://localhost:5500`, `http://127.0.0.1:5500`).
- **Una sola instancia** por carpeta de datos: un segundo arranque abre la ventana de la primera y termina.
- **Primer arranque**: sin ofertas guardadas, se sincroniza en segundo plano (`jobs.sync.on-first-start`).
- **API key de Gemini**: variable de entorno `GEMINI_API_KEY` o, si no existe, la línea
  `gemini.api.key=...` en `%APPDATA%\SkillMap\config.properties`. Nunca en el repositorio.
- **Frontend**: se sirve desde `classpath:/static/`. El script de empaquetado lo copia a
  `src/main/resources/static/` (en `.gitignore`).
- **Logs**: consola y `%APPDATA%\SkillMap\logs\skillmap.log` (`skillmap.logs-dir`). Rota al pasar de ~5 MB:
  `skillmap.log`, `skillmap.1.log` y `skillmap.2.log` como mucho. La key de Gemini se tapa (`***`) en todos los logs.
- **Empaquetar** (Windows, JDK con jpackage): `powershell -ExecutionPolicy Bypass -File scripts\build-app.ps1`
  genera `dist\SkillMap\SkillMap.exe` y lo prueba con datos temporales.
- **Cerrar** la app empaquetada: `dist\SkillMap\stop.bat` (o `stop.bat Otro.exe` si el ejecutable se llama distinto).
  Solo cierra el ejecutable de su misma carpeta.

## Endpoints (primer corte)
- `GET  /api/skills` — lista todas las habilidades
- `GET  /api/skills/{id}` — detalle de una habilidad
- `PATCH /api/skills/{id}/master` — marca una habilidad como dominada

## Próximos commits
- CareerGoal + GoalSkill (relación objetivo–habilidad y % de demanda)
- Migrar de H2 a PostgreSQL
- Endpoint del analizador de ofertas


## Commit 2: CareerGoal + GoalSkill

Nuevos endpoints:

- `GET /api/goals` — lista de objetivos laborales
- `GET /api/goals/{id}` — un objetivo con sus habilidades e importancia (1-5)
- `GET /api/goals/{id}/readiness` — % de preparación ponderado, conteos y brechas ordenadas por demanda

Datos de ejemplo: "Frontend Senior" (id 1) y "Backend Java" (id 2).
