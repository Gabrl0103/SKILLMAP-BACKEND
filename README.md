# SKILLMAP-BACKEND
Repositorio backend de **Cenit** (antes SkillMap): tu ruta hacia el cenit de tu carrera.

El repositorio, el paquete `com.skillmap.api` y las propiedades `skillmap.*` conservan el nombre anterior para no romper
integraciones ni datos guardados.

- `skillmap-api/`: backend Spring Boot (ver `skillmap-api/README.md`).
- `cenit-desktop/`: app de escritorio de Windows (Electron): `Cenit.exe` abre Cenit en su propia ventana, con el
  backend incluido. Se construye con un solo comando:
  `powershell -ExecutionPolicy Bypass -File cenit-desktop\scripts\build-desktop.ps1` (ver `cenit-desktop/README.md`).
- `docs/`: arquitectura, flujo frontend → backend → ofertas e IA, perfiles de ejecución, persistencia, empaquetado,
  decisiones y limitaciones ([docs/ARQUITECTURA.md](docs/ARQUITECTURA.md)).

El frontend vive en el repositorio **SKILLMAP-FRONTEND**; los scripts de build lo copian dentro del backend.
