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
./mvnw spring-boot:run
```
Base de datos H2 en memoria para desarrollo — no requiere instalar nada.
Consola en `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:skillmapdb`).

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
