@echo off
rem Cierra SkillMap (app de escritorio empaquetada) por nombre de proceso.
rem Uso: stop.bat [NombreDelEjecutable.exe]   (por defecto SkillMap.exe)
rem /F es necesario: la app no tiene ventana propia que reciba la orden de cierre.
rem H2 tolera el cierre forzado y el sistema suelta el cerrojo de instancia.
setlocal
set "APP_EXE=%~1"
if "%APP_EXE%"=="" set "APP_EXE=SkillMap.exe"

taskkill /IM "%APP_EXE%" /F >nul 2>&1
if errorlevel 1 (
    echo SkillMap no estaba abierta ^(%APP_EXE%^).
    exit /b 0
)
echo SkillMap cerrada.
exit /b 0
