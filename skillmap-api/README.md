# skillmap-api

Backend compartido (Java + Spring Boot) para los clientes **Desktop**, **Mobile**
y **Wearable** de **Cenit** (antes SkillMap), "Tu ruta hacia el cenit de tu carrera". Arquitectura limpia por capas:

```
domain/          → entidades y reglas de negocio puras (sin Spring, sin JPA)
application/     → casos de uso, orquestan el dominio
infrastructure/  → JPA, base de datos, adaptadores técnicos
presentation/    → controladores REST que consumen los 3 clientes
```

Detalle de capas, flujo, perfiles, empaquetado y decisiones en [`../docs/ARQUITECTURA.md`](../docs/ARQUITECTURA.md).

## Cómo correrlo
```
./mvnw spring-boot:run -Dspring-boot.run.arguments=--skillmap.desktop.open-browser=false
```
Sin el argumento, al arrancar el backend suelto abre la interfaz en una ventana de Edge (modo `--app`) o en el
navegador por defecto. Es solo para desarrollo y para la app anterior de jpackage: **la app de escritorio actual es
`cenit-desktop/` (Electron)**, que abre Cenit en su propia ventana y arranca el backend sin navegador.

Tests: `./mvnw test` con JDK 21 (con JDK 26, Mockito puede fallar al instrumentar clases).

## Propiedades principales

Todas en `src/main/resources/application.properties` (comentadas por secciones); se cambian con `--propiedad=valor`.
Ninguna lleva secretos.

| Propiedad | Por defecto | Para qué |
|---|---|---|
| `skillmap.home` | `%APPDATA%\SkillMap` (`~/SkillMap`) | Carpeta del usuario: datos, `config.properties`, logs |
| `skillmap.data-dir` | `${skillmap.home}/data` | Base H2 |
| `skillmap.config-dir` | `${skillmap.home}` | Dónde está `config.properties` (key de Gemini guardada desde Ajustes) |
| `skillmap.logs-dir` | `${skillmap.home}/logs` | Logs con rotación |
| `server.port` / `server.address` | `8080` / `127.0.0.1` | Puerto (si está ocupado, otro libre) y red (solo este equipo) |
| `skillmap.cors.allowed-origins` | Live Server (`:5500`) | Orígenes externos permitidos en `/api/**` |
| `skillmap.auth.session-ttl` | `30d` | Duración de la sesión |
| `skillmap.desktop.open-browser` | `true` | Abrir la interfaz al arrancar (Electron pasa `false`) |
| `skillmap.desktop.strict-port` | `false` | Usar `server.port` exacto (Electron pasa `true`) |
| `skillmap.desktop.exit-on-stdin-close` | `false` | Apagarse al cerrar stdin (Electron pasa `true`) |
| `gemini.model` / `gemini.fallback-model` | `gemini-3.8-flash` / `gemini-3.5-flash` | Modelos para el Analizador con key propia |
| `gemini.timeout` / `gemini.total-timeout` | `30s` / `35s` | Límite por intento y total |
| `skillmap.ai.remote-url` | servicio en Render | IA sin key propia (perfil cloud) |
| `jobs.sync.cron` | `0 0 6 * * *` (UTC) | Sincronización diaria de ofertas (`-` la desactiva) |
| `jobs.sync.min-interval` | `6h` | Mínimo entre consultas a una misma fuente |
| `jobs.sync.on-first-start` | `false` | Sincronizar al arrancar sin ofertas (la app trae las suyas) |
| `skillmap.sync.remotive.*` / `skillmap.sync.arbeitnow.*` | cada 4 / 1 días, activas | Sincronización automática por fuente |
| `jobs.retention.max-postings` / `max-age` | `1000` / `60d` | Cuántas ofertas se guardan |
| `jobs.demand.min-postings` | `30` | Mínimo de ofertas para recalcular la demanda |
| `skillmap.ai.daily-limit-per-ip` / `-global` | `20` / `150` (solo perfil `cloud`) | Cupo diario del servicio de IA |

Perfil `cloud` (Render): `application-cloud.properties`. Tests: `src/test/resources/config/application.properties`.

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
  Solo cierra el ejecutable de su misma carpeta. Con la app de escritorio de `cenit-desktop/` (Electron) ya no hace
  falta: al cerrar la ventana se detiene el backend.
- **App de escritorio** (`cenit-desktop/`, Electron): lanza este backend oculto con
  `--skillmap.desktop.open-browser=false` (no abre el navegador; también vale la variable de entorno
  `SKILLMAP_DESKTOP_OPENBROWSER=false`), `--server.port=<puerto que elige Electron>`,
  `--skillmap.desktop.strict-port=true` (usa ese puerto exacto: si está ocupado falla y Electron prueba otro, en vez de
  cambiarlo sin avisar) y `--skillmap.desktop.exit-on-stdin-close=true` (se apaga ordenadamente cuando Electron cierra
  stdin o muere: no quedan procesos `java` huérfanos). Si otra instancia ya usa la misma carpeta de datos y no hay
  navegador que abrir, el arranque termina con código 3. Por defecto `open-browser=true` y las otras dos en `false`: el
  comportamiento de `mvnw spring-boot:run` y de `dist\Cenit\Cenit.exe` no cambia.

## Endpoints (primer corte)
- `GET  /api/skills` — lista todas las habilidades, con el estado del usuario y `statusUpdatedAt`: cuándo pasó a ese
  estado (ISO-8601 en UTC, al segundo; `null` si está pendiente). Repetir el mismo estado no mueve la fecha. Las
  brechas de `GET /api/goals/{id}/readiness` traen el mismo campo.
- `GET  /api/skills/{id}` — detalle de una habilidad (mismos campos)
- `PATCH /api/skills/{id}/master` — marca una habilidad como dominada
- `PUT /api/skills/{id}/status` — `{"status":"PENDING"|"IN_PROGRESS"|"MASTERED"}`: pendiente, aprendiendo o dominada (solo para el usuario)

La fecha vive en `user_skills.updated_at`. `ddl-auto=update` agrega la columna (admite null) a una base existente y, al
arrancar, `UserSkillDatesBackfill` les da a las filas sin fecha la fecha de alta de su cuenta: la más antigua posible,
no una inventada, así lo marcado antes de esta versión no cuenta como avance de esta semana. Lo prueban
`SkillStatusDateTest` (reloj fijo) y `LegacyUserSkillsTableTest` (base anterior con progreso guardado).
- `PUT /api/auth/me/goal` — `{"goalId": n}`: objetivo profesional del usuario (sale como `goalId` en `/api/auth/me`); su progreso no cambia.
  Acepta un objetivo base o uno propio del usuario (404 si es propio de otro usuario).

### Objetivos propios ("Otro objetivo")

Cada usuario puede crear hasta 3 objetivos propios: nombre de 2 a 40 caracteres en texto plano (sin `<` ni `>`), que no
repita el de un objetivo base ni el de otro propio (sin mayúsculas ni espacios de sobra), descripción opcional de hasta
120 y de 3 a 8 habilidades del catálogo sin repetir (reglas en `CustomGoalFactory`). Todas sus habilidades pesan igual
(importancia 3): el % es dominadas / total. Funcionan en todo lo que usa un `goalId`: readiness, historial, ofertas
recomendadas, `PUT /api/auth/me/goal` y export. Sus ids empiezan en 1.000.000 (secuencia `custom_goals_seq`), así nunca
chocan con los base.

- `GET /api/goals` — ahora devuelve los base y, después, los propios del usuario. Campos nuevos (solo se agregan):
  `custom` (`true` en los propios), `createdAt` y `updatedAt` (null en los base).
- `GET /api/goals/custom` — los propios del usuario, del más antiguo al más nuevo.
- `POST /api/goals/custom` — `{"name", "description", "skillIds": [..]}` → 201 con el objetivo (misma forma que
  `GET /api/goals`). 400 si no cumple las reglas, 409 si el nombre ya existe o ya tiene 3.
- `PUT /api/goals/custom/{id}` — mismo cuerpo; 403 si `id` es un objetivo base, 404 si no es suyo, 400/409 como al crear.
- `DELETE /api/goals/custom/{id}` — borra el objetivo y su historial de preparación y responde `{"goalId": n}`: el
  objetivo elegido después. Si era el elegido, el usuario vuelve a su último objetivo base (`users.base_goal_id`) o al
  primero. El progreso por habilidad no se toca. 403/404 como al editar.

Persistencia: tablas `custom_goals` (FK `user_id → users` con `ON DELETE CASCADE`, índice único `(user_id, name_key)`)
y `custom_goal_skills` (FK a `custom_goals` con cascada y a `skills`), y la columna `users.base_goal_id`. `ddl-auto=update`
las crea en una base existente; `UserBaseGoalBackfill` da a las cuentas que ya tenían objetivo ese mismo como "base
anterior". Al eliminar la cuenta se borran también (y la base los borra en cascada si se borra la fila del usuario). Lo
prueban `CustomGoalFactoryTest`, `CustomGoalTest` y `LegacyCustomGoalsTest` (base de la versión anterior).

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
