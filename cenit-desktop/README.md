# cenit-desktop

App de escritorio de Windows de **Cenit**: `Cenit.exe` abre la app en su propia ventana (título "Cenit", icono propio,
sin barra del navegador, sin consola). Electron es solo el cascarón; todo lo hace el backend Spring Boot de
`skillmap-api/`, que va incluido dentro (runtime de Java propio + jar) y se lanza oculto.

## Construir

Requisitos (solo para construir; quien usa la app no necesita nada instalado):

- Windows 10/11 x64.
- **Node.js 20+** con npm (probado con Node 24).
- **JDK 21+ con `jlink` y `jmods`** (por defecto `C:\Program Files\openjdk-26_windows-x64_bin\jdk-26`; otro con `-JdkHome`).
- El repositorio del frontend (por defecto `C:\Users\Administrator\Desktop\SKILLMAP FRONTEND\SKILLMAP-FRONTEND`;
  otro con `-FrontendPath`).
- Conexión a internet la primera vez (npm descarga Electron y las herramientas de electron-builder).

Un solo comando, desde la raíz del repositorio:

```powershell
powershell -ExecutionPolicy Bypass -File cenit-desktop\scripts\build-desktop.ps1
```

Hace, en orden: copia el frontend a `skillmap-api\src\main\resources\static`, compila el jar (`mvnw clean package
-DskipTests`), crea `cenit-desktop\backend` (runtime con `jlink` + jar extraído), prueba ese backend (datos temporales,
sin red: `/api/health`, `/` y apagado al cerrar stdin), instala dependencias (`npm ci`) y empaqueta con electron-builder.

Resultado en `cenit-desktop\dist\`:

- `win-unpacked\Cenit.exe`: la app sin instalar. Se comprime la carpeta `win-unpacked` entera y se ejecuta `Cenit.exe`.
- `Cenit-Setup-<versión>.exe`: instalador (NSIS) con accesos directos en el escritorio y el menú Inicio.

Opciones: `-StageOnly` (solo prepara `backend\` para probar con `npm start`), `-SkipSmokeTest`.
La versión y el nombre del producto salen de `package.json`. El icono (`build\icon.ico`) se genera desde la estrella del
logo con `npm run icon` (solo hace falta si cambia el logo).

`dist\`, `node_modules\` y `backend\` no se suben a git (`.gitignore`).

## Cómo funciona

1. `Cenit.exe` toma un puerto libre de `127.0.0.1` (18080–18089, el mismo entre arranques para que la sesión guardada
   en el navegador interno siga valiendo; si están todos ocupados, uno al azar).
2. Lanza `resources\backend\runtime\bin\java.exe -jar resources\backend\app\cenit-backend.jar` oculto
   (`windowsHide`), sin abrir el navegador (`--skillmap.desktop.open-browser=false`), con ese puerto exacto
   (`--skillmap.desktop.strict-port=true`) y atado a la ventana por stdin (`--skillmap.desktop.exit-on-stdin-close=true`).
3. Muestra "Preparando tu ruta…" hasta que `GET /api/health` responde y entonces carga `http://127.0.0.1:PUERTO`.
4. Al cerrar la ventana: cierra stdin (el backend se apaga ordenadamente y H2 guarda), espera hasta 8 s y, si sigue
   vivo, `taskkill /PID <pid> /T /F`. Si Electron muere de golpe, el backend también se cierra (stdin se cierra solo).
   No queda ningún `java` corriendo.

Además:

- **Una sola instancia**: abrir `Cenit.exe` otra vez enfoca la ventana existente.
- **Enlaces externos** (`target=_blank` u otro origen) se abren en el navegador del sistema; la ventana no navega
  fuera de la app.
- **Sin atrás/adelante**: los botones laterales del mouse, Alt+←/→ y las teclas de navegador no hacen nada; el
  historial se vacía tras cada carga, así que la pantalla de carga nunca queda "detrás" de la app.
- **Exportar CV**: el preload expone solo `cenitDesktop.exportPdf` y main.js genera el PDF con `printToPDF` (A4, sin
  fondos, reglas `@media print` del CV) y lo guarda donde elija el usuario (por defecto `Documentos\CV-<nombre>.pdf`).
- **Sin menú, sin DevTools ni recarga** (F5, Ctrl+R, F12, Ctrl+Shift+I) en el build empaquetado. Menú contextual solo
  en campos de texto (cortar, copiar, pegar, seleccionar todo).
- **Errores**: si el backend no responde en 60 s, no arranca o se cae, sale una pantalla de error con "Reintentar" y
  "Cerrar" y un enlace a la carpeta de logs. Si otra copia de Cenit (p. ej. la versión anterior, `dist\Cenit\Cenit.exe`
  de jpackage) usa los mismos datos, lo dice.
- La ventana arranca en 1440×900 (mínimo 1100×700), ajustada al área útil si la pantalla es más pequeña.

## Datos y logs

- **Datos** (igual que antes, no se mueven): `%APPDATA%\SkillMap` — base H2 en `data\`, `config.properties` (key de
  Gemini si se guardó desde Ajustes).
- **Logs** en `%APPDATA%\SkillMap\logs`:
  - `skillmap.log`: el del backend (con rotación, como siempre).
  - `desktop.log`: arranques, puerto, tiempos, errores de Electron (rota a `desktop.1.log` al pasar de 5 MB).
  - `backend-console.log`: salida de consola del backend del último arranque (útil si Java no llega a iniciar).
- **Perfil del navegador interno** (sesión iniciada, idioma, caché): `%APPDATA%\SkillMap\desktop`.
- Para probar sin tocar los datos reales: `Cenit.exe --cenit-home=C:\Temp\cenit-prueba` (o la variable `CENIT_HOME`).

## stop.bat

Ya no hace falta: al cerrar la ventana se detiene todo. `skillmap-api\scripts\stop.bat` sigue funcionando para la app
anterior de jpackage (`skillmap-api\dist\Cenit\Cenit.exe`), que se sigue generando con `build-app.ps1`.

## Desarrollo

```powershell
powershell -ExecutionPolicy Bypass -File cenit-desktop\scripts\build-desktop.ps1 -StageOnly
cd cenit-desktop
npm install
npm start          # Electron sin empaquetar (con DevTools), usando cenit-desktop\backend
```
