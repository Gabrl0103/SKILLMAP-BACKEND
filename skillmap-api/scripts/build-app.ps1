<#
.SYNOPSIS
    Genera la app de escritorio de Windows (dist\Cenit\Cenit.exe) con jpackage.

.DESCRIPTION
    1. Copia el frontend (index.html, css, js, assets, vendor) a src\main\resources\static.
    2. Compila el jar de Spring Boot (mvnw clean package -DskipTests).
    3. Crea la imagen de la app con jpackage (sin ventana de consola, runtime propio).
    4. Copia scripts\stop.bat junto al ejecutable.
    5. Prueba de humo: arranca Cenit.exe con datos temporales, en otro puerto, sin abrir
       el navegador, sin GEMINI_API_KEY y sin llamar a Remotive, Arbeitnow, Gemini ni al servicio de IA en la nube;
       comprueba /, /api/health y, con un usuario temporal, /api/skills, las ofertas incluidas (/api/jobs/stats) y
       que no hay key de Gemini (/api/settings).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\build-app.ps1
    powershell -ExecutionPolicy Bypass -File scripts\build-app.ps1 -FrontendPath D:\repos\SKILLMAP-FRONTEND
#>
[CmdletBinding()]
param(
    # Repositorio del frontend.
    [string]$FrontendPath = 'C:\Users\Administrator\Desktop\SKILLMAP FRONTEND\SKILLMAP-FRONTEND',
    # JDK con jpackage. Su runtime es el que lleva la app (se usa también para compilar).
    [string]$JdkHome = 'C:\Program Files\openjdk-26_windows-x64_bin\jdk-26',
    # Omite la prueba de humo del paso 5.
    [switch]$SkipSmokeTest
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$AppName = 'Cenit'
$ProjectDir = Split-Path -Parent $PSScriptRoot
$StaticDir = Join-Path $ProjectDir 'src\main\resources\static'
$DistDir = Join-Path $ProjectDir 'dist'
$AppDir = Join-Path $DistDir $AppName
$AppExe = Join-Path $AppDir "$AppName.exe"
# Lo que necesita la app en el navegador; docs, .git y notas del frontend se quedan fuera.
$FrontendItems = @('index.html', 'css', 'js', 'assets', 'vendor')
# Spring Boot 3.2+: el lanzador del jar ejecutable está en org.springframework.boot.loader.launch.
$MainClass = 'org.springframework.boot.loader.launch.JarLauncher'
# java.se trae todos los módulos estándar (java.sql, java.naming, java.desktop, java.management,
# java.instrument, java.net.http...). Además: jdk.unsupported (sun.misc.Unsafe, lo usan Hibernate y
# ByteBuddy), criptografía y almacén de certificados de Windows (HTTPS a Remotive/Gemini), juegos de
# caracteres y locales extra, zipfs, DNS de JNDI y la gestión de la JVM.
$Modules = @(
    'java.se', 'jdk.unsupported', 'jdk.crypto.ec', 'jdk.crypto.mscapi', 'jdk.charsets',
    'jdk.localedata', 'jdk.zipfs', 'jdk.naming.dns', 'jdk.management', 'jdk.net'
)

function Write-Step([string]$Message) {
    Write-Host ''
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Get-RunningApp {
    # Solo instancias de ESTE ejecutable (dist\Cenit\Cenit.exe), nunca otros java.exe.
    Get-Process -Name $AppName -ErrorAction SilentlyContinue |
        Where-Object { $_.Path -and ($_.Path -ieq $AppExe) }
}

# --- Comprobaciones previas -------------------------------------------------------------
$jpackage = Join-Path $JdkHome 'bin\jpackage.exe'
if (-not (Test-Path $jpackage)) {
    throw "No se encontró jpackage en '$JdkHome'. Indica el JDK con -JdkHome."
}
if (-not (Test-Path (Join-Path $JdkHome 'jmods\java.base.jmod'))) {
    throw "El JDK '$JdkHome' no tiene la carpeta jmods: jpackage no puede crear el runtime."
}
if (-not (Test-Path (Join-Path $FrontendPath 'index.html'))) {
    throw "No se encontró index.html en el frontend '$FrontendPath'. Indica la ruta con -FrontendPath."
}
if (Get-RunningApp) {
    throw "$AppExe está abierta. Ciérrala (dist\$AppName\stop.bat) y vuelve a ejecutar el script."
}

# --- 1. Frontend -> static ----------------------------------------------------------------
Write-Step "Copiando el frontend desde $FrontendPath"
$missing = $FrontendItems | Where-Object { -not (Test-Path (Join-Path $FrontendPath $_)) }
if ($missing) {
    throw "Faltan en el frontend: $($missing -join ', ')"
}
# static\ es generada (está en .gitignore): se rehace para no arrastrar archivos borrados.
if (Test-Path $StaticDir) {
    Remove-Item -Recurse -Force $StaticDir
}
New-Item -ItemType Directory -Path $StaticDir | Out-Null
foreach ($item in $FrontendItems) {
    Copy-Item -Path (Join-Path $FrontendPath $item) -Destination $StaticDir -Recurse -Force
}
$copied = @(Get-ChildItem -Path $StaticDir -Recurse -File).Count
Write-Host "  $copied archivos en src\main\resources\static"

# --- 2. Jar de Spring Boot ----------------------------------------------------------------
Write-Step 'Compilando (mvnw clean package -DskipTests)'
$previousJavaHome = $env:JAVA_HOME
$env:JAVA_HOME = $JdkHome
Push-Location $ProjectDir
try {
    & (Join-Path $ProjectDir 'mvnw.cmd') -q clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Maven terminó con código $LASTEXITCODE"
    }
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJavaHome
}
$jar = Get-ChildItem -Path (Join-Path $ProjectDir 'target') -Filter '*.jar' |
    Where-Object { $_.Name -notmatch '(-sources|-javadoc|-plain)\.jar$' } |
    Select-Object -First 1
if (-not $jar) {
    throw 'No se generó el jar en target\'
}
# Versión para Windows: solo números (1 a 3 partes), sin -SNAPSHOT.
$appVersion = if ($jar.BaseName -match '-(\d+(\.\d+){0,2})') { $Matches[1] } else { '1.0.0' }
Write-Host "  $($jar.Name) (versión $appVersion)"

# --- 3. jpackage --------------------------------------------------------------------------
Write-Step "Creando la imagen de la app con jpackage ($JdkHome)"
# jpackage copia toda la carpeta --input: se le da una con solo el jar.
$inputDir = Join-Path $ProjectDir 'target\jpackage-input'
if (Test-Path $inputDir) {
    Remove-Item -Recurse -Force $inputDir
}
New-Item -ItemType Directory -Path $inputDir | Out-Null
Copy-Item -Path $jar.FullName -Destination $inputDir
if (Test-Path $AppDir) {
    Remove-Item -Recurse -Force $AppDir
}
New-Item -ItemType Directory -Path $DistDir -Force | Out-Null

$jpackageArgs = @(
    '--type', 'app-image',
    '--name', $AppName,
    '--app-version', $appVersion,
    '--vendor', 'Cenit',
    '--description', 'Cenit: tu ruta hacia el cenit de tu carrera',
    '--dest', $DistDir,
    '--input', $inputDir,
    '--main-jar', $jar.Name,
    '--main-class', $MainClass,
    '--add-modules', ($Modules -join ','),
    # Tomcat carga su librería nativa con System.load: sin esto Java 22+ avisa en cada arranque.
    '--java-options', '--enable-native-access=ALL-UNNAMED'
    # Sin --win-console: el ejecutable no abre ventana de consola.
)
& $jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) {
    throw "jpackage terminó con código $LASTEXITCODE"
}
if (-not (Test-Path $AppExe)) {
    throw "jpackage no generó $AppExe"
}

# --- 4. stop.bat --------------------------------------------------------------------------
Write-Step 'Copiando stop.bat'
$stopBat = Join-Path $PSScriptRoot 'stop.bat'
$stopContent = Get-Content -Raw -Path $stopBat
$exeName = Split-Path -Leaf $AppExe
if ($stopContent -notmatch [regex]::Escape("set `"APP_EXE=$exeName`"")) {
    throw "scripts\stop.bat no apunta a $exeName por defecto (se espera: set `"APP_EXE=$exeName`")"
}
Copy-Item -Path $stopBat -Destination $AppDir
Write-Host "  stop.bat cierra $exeName"

# --- 5. Prueba de humo --------------------------------------------------------------------
if ($SkipSmokeTest) {
    Write-Step 'Prueba de humo omitida (-SkipSmokeTest)'
} else {
    Write-Step 'Prueba de humo de Cenit.exe'
    # Datos temporales: no se toca %APPDATA%\SkillMap, la carpeta de datos de la app (ni la base, ni config.properties, ni los logs).
    $testHome = Join-Path ([IO.Path]::GetTempPath()) ("skillmap-smoke-" + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $testHome | Out-Null
    $portFile = Join-Path $testHome 'data\instance.port'
    $logFile = Join-Path $testHome 'logs\skillmap.log'

    # Un puerto libre que no sea el del backend de desarrollo (8080 y siguientes).
    do {
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
        $listener.Start()
        $testPort = $listener.LocalEndpoint.Port
        $listener.Stop()
    } while ($testPort -ge 8080 -and $testPort -le 8099)

    $unreachable = 'http://127.0.0.1:9'
    $appArgs = @(
        "--skillmap.home=`"$testHome`"",
        "--server.port=$testPort",
        '--skillmap.desktop.open-browser=false',
        # Nada de red: sin sincronización inicial ni diaria, y fuentes e IA apuntando a un puerto cerrado.
        '--jobs.sync.on-first-start=false',
        '--jobs.sync.cron=-',
        "--jobs.remotive.base-url=$unreachable",
        "--jobs.arbeitnow.base-url=$unreachable",
        "--gemini.base-url=$unreachable",
        "--skillmap.ai.remote-url=$unreachable",
        '--spring.jpa.show-sql=false'
    )
    $outLog = Join-Path $testHome 'stdout.log'
    $errLog = Join-Path $testHome 'stderr.log'
    # Como en un equipo recién instalado: sin GEMINI_API_KEY (el proceso hereda el entorno de este script).
    $previousGeminiKey = $env:GEMINI_API_KEY
    $env:GEMINI_API_KEY = $null
    try {
        $process = Start-Process -FilePath $AppExe -ArgumentList $appArgs -PassThru `
            -RedirectStandardOutput $outLog -RedirectStandardError $errLog
    } finally {
        $env:GEMINI_API_KEY = $previousGeminiKey
    }
    $failure = $null
    try {
        # DesktopLauncher escribe instance.port cuando la app está lista (datos sembrados incluidos).
        $deadline = (Get-Date).AddSeconds(120)
        while (-not (Test-Path $portFile)) {
            if ($process.HasExited) {
                throw "Cenit.exe terminó al arrancar (código $($process.ExitCode))"
            }
            if ((Get-Date) -gt $deadline) {
                throw 'Cenit.exe no estuvo lista en 120 s'
            }
            Start-Sleep -Milliseconds 500
        }
        $port = [int](Get-Content -Raw $portFile).Trim()
        $base = "http://127.0.0.1:$port"
        Write-Host "  Arrancó (PID $($process.Id)) en $base con datos en $testHome"
        if (-not (Test-Path (Join-Path $testHome 'data\skillmap.mv.db'))) {
            throw 'La base de datos no se creó en la carpeta temporal'
        }

        $index = Invoke-WebRequest -UseBasicParsing -Uri "$base/" -TimeoutSec 10
        if ($index.StatusCode -ne 200 -or $index.Content -notmatch 'js/dashboard\.js') {
            throw "/ no devolvió el index.html del frontend (HTTP $($index.StatusCode))"
        }
        foreach ($asset in @('css/styles.css', 'vendor/tailwind.js', 'js/dashboard.js')) {
            $r = Invoke-WebRequest -UseBasicParsing -Uri "$base/$asset" -TimeoutSec 10
            if ($r.StatusCode -ne 200) {
                throw "/$asset devolvió HTTP $($r.StatusCode)"
            }
        }
        Write-Host '  / sirve la app (index.html, css, js, vendor)'

        $health = Invoke-RestMethod -Uri "$base/api/health" -TimeoutSec 10
        if ($health.status -ne 'ok') {
            throw '/api/health no respondió status=ok'
        }
        Write-Host '  /api/health responde ok'

        # El resto de /api exige sesión: un usuario temporal que solo existe en la base de esta prueba.
        $register = @{
            name = 'Prueba de humo'
            email = "smoke-$([guid]::NewGuid().ToString('N'))@skillmap.local"
            password = [guid]::NewGuid().ToString('N')
        } | ConvertTo-Json
        $session = Invoke-RestMethod -Method Post -Uri "$base/api/auth/register" -ContentType 'application/json' `
            -Body $register -TimeoutSec 10
        $auth = @{ Authorization = "Bearer $($session.token)" }

        $skills = Invoke-RestMethod -Uri "$base/api/skills" -Headers $auth -TimeoutSec 10
        $count = @($skills).Count
        if ($count -lt 1) {
            throw '/api/skills respondió sin habilidades'
        }
        Write-Host "  /api/skills responde ($count habilidades)"

        $stats = Invoke-RestMethod -Uri "$base/api/jobs/stats" -Headers $auth -TimeoutSec 10
        if ($stats.totalJobs -lt 1) {
            throw '/api/jobs/stats no tiene ofertas: no se importó seed/job-postings.json'
        }
        Write-Host "  /api/jobs/stats: $($stats.totalJobs) ofertas incluidas en la app"

        $settings = Invoke-RestMethod -Uri "$base/api/settings" -Headers $auth -TimeoutSec 10
        if ($settings.gemini.configured) {
            throw "Hay una key de Gemini configurada (origen: $($settings.gemini.source)): la app debe arrancar sin ella"
        }
        Write-Host '  Sin key de Gemini: el Analizador usa el servicio de IA remoto (skillmap.ai.remote-url)'

        if (-not (Test-Path $logFile) -or -not (Select-String -Path $logFile -Pattern 'Cenit lista' -Quiet)) {
            throw "No se escribió el log en $logFile"
        }
        Write-Host '  Log en logs\skillmap.log de la carpeta temporal'
    } catch {
        $failure = $_
    } finally {
        # Solo se cierra el proceso que arrancó este script.
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force
            $process.WaitForExit(15000) | Out-Null
        }
    }

    if ($failure) {
        Write-Host "  Logs de la prueba en $testHome" -ForegroundColor Yellow
        foreach ($log in @($logFile, $errLog)) {
            if (Test-Path $log) {
                Write-Host "  --- $log" -ForegroundColor Yellow
                Get-Content -Tail 30 $log | ForEach-Object { Write-Host "  $_" }
            }
        }
        throw "La prueba de humo falló: $failure"
    }
    # El sistema puede tardar un instante en soltar los archivos de H2.
    for ($i = 0; $i -lt 10 -and (Test-Path $testHome); $i++) {
        try { Remove-Item -Recurse -Force $testHome -ErrorAction Stop } catch { Start-Sleep -Milliseconds 500 }
    }
}

Write-Step "Listo: $AppExe"
Write-Host "  Cerrar la app: $(Join-Path $AppDir 'stop.bat')"
