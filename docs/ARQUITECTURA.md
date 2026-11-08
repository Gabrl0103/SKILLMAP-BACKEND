# Arquitectura de Cenit

Cenit (antes SkillMap) muestra qué tan preparado está alguien para un objetivo profesional y qué habilidades le faltan,
con la demanda sacada de ofertas de empleo reales. Hay dos repositorios:

- **SKILLMAP-FRONTEND**: HTML + JavaScript sin framework ni build (Tailwind en `vendor/`, copia local).
- **SKILLMAP-BACKEND** (este): `skillmap-api/` (Spring Boot) y `cenit-desktop/` (Electron).

## Estructura del repositorio

```
README.md            visión general y enlaces
docs/                este documento
skillmap-api/        backend Spring Boot (Java 17+, Maven wrapper)
  scripts/           build-app.ps1 (jpackage), stop.bat, ExportJobPostings.java
  Dockerfile         imagen del perfil cloud (Render)
cenit-desktop/       cascarón Electron (main.js, preload.js, loading.html)
  scripts/           build-desktop.ps1 (todo el instalable), make-icon.js
```

Cada carpeta conserva sus scripts: los `.ps1` calculan sus rutas desde su propia ubicación (`$PSScriptRoot`), y el
`Dockerfile` usa `skillmap-api/` como contexto de build. Moverlos a un `scripts/` común no aportaba nada y obligaba a
reescribir esas rutas.

## Capas del backend (`com.skillmap.api`)

| Capa | Paquetes | Contenido | Depende de |
|---|---|---|---|
| Dominio | `domain.model`, `domain.service`, `domain.factory`, `domain.event`, `domain.exception`, `domain.port`, `domain.repository` | Entidades y reglas puras (p. ej. `SkillMatcher`, `JobRecommender`), puertos e interfaces de repositorio | Nada (sin Spring ni JPA) |
| Aplicación | `application.service` (+ `impl`), `application.event` | Casos de uso: orquestan dominio y puertos | Dominio |
| Infraestructura | `infrastructure.persistence` (+ `entity`, `mapper`, `seed`), `infrastructure.ai`, `infrastructure.jobs`, `infrastructure.scheduling`, `infrastructure.security`, `infrastructure.desktop`, `infrastructure.config` | Adaptadores: JPA/H2, Gemini y servicio remoto, Remotive/Arbeitnow, tareas programadas, hash y tokens, arranque de escritorio, configuración de Spring | Dominio, aplicación |
| Presentación | `presentation.controller`, `dto`, `mapper`, `filter`, `exception` | API REST bajo `/api/**`, filtros de autenticación y tamaño, errores | Aplicación, dominio |

Reglas: el dominio no importa Spring, JPA ni otras capas; los adaptadores implementan puertos del dominio
(`JobPostingSource`, `JobSkillExtractor`, `GeminiKeyStore`, `PasswordHasher`, ...). Excepción conocida: `JobController`
y `AiController` usan clases de infraestructura directamente (ver Limitaciones).

## Flujo

```
Frontend (index.html + js/)
   │  fetch /api/**  (mismo origen; token Bearer)
   ▼
skillmap-api  ── H2 (%APPDATA%\SkillMap\data)
   │
   ├─ Ofertas: Remotive y Arbeitnow (HTTP, sin key) ─► demanda de cada habilidad (SkillMatcher, sin IA)
   │           + ofertas incluidas en seed/job-postings.json para no depender de la red al primer arranque
   │
   └─ Analizador (POST /api/analyzer/analyze) ─► SelectingJobSkillExtractor
          ├─ con key propia de Gemini ─► Gemini directo (cuota del usuario)
          └─ sin key ─► servicio de IA en Render (perfil cloud, POST /api/ai/extract-skills) ─► Gemini (key del servidor)
```

La key de Gemini sale de `GEMINI_API_KEY` o de `%APPDATA%\SkillMap\config.properties` (se guarda desde Ajustes); nunca
del repositorio ni del instalable, y los logs la tapan.

## Perfiles de ejecución

| Modo | Cómo se arranca | Qué cambia |
|---|---|---|
| Local / desarrollo | `./mvnw spring-boot:run` | Todo el backend; abre el navegador (Edge en modo app) salvo `--skillmap.desktop.open-browser=false` |
| Escritorio (Electron) | `Cenit.exe` lanza el backend oculto | Argumentos: puerto exacto elegido por Electron, sin navegador, apagado al cerrar stdin |
| Escritorio (jpackage, anterior) | `skillmap-api\dist\Cenit\Cenit.exe` | Como local; se cierra con `stop.bat` |
| `cloud` (Render) | `Dockerfile`, `--spring.profiles.active=cloud` | Solo `/api/ai/extract-skills` y `/api/health`; sin base de datos ni escaneo de componentes; cupo diario por IP |
| Tests | `./mvnw test` | `src/test/resources/config/application.properties`: sin llamadas a Render ni sincronización automática |

Las propiedades están comentadas por secciones en `skillmap-api/src/main/resources/application.properties` y
resumidas en `skillmap-api/README.md`.

## Persistencia

- H2 en modo archivo: `%APPDATA%\SkillMap\data\skillmap.mv.db` (`~/SkillMap` fuera de Windows). `ddl-auto=update`:
  crea lo que falta y conserva los datos; las migraciones puntuales van en código (`UserSkillDatesBackfill`).
- Al arrancar con la base vacía: `DataSeeder` (habilidades y objetivos) y `JobPostingSeeder` (ofertas incluidas).
- La carpeta se llama `SkillMap` a propósito (nombre anterior): quien actualiza no pierde sus datos.
- Logs en `%APPDATA%\SkillMap\logs`; perfil del navegador interno de Electron en `%APPDATA%\SkillMap\desktop`.

## Empaquetado

- **Electron** (`cenit-desktop\scripts\build-desktop.ps1`, el instalable actual): copia el frontend a
  `skillmap-api/src/main/resources/static`, compila el jar, crea un runtime de Java con `jlink`, hace una prueba de humo
  (y genera el archivo CDS para arrancar más rápido) y empaqueta con electron-builder: `dist\win-unpacked\Cenit.exe` y
  `dist\Cenit-Setup-<versión>.exe`.
- **jpackage** (`skillmap-api\scripts\build-app.ps1`, anterior): `skillmap-api\dist\Cenit\Cenit.exe`, abre Edge en
  modo app.
- **Render**: `skillmap-api/Dockerfile` (JDK 17, usuario sin privilegios), la key en el panel de Render.

## Construir y probar

- Tests del backend: `cd skillmap-api` y `./mvnw test` (con JDK 21; con JDK 26 Mockito puede fallar).
- Instalable: `powershell -ExecutionPolicy Bypass -File cenit-desktop\scripts\build-desktop.ps1` (requisitos en
  `cenit-desktop/README.md`).
- Frontend en desarrollo: backend con `./mvnw spring-boot:run` y Live Server sobre el repositorio del frontend.

## Decisiones

- **Arquitectura por capas con puertos**: el dominio se prueba sin Spring y las fuentes externas se cambian sin tocarlo.
- **H2 en archivo** en vez de un servidor de base de datos: la app es de escritorio y de un solo usuario por equipo.
- **Demanda sin IA**: `SkillMatcher` busca las habilidades en el texto de las ofertas; la IA solo se usa en el
  Analizador, cuando el usuario lo pide.
- **IA en Render**: quien no tiene key puede usar el Analizador; con key propia no se gasta el cupo compartido.
- **Electron solo como cascarón**: toda la lógica sigue en el backend; el frontend es el mismo que en el navegador.
- **Nombres heredados** (`com.skillmap.api`, `skillmap.*`, `%APPDATA%\SkillMap`): compatibilidad de datos.

## Limitaciones

- `JobController` y `AiController` dependen de clases de infraestructura (fuentes, sincronización automática,
  `CloudConfig`, `AiDailyLimiter`); lo correcto sería pasar por un servicio de aplicación.
- Sin migraciones versionadas (Flyway/Liquibase): `ddl-auto=update` y migraciones en código.
- El cupo de IA del servicio en Render está en memoria: se reinicia con el servicio. El plan gratis de Render duerme
  el servicio: tras un rato sin uso, el primer análisis sin key tarda 30-60 s más en despertarlo.
- Solo Windows x64 para el instalable; el instalador no está firmado.
- La URL del frontend en desarrollo y la ruta del repositorio del frontend en los scripts de build son rutas locales
  (se cambian con `-FrontendPath`).
