@echo off
rem Cierra SkillMap (app de escritorio empaquetada): solo el ejecutable que está en la misma
rem carpeta que este archivo (dist\SkillMap). Otras copias de SkillMap.exe y los java.exe no se tocan.
rem Uso: stop.bat [NombreDelEjecutable.exe]   (por defecto SkillMap.exe)
rem Se fuerza el cierre: la app no tiene ventana propia que reciba la orden de cierre.
rem H2 tolera el cierre forzado y el sistema suelta el cerrojo de instancia.
setlocal
set "APP_EXE=%~1"
if "%APP_EXE%"=="" set "APP_EXE=SkillMap.exe"
set "APP_PATH=%~dp0%APP_EXE%"

rem taskkill no filtra por ruta: se busca el proceso por nombre y se compara la ruta completa.
rem Rutas por variable de entorno para no pelear con espacios ni comillas.
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$target = [IO.Path]::GetFullPath($env:APP_PATH);" ^
  "$name = [IO.Path]::GetFileNameWithoutExtension($env:APP_EXE);" ^
  "$procs = @(Get-Process -Name $name -ErrorAction SilentlyContinue | Where-Object { $_.Path -and ([IO.Path]::GetFullPath($_.Path) -ieq $target) });" ^
  "if ($procs.Count -eq 0) { exit 1 };" ^
  "try { $procs | Stop-Process -Force -ErrorAction Stop } catch { exit 2 };" ^
  "exit 0"
if errorlevel 2 goto failed
if errorlevel 1 goto notrunning
echo SkillMap cerrada.
exit /b 0

:notrunning
echo SkillMap no estaba abierta (%APP_PATH%).
exit /b 0

:failed
echo No se pudo cerrar SkillMap (%APP_PATH%).
exit /b 1
