# skillmap-api

Backend compartido (Java + Spring Boot) para los clientes **Desktop**, **Mobile**
y **Wearable** de **Cenit** (antes SkillMap), "Tu ruta hacia el cenit de tu carrera". Arquitectura limpia por capas:

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

- **Datos**: H2 en modo archivo en `%APPDATA%\SkillMap\data` (`~/SkillMap/data` fuera de Windows). La carpeta
  conserva el nombre anterior de la app a propósito: quien ya la usaba no pierde sus datos.
  Se cambia con `--skillmap.home=...` o solo la base con `--skillmap.data-dir=...`.
  Consola en `/h2-console` (JDBC URL: `jdbc:h2:file:<carpeta de datos>/skillmap`).
- **Puerto**: 8080; si está ocupado, el siguiente libre (hasta 8089) o uno al azar.
- **Red**: escucha solo en `127.0.0.1` (no accesible desde la red local); `--server.address=0.0.0.0` para abrirlo.
- **CORS**: la API acepta el mismo origen y `skillmap.cors.allowed-origins`
  (por defecto Live Server: `http://localhost:5500`, `http://127.0.0.1:5500`).
- **Una sola instancia** por carpeta de datos: un segundo arranque abre la ventana de la primera y termina.
- **Ofertas incluidas**: con la tabla de ofertas vacía, al arrancar se importan las de
  `src/main/resources/seed/job-postings.json` (sin llamar a ninguna API) y se recalcula la demanda.
  Cada oferta guarda su fuente, título, empresa, URL original (Remotive exige enlazarla) y fecha.
  Se regenera desde una COPIA de la base con `scripts/ExportJobPostings.java` (solo lectura; el comando está en el archivo).
- **Primer arranque**: ya no se sincroniza (`jobs.sync.on-first-start=false`): la app trae sus ofertas.
- **Remotive automático**: al arrancar y cada hora, si la última actualización de Remotive tiene
  `skillmap.sync.remotive.interval-days` días o más (4 por defecto, de 3 a 5), se sincroniza solo Remotive.
- **API key de Gemini**: variable de entorno `GEMINI_API_KEY` o, si no existe, la línea
  `gemini.api.key=...` en `%APPDATA%\SkillMap\config.properties`. Nunca en el repositorio ni en el instalable:
  sin key propia, el Analizador usa el servicio de IA en Render (`skillmap.ai.remote-url`).
- **Frontend**: se sirve desde `classpath:/static/`. El script de empaquetado lo copia a
  `src/main/resources/static/` (en `.gitignore`).
- **Logs**: consola y `%APPDATA%\SkillMap\logs\skillmap.log` (`skillmap.logs-dir`). Rota al pasar de ~5 MB:
  `skillmap.log`, `skillmap.1.log` y `skillmap.2.log` como mucho. La key de Gemini se tapa (`***`) en todos los logs.
- **Empaquetar** (Windows, JDK con jpackage): `powershell -ExecutionPolicy Bypass -File scripts\build-app.ps1`
  genera `dist\Cenit\Cenit.exe` y lo prueba con datos temporales, sin red y sin `GEMINI_API_KEY`
  (registra un usuario temporal y comprueba `/api/health`, `/api/skills` y que se cargaron las ofertas incluidas).
- **Cerrar** la app empaquetada: `dist\Cenit\stop.bat` (o `stop.bat Otro.exe` si el ejecutable se llama distinto).
  Solo cierra el ejecutable de su misma carpeta.

## Endpoints (primer corte)
- `GET  /api/skills` — lista todas las habilidades
- `GET  /api/skills/{id}` — detalle de una habilidad
- `PATCH /api/skills/{id}/master` — marca una habilidad como dominada
- `PUT /api/skills/{id}/status` — `{"status":"PENDING"|"IN_PROGRESS"|"MASTERED"}`: pendiente, aprendiendo o dominada (solo para el usuario)
- `PUT /api/auth/me/goal` — `{"goalId": n}`: objetivo profesional del usuario (sale como `goalId` en `/api/auth/me`); su progreso no cambia

## Próximos commits
- CareerGoal + GoalSkill (relación objetivo–habilidad y % de demanda)
- Migrar de H2 a PostgreSQL
- Endpoint del analizador de ofertas


## Commit 2: CareerGoal + GoalSkill

Nuevos endpoints:

- `GET /api/goals` — lista de objetivos laborales
- `GET /api/goals/{id}` — un objetivo con sus habilidades e importancia (1-5)
- `GET /api/goals/{id}/readiness` — % de preparación ponderado, conteos y brechas ordenadas por demanda
- `GET /api/goals/{id}/readiness/history?days=30` — `[{"date","percentage"}]` del usuario por fecha (1 a 365 días, 30 por defecto); se guarda un punto por día al cambiar el estado de una habilidad o el objetivo, solo si el % cambia

Datos de ejemplo: "Frontend Senior" (id 1) y "Backend Java" (id 2).

## Ofertas de empleo

Todas exigen sesión (`Authorization: Bearer <token>`; sin ella, 401).

- `GET /api/jobs?limit=50` — las ofertas guardadas más recientes, sin la descripción
- `GET /api/jobs/stats` — total y ofertas por fuente, última y próxima sincronización
- `POST /api/jobs/sync` — trae ofertas nuevas de Arbeitnow y Remotive (como mucho cada 6 h por fuente)
- `GET /api/jobs/recommended?limit=10&goalId=` — las ofertas guardadas que mejor encajan con el objetivo del usuario
  (`users.goal_id`; si no tiene, el primero; `goalId` opcional para pedir las de otro objetivo, 404 si no existe).
  `limit` de 1 a 30 (10 por defecto; fuera de rango, 400).
  Las habilidades de cada oferta salen de `SkillMatcher` (sin IA, sin llamadas externas) y se recalcula en cada
  petición con el progreso actual; la detección por oferta se cachea en memoria (no depende del usuario).
  - Solo ofertas con al menos 2 habilidades detectadas y al menos 1 del objetivo.
  - `matchPercentage`: % de las habilidades detectadas que el usuario ya tiene (dominada 1, aprendiendo 0,5, pendiente 0).
  - Orden: mayor `matchPercentage`, luego más habilidades del objetivo, luego la más reciente.
  - Cada elemento: `id`, `title`, `company`, `source`, `url` (la original: Remotive exige citarla y enlazarla),
    `publishedAt`, `matchPercentage`, `masteredSkills` (nombres) y `missingSkills` (`[{"name","status"}]`, con
    `IN_PROGRESS` antes que `PENDING`). Nunca el texto completo de la oferta.
- `GET /api/jobs/{id}` — una oferta con su `description` completa (para llevarla al Analizador); 404 si no existe.
  No llama a la IA: el análisis solo se hace si el usuario lo pide en el Analizador.
