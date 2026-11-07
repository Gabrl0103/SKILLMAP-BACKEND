<#
.SYNOPSIS
    Genera Cenit de escritorio (Electron + backend Spring Boot incluido) con un solo comando.

.DESCRIPTION
    1. Copia el frontend (index.html, css, js, assets, vendor) a skillmap-api\src\main\resources\static.
    2. Compila el jar de Spring Boot (mvnw clean package -DskipTests).
    3. Prepara cenit-desktop\backend: runtime de Java propio (jlink, con java.exe) y el jar extraído
       (jarmode tools: arranca más rápido que el jar anidado).
    4. Prueba de humo del backend preparado (datos temporales, sin red): /api/health, /, registro, /api/skills y
       apagado al cerrar stdin. De paso genera backend\app\cenit-backend.jsa (CDS de las clases usadas): el primer
       arranque de la app baja de ~6-8 s a ~4 s. Con -SkipSmokeTest no se genera y la app arranca sin él.
    5. Instala las dependencias de Electron (npm ci) y empaqueta con electron-builder:
       dist\win-unpacked\Cenit.exe (carpeta sin instalar) y dist\Cenit-Setup-<versión>.exe (instalador NSIS).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File cenit-desktop\scripts\build-desktop.ps1
    powershell -ExecutionPolicy Bypass -File cenit-desktop\scripts\build-desktop.ps1 -FrontendPath D:\repos\SKILLMAP-FRONTEND
#>
[CmdletBinding()]
param(
    # Repositorio del frontend.
    [string]$FrontendPath = 'C:\Users\Administrator\Desktop\SKILLMAP FRONTEND\SKILLMAP-FRONTEND',
    # JDK con jlink y jmods. Su runtime es el que lleva la app (se usa también para compilar).
    [string]$JdkHome = 'C:\Program Files\openjdk-26_windows-x64_bin\jdk-26',
    # Solo prepara cenit-desktop\backend (para probar con "npm start"), sin empaquetar.
    [switch]$StageOnly,
    # Omite la prueba de humo del backend (paso 4).
    [switch]$SkipSmokeTest
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$DesktopDir = Split-Path -Parent $PSScriptRoot
$RepoDir = Split-Path -Parent $DesktopDir
$ApiDir = Join-Path $RepoDir 'skillmap-api'
$StaticDir = Join-Path $ApiDir 'src\main\resources\static'
$BackendDir = Join-Path $DesktopDir 'backend'
$RuntimeDir = Join-Path $BackendDir 'runtime'
$BackendAppDir = Join-Path $BackendDir 'app'
$BackendJarName = 'cenit-backend.jar'
$DistDir = Join-Path $DesktopDir 'dist'
$FrontendItems = @('index.html', 'css', 'js', 'assets', 'vendor')
# Los mismos módulos que scripts\build-app.ps1 (jpackage): todo java.se más lo que usan Hibernate, HTTPS y H2.
$Modules = @(
    'java.se', 'jdk.unsupported', 'jdk.crypto.ec', 'jdk.crypto.mscapi', 'jdk.charsets',
    'jdk.localedata', 'jdk.zipfs', 'jdk.naming.dns', 'jdk.management', 'jdk.net'
)

function Write-Step([string]$Message) {
    Write-Host ''
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Get-FolderSizeMB([string]$Path) {
    $bytes = (Get-ChildItem -Path $Path -Recurse -File -Force | Measure-Object -Property Length -Sum).Sum
    return [math]::Round($bytes / 1MB, 1)
}

function Invoke-Native([string]$What, [scriptblock]$Command) {
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$What terminó con código $LASTEXITCODE"
    }
}

$started = Get-Date

# --- Comprobaciones previas -------------------------------------------------------------
$jlink = Join-Path $JdkHome 'bin\jlink.exe'
$jdkJava = Join-Path $JdkHome 'bin\java.exe'
if (-not (Test-Path $jlink) -or -not (Test-Path (Join-Path $JdkHome 'jmods\java.base.jmod'))) {
    throw "No se encontró jlink con jmods en '$JdkHome'. Indica el JDK con -JdkHome."
}
if (-not (Test-Path (Join-Path $FrontendPath 'index.html'))) {
    throw "No se encontró index.html en el frontend '$FrontendPath'. Indica la ruta con -FrontendPath."
}
if (-not $StageOnly) {
    if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
        throw 'No se encontró npm: instala Node.js 20 o superior.'
    }
}
$running = Get-Process -Name 'Cenit' -ErrorAction SilentlyContinue |
    Where-Object { $_.Path -and $_.Path.StartsWith($DesktopDir, [StringComparison]::OrdinalIgnoreCase) }
if ($running) {
    throw "Cenit de cenit-desktop\dist está abierta. Ciérrala y vuelve a ejecutar el script."
}

# --- 1. Frontend -> static ----------------------------------------------------------------
Write-Step "Copiando el frontend desde $FrontendPath"
$missing = $FrontendItems | Where-Object { -not (Test-Path (Join-Path $FrontendPath $_)) }
if ($missing) {
    throw "Faltan en el frontend: $($missing -join ', ')"
}
if (Test-Path $StaticDir) {
    Remove-Item -Recurse -Force $StaticDir
}
New-Item -ItemType Directory -Path $StaticDir | Out-Null
foreach ($item in $FrontendItems) {
    Copy-Item -Path (Join-Path $FrontendPath $item) -Destination $StaticDir -Recurse -Force
}
Write-Host "  $(@(Get-ChildItem -Path $StaticDir -Recurse -File).Count) archivos en skillmap-api\src\main\resources\static"

# --- 2. Jar de Spring Boot ----------------------------------------------------------------
Write-Step 'Compilando el backend (mvnw clean package -DskipTests)'
$previousJavaHome = $env:JAVA_HOME
$env:JAVA_HOME = $JdkHome
Push-Location $ApiDir
try {
    Invoke-Native 'Maven' { & (Join-Path $ApiDir 'mvnw.cmd') -q clean package -DskipTests }
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJavaHome
}
$jar = Get-ChildItem -Path (Join-Path $ApiDir 'target') -Filter '*.jar' |
    Where-Object { $_.Name -notmatch '(-sources|-javadoc|-plain)\.jar$' } |
    Select-Object -First 1
if (-not $jar) {
    throw 'No se generó el jar en skillmap-api\target\'
}
Write-Host "  $($jar.Name) ($([math]::Round($jar.Length / 1MB, 1)) MB)"

# --- 3. Backend para Electron: runtime + jar extraído ----------------------------------------
Write-Step 'Preparando cenit-desktop\backend (jlink + jar extraído)'
if (Test-Path $BackendDir) {
    Remove-Item -Recurse -Force $BackendDir
}
New-Item -ItemType Directory -Path $BackendAppDir -Force | Out-Null
# Con java.exe (sin --strip-native-commands) para lanzarlo desde Electron; con el archivo CDS base
# (--generate-cds-archive), sobre el que se apoya el de la app (paso 4).
Invoke-Native 'jlink' {
    & $jlink --add-modules ($Modules -join ',') --output $RuntimeDir `
        --strip-debug --no-man-pages --no-header-files --compress zip-6 --generate-cds-archive
}
Write-Host "  runtime: $(Get-FolderSizeMB $RuntimeDir) MB"

# Spring Boot 3.3+: "extract" deja el jar de la app y lib\ al lado (Class-Path relativo).
$extractDir = Join-Path $ApiDir 'target\desktop-extract'
if (Test-Path $extractDir) {
    Remove-Item -Recurse -Force $extractDir
}
& $jdkJava -Djarmode=tools -jar $jar.FullName extract --destination $extractDir 2>&1 | Out-Host
$extractedJar = Join-Path $extractDir $jar.Name
if ($LASTEXITCODE -eq 0 -and (Test-Path $extractedJar)) {
    Copy-Item -Path (Join-Path $extractDir 'lib') -Destination $BackendAppDir -Recurse
    Copy-Item -Path $extractedJar -Destination (Join-Path $BackendAppDir $BackendJarName)
    Write-Host "  jar extraído: $(@(Get-ChildItem (Join-Path $BackendAppDir 'lib')).Count) librerías en app\lib"
} else {
    Write-Warning 'No se pudo extraer el jar (jarmode tools); se usa el jar ejecutable completo.'
    Copy-Item -Path $jar.FullName -Destination (Join-Path $BackendAppDir $BackendJarName)
}
Write-Host "  backend total: $(Get-FolderSizeMB $BackendDir) MB"

# --- 4. Prueba de humo del backend preparado --------------------------------------------------
if ($SkipSmokeTest) {
    Write-Step 'Prueba de humo omitida (-SkipSmokeTest)'
} else {
    Write-Step 'Prueba de humo del backend (datos temporales, sin red)'
    $testHome = Join-Path ([IO.Path]::GetTempPath()) ('cenit-desktop-smoke-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $testHome | Out-Null
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $testPort = $listener.LocalEndpoint.Port
    $listener.Stop()
    $unreachable = 'http://127.0.0.1:9'
    $psi = [Diagnostics.ProcessStartInfo]::new((Join-Path $RuntimeDir 'bin\java.exe'))
    $cdsArchive = Join-Path $BackendAppDir 'cenit-backend.jsa'
    # Mismas opciones de la JVM que main.js; al salir vuelca las clases cargadas en el archivo CDS.
    $psi.Arguments = (@(
        '-Xms64m', '-Xmx512m', '-XX:+UseSerialGC', '-XX:TieredStopAtLevel=1', '--enable-native-access=ALL-UNNAMED',
        "-XX:ArchiveClassesAtExit=`"$cdsArchive`"",
        '-jar', "`"$(Join-Path $BackendAppDir $BackendJarName)`"",
        "--server.port=$testPort", '--server.address=127.0.0.1',
        '--skillmap.desktop.open-browser=false', '--skillmap.desktop.strict-port=true',
        '--skillmap.desktop.exit-on-stdin-close=true', "`"--skillmap.home=$testHome`"",
        '--jobs.sync.cron=-', "--jobs.remotive.base-url=$unreachable", "--jobs.arbeitnow.base-url=$unreachable",
        "--gemini.base-url=$unreachable", "--skillmap.ai.remote-url=$unreachable",
        '--skillmap.sync.remotive.enabled=false', '--spring.jpa.show-sql=false'
    ) -join ' ')
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.EnvironmentVariables.Remove('GEMINI_API_KEY')
    $proc = [Diagnostics.Process]::Start($psi)
    # Vaciar las tuberías en segundo plano para que el proceso no se bloquee al escribir.
    $outTask = $proc.StandardOutput.ReadToEndAsync()
    $errTask = $proc.StandardError.ReadToEndAsync()
    $failure = $null
    $sw = [Diagnostics.Stopwatch]::StartNew()
    try {
        $ok = $false
        while ($sw.Elapsed.TotalSeconds -lt 90) {
            if ($proc.HasExited) {
                throw "el backend terminó al arrancar (código $($proc.ExitCode))"
            }
            try {
                $health = Invoke-RestMethod -Uri "http://127.0.0.1:$testPort/api/health" -TimeoutSec 2
                if ($health.status -eq 'ok') { $ok = $true; break }
            } catch { }
            Start-Sleep -Milliseconds 300
        }
        if (-not $ok) {
            throw 'el backend no respondió /api/health en 90 s'
        }
        Write-Host "  /api/health ok en $([math]::Round($sw.Elapsed.TotalSeconds, 1)) s"
        $index = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$testPort/" -TimeoutSec 10
        if ($index.Content -notmatch 'js/dashboard\.js') {
            throw '/ no devolvió el index.html del frontend'
        }
        Write-Host '  / sirve el frontend'
        # Un usuario temporal (solo existe en la base de esta prueba): ejercita la API y sus clases para el CDS.
        $register = @{
            name = 'Prueba de humo'
            email = "smoke-$([guid]::NewGuid().ToString('N'))@cenit.local"
            password = [guid]::NewGuid().ToString('N')
        } | ConvertTo-Json
        $session = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$testPort/api/auth/register" `
            -ContentType 'application/json' -Body $register -TimeoutSec 10
        $auth = @{ Authorization = "Bearer $($session.token)" }
        # Asignar antes de contar: en PowerShell 5.1, @(Invoke-RestMethod ...) da un único elemento con todo el array.
        $skills = Invoke-RestMethod -Uri "http://127.0.0.1:$testPort/api/skills" -Headers $auth -TimeoutSec 10
        $skills = @($skills)
        if ($skills.Count -lt 1) {
            throw '/api/skills respondió sin habilidades'
        }
        foreach ($api in @('/api/auth/me', '/api/goals', '/api/settings', '/api/jobs/stats')) {
            Invoke-RestMethod -Uri "http://127.0.0.1:$testPort$api" -Headers $auth -TimeoutSec 10 | Out-Null
        }
        Write-Host "  Registro y API con sesión: ok ($($skills.Count) habilidades)"
        # Cerrar stdin = cerrar la ventana de Electron: debe apagarse solo y de forma ordenada (y volcar el CDS).
        $proc.StandardInput.Close()
        if (-not $proc.WaitForExit(60000)) {
            throw 'el backend no se detuvo al cerrar stdin (60 s)'
        }
        Write-Host "  Se detuvo al cerrar stdin (código $($proc.ExitCode))"
        if (Test-Path $cdsArchive) {
            Write-Host "  Archivo CDS: $([math]::Round((Get-Item $cdsArchive).Length / 1MB, 1)) MB"
        } else {
            Write-Warning 'No se generó el archivo CDS: la app funciona igual, pero arranca algo más lenta.'
        }
    } catch {
        $failure = $_
    } finally {
        if (-not $proc.HasExited) {
            & taskkill /PID $proc.Id /T /F | Out-Null
            $proc.WaitForExit(10000) | Out-Null
        }
    }
    if ($failure) {
        Write-Host "  Datos de la prueba en $testHome" -ForegroundColor Yellow
        Write-Host ($errTask.Result + $outTask.Result).Split("`n")[-40..-1] -Separator "`n"
        throw "La prueba de humo del backend falló: $failure"
    }
    for ($i = 0; $i -lt 10 -and (Test-Path $testHome); $i++) {
        try { Remove-Item -Recurse -Force $testHome -ErrorAction Stop } catch { Start-Sleep -Milliseconds 500 }
    }
}

if ($StageOnly) {
    Write-Step 'Listo (-StageOnly): backend preparado en cenit-desktop\backend. Prueba con: npm start'
    return
}

# --- 5. Electron ------------------------------------------------------------------------------
Write-Step 'Instalando dependencias de Electron'
Push-Location $DesktopDir
try {
    if (Test-Path (Join-Path $DesktopDir 'package-lock.json')) {
        Invoke-Native 'npm ci' { & npm.cmd ci --no-audit --no-fund }
    } else {
        Invoke-Native 'npm install' { & npm.cmd install --no-audit --no-fund }
    }

    Write-Step 'Empaquetando con electron-builder'
    if (Test-Path $DistDir) {
        Remove-Item -Recurse -Force $DistDir
    }
    # Sin certificado de firma: electron-builder no intenta firmar ni buscar uno.
    $env:CSC_IDENTITY_AUTO_DISCOVERY = 'false'
    Invoke-Native 'electron-builder' { & npm.cmd run dist }
} finally {
    Pop-Location
}

$unpacked = Join-Path $DistDir 'win-unpacked'
$exe = Join-Path $unpacked 'Cenit.exe'
if (-not (Test-Path $exe)) {
    throw "electron-builder no generó $exe"
}
$installer = Get-ChildItem -Path $DistDir -Filter 'Cenit-Setup-*.exe' | Select-Object -First 1

Write-Step "Listo en $([math]::Round(((Get-Date) - $started).TotalMinutes, 1)) min"
Write-Host "  Sin instalar: $exe ($(Get-FolderSizeMB $unpacked) MB)"
Write-Host "    de ellos, backend (Java + jar): $(Get-FolderSizeMB (Join-Path $unpacked 'resources\backend')) MB"
if ($installer) {
    Write-Host "  Instalador:   $($installer.FullName) ($([math]::Round($installer.Length / 1MB, 1)) MB)"
}
