// Cenit de escritorio: Electron es solo el cascarón (ventana + icono). Todo lo hace el backend
// Spring Boot incluido en resources/backend (runtime de Java propio + jar), que se lanza oculto:
//   1. Busca un puerto libre en 127.0.0.1 (estable entre arranques: el localStorage del frontend va por origen).
//   2. Lanza el backend sin navegador, con ese puerto exacto y atado a esta app por stdin.
//   3. Muestra loading.html mientras espera GET /api/health (60 s como mucho).
//   4. Carga http://127.0.0.1:PUERTO en la ventana.
//   5. Al cerrar: cierra stdin (apagado ordenado, H2 guarda), espera y, si sigue vivo,
//      taskkill /PID <pid> /T /F. No queda ningún java corriendo.
// Recordatorios (meta semanal, racha en riesgo, meta cumplida): el scheduler corre aquí, en el proceso principal (ver
// reminders.js y "Recordatorios" más abajo). Con "Seguir en segundo plano al cerrar" la ventana se oculta y Cenit queda
// en la bandeja; "Salir" la cierra del todo. "Iniciar con Windows" la abre oculta (--hidden) en la bandeja.
'use strict';

const {
  app, BrowserWindow, Menu, Notification, Tray, shell, ipcMain, nativeTheme, powerMonitor, screen, session, dialog,
} = require('electron');
const { spawn, spawnSync } = require('node:child_process');
const fs = require('node:fs');
const http = require('node:http');
const net = require('node:net');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const reminders = require('./reminders');

// --- Configuración ------------------------------------------------------------------------
const PREFERRED_PORTS = Array.from({ length: 10 }, (_, i) => 18080 + i);
const START_TIMEOUT_MS = Number(process.env.CENIT_START_TIMEOUT_MS) || 60_000;
const HEALTH_POLL_MS = 250;
const GRACEFUL_STOP_MS = 8_000;
const EXIT_ALREADY_RUNNING = 3; // SingleInstanceGuard.EXIT_ALREADY_RUNNING del backend
const LOG_MAX_BYTES = 5 * 1024 * 1024;
const BACKEND_TAIL_LINES = 80;
const isDev = !app.isPackaged;

function argValue(name) {
  const prefix = `--${name}=`;
  const arg = process.argv.find((a) => a.startsWith(prefix));
  return arg ? arg.slice(prefix.length) : undefined;
}

// Datos donde siempre: %APPDATA%\SkillMap (backend: data, logs, config.properties). El perfil de Chromium
// (localStorage, caché) va en %APPDATA%\SkillMap\desktop. --cenit-home=... o CENIT_HOME para pruebas.
const HOME = path.resolve(argValue('cenit-home') || process.env.CENIT_HOME
  || path.join(process.env.APPDATA || app.getPath('appData'), 'SkillMap'));
const LOGS_DIR = path.join(HOME, 'logs');
const LOG_FILE = path.join(LOGS_DIR, 'desktop.log');
const BACKEND_LOG_FILE = path.join(LOGS_DIR, 'backend-console.log');
app.setPath('userData', path.join(HOME, 'desktop'));
const PREFS_FILE = path.join(app.getPath('userData'), 'ui-prefs.json');

const BACKEND_DIR = app.isPackaged
  ? path.join(process.resourcesPath, 'backend')
  : path.join(__dirname, 'backend');
const JAVA_EXE = path.join(BACKEND_DIR, 'runtime', 'bin', 'java.exe');
const BACKEND_JAR = path.join(BACKEND_DIR, 'app', 'cenit-backend.jar');
// Archivo CDS generado en el build (build-desktop.ps1): arranque en ~4 s en vez de ~6-8 s. Si no vale, Java lo ignora.
const BACKEND_CDS = path.join(BACKEND_DIR, 'app', 'cenit-backend.jsa');
const LOADING_HTML = path.join(__dirname, 'loading.html');
const ICON = path.join(__dirname, 'build', 'icon.ico');
const APP_ID = 'com.cenit.desktop'; // = appId del instalador: nombre e icono de Cenit en las notificaciones
// Ajustes de escritorio por cuenta (recordatorios) y del equipo (segundo plano, inicio con Windows). Sin tokens.
const SETTINGS_FILE = path.join(HOME, 'desktop-settings.json');
const REMINDER_TICK_MS = 60_000;
const SNAPSHOT_TTL_MS = 15 * 60_000;
const SNAPSHOT_TIMEOUT_MS = 20_000;
const LOGIN_ARGS = ['--hidden'];
// Abierta por "Iniciar con Windows": sin ventana, solo en la bandeja
const startHidden = process.argv.includes('--hidden');
// Solo en desarrollo: CENIT_DEV_NOW=2026-11-14T17:58 hace correr el reloj de los recordatorios desde esa hora (para
// probarlos sin esperar al sábado), CENIT_DEV_STATIC=<frontend> sirve el frontend del repo en vivo y
// CENIT_DEV_IGNORE_BLOCKED=1 no mira si Windows tiene las notificaciones apagadas. Con --inspect, el último aviso y el
// menú de la bandeja quedan en global.cenitDevLastNotification y global.cenitDevTrayMenu (para simular clics).
const clockOffset = (() => {
  const at = isDev ? Date.parse(process.env.CENIT_DEV_NOW || '') : NaN;
  return Number.isFinite(at) ? at - Date.now() : 0;
})();
const clock = () => new Date(Date.now() + clockOffset);

// --- Log ----------------------------------------------------------------------------------
fs.mkdirSync(LOGS_DIR, { recursive: true });
try {
  if (fs.statSync(LOG_FILE).size > LOG_MAX_BYTES) fs.renameSync(LOG_FILE, path.join(LOGS_DIR, 'desktop.1.log'));
} catch { /* no existe todavía */ }
const logStream = fs.createWriteStream(LOG_FILE, { flags: 'a' });

function log(message) {
  const line = `${new Date().toISOString()} ${message}\n`;
  logStream.write(line);
  if (isDev) process.stdout.write(line);
}

// --- Estado -------------------------------------------------------------------------------
/** @type {BrowserWindow | null} */
let mainWindow = null;
/** @type {{proc: import('node:child_process').ChildProcess, port: number, exited: Promise<number|null>, alive: boolean, tail: string[]} | null} */
let backend = null;
let appOrigin = null;
let startGeneration = 0;
let quitting = false;
let shutdownDone = false;
let uiPrefs = readPrefs();
// Pasar a la app cuando la pantalla de carga termine su apertura (cenit:proceed) o, como mucho, ENTER_FALLBACK_MS después
let pendingEnter = null;
const ENTER_FALLBACK_MS = 2_000;
const launchedAt = Date.now();
let appShownLogged = false;
// Segundo plano y recordatorios
let settings = reminders.readSettings(SETTINGS_FILE);
/** @type {Tray | null} */
let tray = null;
let sessionEnding = false; // Windows cierra sesión o se apaga: la ventana se cierra de verdad
let remindersPaused = false; // "Pausar recordatorios" de la bandeja (hasta reanudar o cerrar Cenit)
let reminderUser = null; // cuenta con sesión abierta en la app (la avisa js/recordatorios.js); null = sin recordatorios
let snapshotCache = null; // { at, user, data }: último estado que mandó la app
let snapshotSeq = 0;
const pendingSnapshots = new Map();
let reminderBusy = false;
let reminderSoon = null;
const liveNotifications = []; // las últimas, para que su clic siga funcionando desde el centro de actividades

// --- Una sola instancia -------------------------------------------------------------------
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  // Abrir Cenit.exe otra vez (también desde la bandeja o el menú Inicio) muestra la ventana; no crea otra
  app.on('second-instance', (_event, argv) => {
    if (!argv.includes('--hidden')) showMainWindow();
  });
  init();
}

function init() {
  log(`--- Cenit ${app.getVersion()} (Electron ${process.versions.electron}) · datos en ${HOME}${startHidden ? ' · oculta (inicio con Windows)' : ''}`);
  if (clockOffset) log(`(desarrollo) reloj de recordatorios desde ${clock().toString()}`);
  app.setAppUserModelId(APP_ID); // icono y agrupación propios en la barra de tareas y en las notificaciones
  Menu.setApplicationMenu(null);

  app.whenReady().then(() => {
    // Ningún permiso del navegador (cámara, notificaciones, ubicación...): la app no los usa. Los recordatorios usan
    // Notification del proceso principal, que no pasa por aquí.
    session.defaultSession.setPermissionRequestHandler((_wc, permission, callback) => {
      callback(permission === 'clipboard-sanitized-write');
    });
    syncLoginItemSetting();
    createWindow();
    syncTray();
    startBackend();
    initReminders();
  });

  // Con "Seguir en segundo plano" la ventana no se cierra (se oculta), así que esto solo pasa al salir de verdad
  app.on('window-all-closed', () => app.quit());

  app.on('before-quit', (event) => {
    if (shutdownDone) return;
    event.preventDefault();
    if (quitting) return;
    quitting = true;
    for (const win of BrowserWindow.getAllWindows()) win.hide();
    tray?.destroy(); // sin icono fantasma en la bandeja mientras se apaga el backend
    tray = null;
    stopBackend().finally(() => {
      shutdownDone = true;
      log('Cenit cerrada');
      logStream.end(() => app.quit());
      setTimeout(() => app.exit(0), 2_000); // por si el log no termina de vaciarse
    });
  });

  // Apagado o cierre de sesión de Windows: no hay tiempo para esperar.
  app.on('session-end', () => { sessionEnding = true; killBackendNow(); });
  process.on('exit', () => killBackendNow());

  ipcMain.on('cenit:retry', (event) => {
    if (!isLocalPage(event.senderFrame?.url)) return;
    log('Reintentar');
    startBackend();
  });
  ipcMain.on('cenit:quit', (event) => {
    if (isLocalPage(event.senderFrame?.url)) app.quit();
  });
  ipcMain.on('cenit:open-logs', (event) => {
    if (isLocalPage(event.senderFrame?.url)) shell.openPath(LOGS_DIR);
  });
  ipcMain.on('cenit:proceed', (event) => {
    if (isLocalPage(event.senderFrame?.url)) pendingEnter?.();
  });
  ipcMain.on('cenit:ui-prefs', (event, prefs) => {
    if (!isAppUrl(event.senderFrame?.url) || typeof prefs !== 'object' || !prefs) return;
    const next = {
      lang: prefs.lang === 'en' ? 'en' : 'es',
      theme: prefs.theme === 'dark' ? 'dark' : 'light',
      motion: prefs.motion === 'reduced' ? 'reduced' : 'full',
    };
    // El fondo de la ventana sigue al tema: sin destello de otro color al cargar páginas
    if (next.theme !== uiPrefs.theme) mainWindow?.setBackgroundColor(windowBackground(next.theme));
    if (next.lang !== uiPrefs.lang || next.theme !== uiPrefs.theme || next.motion !== uiPrefs.motion) {
      const langChanged = next.lang !== uiPrefs.lang;
      uiPrefs = next;
      try { fs.writeFileSync(PREFS_FILE, JSON.stringify(uiPrefs)); } catch { /* solo afecta a la pantalla de carga */ }
      if (langChanged) updateTrayMenu();
    }
  });
  // Exportar CV: en Electron window.print() no sirve (sin vista previa ni "Guardar como PDF"). El frontend pide aquí
  // el PDF: se imprime la propia página (con el CV abierto, @media print deja solo el CV en papel blanco), A4 y sin
  // fondos, y se guarda donde elija el usuario (por defecto Documentos\CV-<nombre>.pdf).
  ipcMain.handle('cenit:export-pdf', async (event, request) => {
    if (!isAppUrl(event.senderFrame?.url) || !mainWindow) return { ok: false, error: 'forbidden' };
    const es = uiPrefs.lang !== 'en';
    const name = pdfFileName(request && typeof request.name === 'string' ? request.name : '');
    try {
      const { canceled, filePath } = await dialog.showSaveDialog(mainWindow, {
        title: es ? 'Guardar CV en PDF' : 'Save CV as PDF',
        defaultPath: path.join(app.getPath('documents'), name),
        filters: [{ name: 'PDF', extensions: ['pdf'] }],
      });
      if (canceled || !filePath) return { ok: false, canceled: true };
      const data = await event.sender.printToPDF({
        pageSize: 'A4',
        printBackground: false,
        preferCSSPageSize: true,
        margins: { top: 0.63, bottom: 0.63, left: 0.59, right: 0.59 }, // pulgadas = @page { margin:16mm 15mm }
      });
      const target = path.extname(filePath).toLowerCase() === '.pdf' ? filePath : `${filePath}.pdf`;
      await fs.promises.writeFile(target, data);
      log(`CV exportado en PDF (${data.length} bytes)`);
      return { ok: true, fileName: path.basename(target) };
    } catch (err) {
      log(`No se pudo exportar el CV: ${err.message}`);
      return { ok: false, error: 'failed' };
    }
  });
  // Exportar mis datos (Ajustes > Datos y cuenta): el frontend ya armó el JSON; aquí solo se elige dónde guardarlo (por
  // defecto Documentos\<nombre>.json). Tope de 20 MB: un export real pesa unos pocos KB.
  ipcMain.handle('cenit:save-json', async (event, request) => {
    if (!isAppUrl(event.senderFrame?.url) || !mainWindow) return { ok: false, error: 'forbidden' };
    const text = request && typeof request.text === 'string' ? request.text : '';
    if (!text || text.length > 20 * 1024 * 1024) return { ok: false, error: 'invalid' };
    const es = uiPrefs.lang !== 'en';
    const name = jsonFileName(request && typeof request.name === 'string' ? request.name : '');
    try {
      const { canceled, filePath } = await dialog.showSaveDialog(mainWindow, {
        title: es ? 'Guardar mis datos' : 'Save my data',
        defaultPath: path.join(app.getPath('documents'), name),
        filters: [{ name: 'JSON', extensions: ['json'] }],
      });
      if (canceled || !filePath) return { ok: false, canceled: true };
      const target = path.extname(filePath).toLowerCase() === '.json' ? filePath : `${filePath}.json`;
      await fs.promises.writeFile(target, text, 'utf8');
      log(`Datos exportados (${Buffer.byteLength(text)} bytes)`);
      return { ok: true, fileName: path.basename(target) };
    } catch (err) {
      log(`No se pudieron exportar los datos: ${err.message}`);
      return { ok: false, error: 'failed' };
    }
  });
  ipcMain.handle('cenit:version', (event) => (isAppUrl(event.senderFrame?.url) ? app.getVersion() : ''));

  // Ajustes > Recordatorios y Sistema (js/recordatorios.js). Todo se valida aquí (reminders.js) antes de guardarse.
  ipcMain.handle('cenit:desktop-settings', (event) => (isAppUrl(event.senderFrame?.url) ? desktopState() : null));
  ipcMain.handle('cenit:set-system', (event, patch) => {
    if (!isAppUrl(event.senderFrame?.url) || !patch || typeof patch !== 'object') return { ok: false, error: 'forbidden' };
    const next = reminders.cleanSystem({ ...settings.system, ...patch });
    if (next.openAtLogin !== settings.system.openAtLogin && !applyLoginItem(next.openAtLogin)) {
      return { ok: false, error: 'login-item', state: desktopState() };
    }
    settings.system = next;
    syncTray();
    return { ok: saveSettings(), state: desktopState() };
  });
  ipcMain.handle('cenit:set-reminders', (event, patch) => {
    if (!isAppUrl(event.senderFrame?.url) || !reminderUser) return { ok: false, error: 'forbidden' };
    const account = reminders.accountOf(settings, reminderUser);
    account.reminders = reminders.patchReminders(account.reminders, patch);
    const ok = saveSettings();
    scheduleReminderCheck(); // por si la nueva hora ya pasó hoy
    return { ok, state: desktopState() };
  });
  ipcMain.handle('cenit:reminder-test', async (event) => {
    if (!isAppUrl(event.senderFrame?.url) || !reminderUser) return { ok: false, error: 'forbidden' };
    return testReminder();
  });
  // Entra o sale una cuenta (también al cerrar sesión): los recordatorios son solo de la cuenta abierta
  ipcMain.on('cenit:reminder-user', (event, id) => {
    if (!isAppUrl(event.senderFrame?.url)) return;
    const next = reminders.isAccountId(id) ? id : null;
    if (next === reminderUser) return;
    reminderUser = next;
    snapshotCache = null;
    log(next ? 'Recordatorios: sesión abierta' : 'Recordatorios: sin sesión (detenidos)');
    if (next) scheduleReminderCheck(5_000); // deja que la app termine de cargar Mi Ruta
  });
  // Cambió el avance o la meta: el estado guardado ya no vale (y la meta pudo cumplirse)
  ipcMain.on('cenit:reminder-changed', (event) => {
    if (!isAppUrl(event.senderFrame?.url)) return;
    snapshotCache = null;
    scheduleReminderCheck(3_000);
  });
  ipcMain.on('cenit:reminder-snapshot', (event, id, data) => {
    if (!isAppUrl(event.senderFrame?.url)) return;
    const done = pendingSnapshots.get(id);
    if (done) done(reminders.cleanSnapshot(data));
  });
}

// --- Recordatorios ------------------------------------------------------------------------
// Scheduler en el proceso principal: un intervalo de 60 s (más una revisión al volver de suspensión, al desbloquear, al
// entrar una cuenta y al cambiar el avance). Cada vuelta pregunta a reminders.dueTypes si algo puede salir ya; solo
// entonces pide el estado a la app (cacheado 15 min o hasta el próximo cambio). Así no duplica la lógica de las rachas
// (vive en el renderer, con su registro en localStorage), sigue funcionando con la ventana oculta en la bandeja y no
// hace nada sin backend listo ni sesión abierta.

function initReminders() {
  setInterval(() => checkReminders(), REMINDER_TICK_MS);
  powerMonitor.on('resume', () => scheduleReminderCheck(10_000));
  powerMonitor.on('unlock-screen', () => scheduleReminderCheck(3_000));
}

function scheduleReminderCheck(ms = 1_000) {
  clearTimeout(reminderSoon);
  reminderSoon = setTimeout(() => checkReminders(), ms);
}

function saveSettings() {
  try {
    reminders.writeSettings(SETTINGS_FILE, settings);
    return true;
  } catch (err) {
    log(`No se pudieron guardar los ajustes de escritorio: ${err.message}`);
    return false;
  }
}

function desktopState() {
  const account = reminderUser ? settings.accounts[reminderUser] : null;
  return {
    system: { ...settings.system },
    reminders: account ? account.reminders : reminders.cleanReminders(null),
    paused: remindersPaused,
  };
}

/** Pide a la app (js/recordatorios.js) su estado: meta, avance, racha y textos. null si no responde o no hay sesión. */
function requestSnapshot() {
  const wc = mainWindow?.webContents;
  if (!wc || !isAppUrl(wc.getURL())) return Promise.resolve(null);
  const id = ++snapshotSeq;
  return new Promise((resolve) => {
    const finish = (data) => {
      clearTimeout(timer);
      pendingSnapshots.delete(id);
      resolve(data);
    };
    const timer = setTimeout(() => finish(null), SNAPSHOT_TIMEOUT_MS);
    pendingSnapshots.set(id, finish);
    wc.send('cenit:reminder-request', id);
  });
}

async function currentSnapshot() {
  const user = reminderUser;
  if (snapshotCache && snapshotCache.user === user && Date.now() - snapshotCache.at < SNAPSHOT_TTL_MS) return snapshotCache.data;
  const data = await requestSnapshot();
  if (!data || data.userId !== user || user !== reminderUser) return null;
  snapshotCache = { at: Date.now(), user, data };
  return data;
}

async function checkReminders() {
  if (reminderBusy || quitting || remindersPaused || !appOrigin || !reminderUser) return;
  const user = reminderUser;
  const account = settings.accounts[user];
  const config = account ? account.reminders : reminders.cleanReminders(null);
  const types = reminders.dueTypes(clock(), config, account ? account.sent : {});
  if (!types.length) return;
  reminderBusy = true;
  try {
    const snap = await currentSnapshot();
    if (!snap || user !== reminderUser || remindersPaused || quitting) return;
    const type = reminders.pickReminder(types, snap);
    if (!type) return;
    const blocked = notificationsBlocked();
    if (blocked) {
      log(`Recordatorio (${type}) sin mostrar: notificaciones bloqueadas (${blocked})`);
      return;
    }
    const target = reminders.accountOf(settings, user);
    target.sent = reminders.markSent(target.sent, type, clock());
    saveSettings();
    showReminder(snap.texts[type], type);
  } catch (err) {
    log(`Recordatorios: ${err.message}`);
  } finally {
    reminderBusy = false;
  }
}

/** Muestra la notificación. El clic abre Cenit en Mi Ruta. Resuelve 'shown' | 'failed' | 'unknown' (sin respuesta). */
function showReminder({ title, body }, type) {
  return new Promise((resolve) => {
    if (!Notification.isSupported()) { resolve('failed'); return; }
    const notification = new Notification({ title, body, icon: ICON });
    liveNotifications.push(notification);
    if (liveNotifications.length > 5) liveNotifications.shift();
    let timer = setTimeout(() => resolve('unknown'), 4_000);
    // Windows emite 'show' al aceptar el aviso y, si su configuración lo bloquea, 'failed' enseguida: se espera un poco
    notification.on('show', () => {
      log(`Recordatorio entregado a Windows (${type})`);
      clearTimeout(timer);
      timer = setTimeout(() => resolve('shown'), 800);
    });
    notification.on('failed', (_event, error) => {
      log(`El recordatorio no se pudo mostrar (${type}): ${error}`);
      clearTimeout(timer);
      resolve('failed');
    });
    notification.on('click', () => {
      log(`Recordatorio abierto (${type})`);
      openRuta();
    });
    notification.show();
    if (isDev) global.cenitDevLastNotification = notification; // pruebas: simular el clic desde --inspect
  });
}

// "Probar recordatorio" (Ajustes): el aviso que tocaría con el estado de ahora, sin contar para el máximo diario
async function testReminder() {
  const blocked = notificationsBlocked();
  if (blocked) return { ok: false, blocked };
  snapshotCache = null;
  const snap = await currentSnapshot();
  if (!snap) return { ok: false, error: 'no-data' };
  const type = snap.met ? 'met' : snap.streak >= 1 ? 'streak' : 'weekly';
  const result = await showReminder(snap.texts[type], `${type}, prueba`);
  return result === 'failed' ? { ok: false, error: 'failed' } : { ok: true };
}

/**
 * ¿Windows tiene bloqueadas las notificaciones? 'system' (todas apagadas), 'app' (las de Cenit) o null. No detecta
 * "No molestar"/Asistente de concentración: ahí los avisos llegan igual al centro de actividades, sin mostrarse.
 */
function notificationsBlocked() {
  if (!Notification.isSupported()) return 'unsupported';
  if (process.platform !== 'win32' || (isDev && process.env.CENIT_DEV_IGNORE_BLOCKED === '1')) return null;
  const dword = (key, value) => {
    try {
      const out = spawnSync('reg', ['query', key, '/v', value], { windowsHide: true, encoding: 'utf8', timeout: 5_000 }).stdout || '';
      const match = /REG_DWORD\s+0x([0-9a-f]+)/i.exec(out);
      return match ? parseInt(match[1], 16) : null;
    } catch { return null; }
  };
  const base = 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion';
  if (dword(`${base}\\PushNotifications`, 'ToastEnabled') === 0) return 'system';
  if (dword(`${base}\\Notifications\\Settings\\${APP_ID}`, 'Enabled') === 0) return 'app';
  return null;
}

function showMainWindow() {
  if (!mainWindow) return;
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.show();
  mainWindow.focus();
}

function openRuta() {
  showMainWindow();
  const wc = mainWindow?.webContents;
  if (wc && isAppUrl(wc.getURL())) wc.send('cenit:open-ruta');
}

// --- Bandeja e inicio con Windows ---------------------------------------------------------

// Hay bandeja con "Seguir en segundo plano" activo o si Cenit se abrió oculta al iniciar Windows
function syncTray() {
  if (settings.system.background || startHidden) {
    if (!tray) {
      tray = new Tray(ICON);
      tray.setToolTip('Cenit');
      tray.on('click', showMainWindow);
      tray.on('double-click', showMainWindow);
    }
    updateTrayMenu();
  } else if (tray) {
    tray.destroy();
    tray = null;
  }
}

function updateTrayMenu() {
  if (!tray) return;
  const es = uiPrefs.lang !== 'en';
  const menu = Menu.buildFromTemplate([
    { label: es ? 'Abrir Cenit' : 'Open Cenit', click: showMainWindow },
    {
      label: es ? 'Pausar recordatorios' : 'Pause reminders',
      type: 'checkbox',
      checked: remindersPaused,
      click: (item) => {
        remindersPaused = item.checked;
        log(remindersPaused ? 'Recordatorios en pausa' : 'Recordatorios reanudados');
        if (!remindersPaused) scheduleReminderCheck();
      },
    },
    { type: 'separator' },
    { label: es ? 'Salir' : 'Quit', click: () => app.quit() },
  ]);
  tray.setContextMenu(menu);
  if (isDev) global.cenitDevTrayMenu = menu; // pruebas: usar el menú desde --inspect
}

// Iniciar con Windows: clave Run del usuario (nombre = APP_ID), con --hidden. Solo en la app instalada.
function applyLoginItem(enabled) {
  if (!app.isPackaged) {
    log(`(desarrollo) Iniciar con Windows ${enabled ? 'activado' : 'desactivado'} sin registrar`);
    return true;
  }
  try {
    app.setLoginItemSettings({ openAtLogin: enabled, path: process.execPath, args: LOGIN_ARGS });
    log(`Iniciar con Windows ${enabled ? 'activado' : 'desactivado'}`);
    return true;
  } catch (err) {
    log(`No se pudo cambiar Iniciar con Windows: ${err.message}`);
    return false;
  }
}

// Lo que diga Windows manda (el usuario pudo desactivarlo en el Administrador de tareas)
function syncLoginItemSetting() {
  if (!app.isPackaged) return;
  try {
    const item = app.getLoginItemSettings({ path: process.execPath, args: LOGIN_ARGS });
    const enabled = Boolean(item.openAtLogin && item.executableWillLaunchAtLogin !== false);
    if (enabled !== settings.system.openAtLogin) {
      settings.system = { ...settings.system, openAtLogin: enabled };
      saveSettings();
    }
  } catch { /* se queda lo guardado */ }
}

/** <nombre>.json sin caracteres que Windows no admite en un nombre de archivo. */
function jsonFileName(raw) {
  const clean = raw.normalize('NFC').replace(/[<>:"/\\|?*\u0000-\u001f]/g, '').replace(/\s+/g, ' ').trim()
    .replace(/[. ]+$/, '').replace(/\.json$/i, '').slice(0, 80);
  return `${clean || 'Cenit-datos'}.json`;
}

/** CV-<nombre>.pdf sin caracteres que Windows no admite en un nombre de archivo. */
function pdfFileName(raw) {
  const clean = raw.normalize('NFC').replace(/[<>:"/\\|?*\u0000-\u001f]/g, '').replace(/\s+/g, ' ').trim()
    .replace(/[. ]+$/, '').slice(0, 80);
  return clean ? `CV-${clean}.pdf` : 'CV.pdf';
}

// --- Ventana ------------------------------------------------------------------------------
function createWindow() {
  // 1440x900 (mín. 1100x700), sin pasar del área útil de la pantalla (portátiles y proyectores pequeños).
  const area = screen.getPrimaryDisplay().workAreaSize;
  const width = Math.min(1440, area.width);
  const height = Math.min(900, area.height);
  mainWindow = new BrowserWindow({
    title: 'Cenit',
    icon: ICON,
    width,
    height,
    minWidth: Math.min(1100, width),
    minHeight: Math.min(700, height),
    center: true,
    show: false,
    autoHideMenuBar: true,
    backgroundColor: windowBackground(uiTheme()),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webviewTag: false,
      spellcheck: false,
      devTools: isDev,
    },
  });
  if (width >= area.width || height >= area.height) mainWindow.maximize();
  // Abierta al iniciar Windows: la ventana existe (la app carga y puede recordar) pero no se muestra hasta abrirla
  mainWindow.once('ready-to-show', () => { if (!startHidden) mainWindow.show(); });
  // "Seguir en segundo plano al cerrar": cerrar oculta la ventana y Cenit sigue en la bandeja. Salir (bandeja), apagar
  // o cerrar sesión de Windows sí la cierran. Con la opción apagada, cerrar cierra todo como siempre.
  mainWindow.on('query-session-end', () => { sessionEnding = true; });
  mainWindow.on('session-end', () => { sessionEnding = true; killBackendNow(); });
  mainWindow.on('close', (event) => {
    if (quitting || sessionEnding || !settings.system.background) return;
    event.preventDefault();
    mainWindow.hide();
    syncTray();
    log('Ventana cerrada: Cenit sigue en la bandeja');
  });
  mainWindow.on('closed', () => { mainWindow = null; });
  // El título es siempre "Cenit" (no el de cada página).
  mainWindow.on('page-title-updated', (event) => event.preventDefault());

  const wc = mainWindow.webContents;

  // Enlaces externos y target=_blank: al navegador del sistema. Nada de ventanas nuevas.
  wc.setWindowOpenHandler(({ url }) => {
    if (!isAppUrl(url)) openExternal(url);
    return { action: 'deny' };
  });
  // Solo se navega dentro de la app (127.0.0.1:PUERTO); a la pantalla de carga/error solo la lleva main.js (loadURL
  // no pasa por will-navigate) o ella misma.
  const guard = (event, url) => {
    if (isAppUrl(url) || (isLocalPage(url) && isLocalPage(wc.getURL()))) return;
    event.preventDefault();
    if (!isLocalPage(url)) openExternal(url);
  };
  wc.on('will-navigate', guard);
  wc.on('will-redirect', guard);
  wc.on('will-attach-webview', (event) => event.preventDefault());

  // La app no tiene historial: atrás/adelante (botones laterales del mouse, Alt+←/→, teclas de navegador) no hacen
  // nada. Antes dejaban la ventana en loading.html ("Cenit… cargando") para siempre. Tres capas: el historial se
  // vacía tras cada navegación (no hay adónde volver), app-command se cancela y los atajos se bloquean. preload.js
  // además cancela el mouseup de los botones 3 y 4.
  wc.on('did-navigate', () => clearHistory(wc));
  // Medida para comparar arranques: cuánto tarda en verse la app desde que se abrió Cenit
  wc.on('did-finish-load', () => {
    if (appShownLogged || !isAppUrl(wc.getURL())) return;
    appShownLogged = true;
    log(`App cargada a los ${((Date.now() - launchedAt) / 1000).toFixed(1)} s de abrir Cenit`);
  });
  wc.on('did-navigate-in-page', () => clearHistory(wc));
  mainWindow.on('app-command', (event, command) => {
    if (command === 'browser-backward' || command === 'browser-forward') event.preventDefault();
  });
  wc.on('before-input-event', (event, input) => {
    if (input.type !== 'keyDown') return;
    const key = input.key;
    if (key === 'BrowserBack' || key === 'BrowserForward'
      || (input.alt && (key === 'ArrowLeft' || key === 'ArrowRight') && !input.control && !input.shift && !input.meta)) {
      event.preventDefault();
    }
  });

  // Sin DevTools ni recarga en producción (sin menú no hay atajos, pero por si acaso).
  if (!isDev) {
    wc.on('before-input-event', (event, input) => {
      if (input.type !== 'keyDown') return;
      const key = input.key.toLowerCase();
      const ctrl = input.control || input.meta;
      if (key === 'f5' || key === 'f12'
        || (ctrl && key === 'r')
        || (ctrl && input.shift && ['i', 'j', 'c'].includes(key))) {
        event.preventDefault();
      }
    });
  }

  // Menú contextual solo en campos de texto: cortar, copiar, pegar, seleccionar todo.
  wc.on('context-menu', (_event, params) => {
    if (!params.isEditable) return;
    const es = uiPrefs.lang !== 'en';
    Menu.buildFromTemplate([
      { role: 'cut', label: es ? 'Cortar' : 'Cut', enabled: params.editFlags.canCut },
      { role: 'copy', label: es ? 'Copiar' : 'Copy', enabled: params.editFlags.canCopy },
      { role: 'paste', label: es ? 'Pegar' : 'Paste', enabled: params.editFlags.canPaste },
      { type: 'separator' },
      { role: 'selectAll', label: es ? 'Seleccionar todo' : 'Select all', enabled: params.editFlags.canSelectAll },
    ]).popup({ window: mainWindow });
  });

  wc.on('render-process-gone', (_event, details) => {
    log(`El renderizador terminó (${details.reason}); se recarga`);
    if (!quitting && appOrigin && details.reason !== 'clean-exit') wc.loadURL(appOrigin);
  });
}

function clearHistory(wc) {
  try {
    if (wc.navigationHistory?.clear) wc.navigationHistory.clear();
    else wc.clearHistory?.();
  } catch { /* la ventana se está cerrando */ }
}

// --bg-solid del frontend y de loading.html en cada tema
function windowBackground(theme) {
  return theme === 'dark' ? '#0B1020' : '#F6F4FC';
}

function uiTheme() {
  return uiPrefs.theme || (nativeTheme.shouldUseDarkColors ? 'dark' : 'light');
}

function showPage(state, extra = {}) {
  if (!mainWindow || quitting) return;
  const url = pathToFileURL(LOADING_HTML);
  url.search = new URLSearchParams({
    state,
    lang: uiPrefs.lang === 'en' ? 'en' : 'es', // el idioma por defecto de la app es español
    theme: uiTheme(),
    ...(uiPrefs.motion === 'reduced' ? { motion: 'reduced' } : {}),
    ...extra,
  }).toString();
  mainWindow.loadURL(url.toString());
}

function showError(kind) {
  if (quitting) return;
  log(`Error: ${kind}`);
  showPage('error', { kind, logs: LOGS_DIR });
  if (mainWindow && !mainWindow.isVisible()) mainWindow.show();
}

function isAppUrl(url) {
  if (!url || !appOrigin) return false;
  try { return new URL(url).origin === appOrigin; } catch { return false; }
}

function isLocalPage(url) {
  if (!url) return false;
  try {
    const target = new URL(url);
    return target.protocol === 'file:' && target.pathname === pathToFileURL(LOADING_HTML).pathname;
  } catch { return false; }
}

function openExternal(url) {
  try {
    const { protocol } = new URL(url);
    if (['http:', 'https:', 'mailto:'].includes(protocol)) shell.openExternal(url);
  } catch { /* URL inválida: se ignora */ }
}

function readPrefs() {
  try { return JSON.parse(fs.readFileSync(PREFS_FILE, 'utf8')) || {}; } catch { return {}; }
}

// --- Backend ------------------------------------------------------------------------------
async function startBackend() {
  const generation = ++startGeneration;
  appOrigin = null;
  showPage('loading');
  await stopBackend();

  if (!fs.existsSync(JAVA_EXE) || !fs.existsSync(BACKEND_JAR)) {
    log(`No se encontró el backend en ${BACKEND_DIR}`);
    showError('missing');
    return;
  }

  const tried = new Set();
  for (let attempt = 1; attempt <= 3; attempt++) {
    const port = await pickPort(tried);
    tried.add(port);
    const result = await launchBackend(port, generation);
    if (generation !== startGeneration || quitting) return; // otro arranque pedido o la app se cierra
    if (result === 'ok') {
      appOrigin = `http://127.0.0.1:${port}`;
      log(`Backend listo en ${appOrigin}`);
      enterApp(generation);
      return;
    }
    if (result !== 'port-in-use') {
      showError(result);
      return;
    }
    log(`El puerto ${port} se ocupó al arrancar; se prueba otro`);
  }
  showError('start-failed');
}

/**
 * Backend listo: si se ve la pantalla de carga, se le avisa (cenit:ready) y ella pide pasar a la app (cenit:proceed) al
 * terminar su apertura y su fundido; si no responde en ENTER_FALLBACK_MS, se pasa igual. Nunca bloquea la carga.
 */
function enterApp(generation) {
  const wc = mainWindow?.webContents;
  if (!wc) return;
  let timer = null;
  const go = () => {
    clearTimeout(timer);
    if (pendingEnter !== go) return;
    pendingEnter = null;
    if (quitting || generation !== startGeneration || !appOrigin) return;
    mainWindow?.loadURL(appOrigin);
  };
  pendingEnter = go;
  if (!isLocalPage(wc.getURL())) { go(); return; }
  timer = setTimeout(go, ENTER_FALLBACK_MS);
  wc.send('cenit:ready');
}

/** @returns {Promise<'ok'|'timeout'|'crashed'|'already-running'|'start-failed'|'port-in-use'>} */
function launchBackend(port, generation) {
  const args = [
    '-Xms64m', '-Xmx512m', '-XX:+UseSerialGC', '-XX:TieredStopAtLevel=1',
    // Solo lectura: no se usa -XX:+AutoCreateSharedArchive (en JDK 26 su primera ejecución tarda ~25 s).
    ...(fs.existsSync(BACKEND_CDS) ? [`-XX:SharedArchiveFile=${BACKEND_CDS}`] : []),
    '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8',
    '--enable-native-access=ALL-UNNAMED',
    '-jar', BACKEND_JAR,
    `--server.port=${port}`,
    '--server.address=127.0.0.1',
    '--skillmap.desktop.open-browser=false',
    '--skillmap.desktop.strict-port=true',
    '--skillmap.desktop.exit-on-stdin-close=true',
    `--skillmap.home=${HOME}`,
    '--spring.jpa.show-sql=false',
  ];
  const devStatic = isDev ? process.env.CENIT_DEV_STATIC : '';
  if (devStatic) {
    args.push(`--spring.web.resources.static-locations=file:${path.resolve(devStatic).replace(/\\/g, '/')}/`,
      '--spring.web.resources.cache.period=0');
  }
  const env = { ...process.env };
  // Opciones de Java del sistema que podrían romper el arranque (ej. un agente o -Xmx raro).
  for (const name of ['JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH', 'ELECTRON_RUN_AS_NODE']) {
    delete env[name];
  }

  log(`Lanzando backend en el puerto ${port}`);
  const backendLog = fs.createWriteStream(BACKEND_LOG_FILE, { flags: 'w' });
  const proc = spawn(JAVA_EXE, args, {
    cwd: BACKEND_DIR,
    env,
    windowsHide: true,
    stdio: ['pipe', 'pipe', 'pipe'],
  });
  const tail = [];
  const current = { proc, port, alive: true, tail, exited: null };
  current.exited = new Promise((resolve) => {
    proc.once('exit', (code, signal) => {
      current.alive = false;
      log(`Backend terminó (código ${code}${signal ? `, señal ${signal}` : ''})`);
      backendLog.end();
      resolve(code);
    });
    proc.once('error', (err) => {
      current.alive = false;
      log(`No se pudo lanzar el backend: ${err.message}`);
      backendLog.end();
      resolve(null);
    });
  });
  const collect = (chunk) => {
    backendLog.write(chunk);
    for (const line of chunk.toString('utf8').split(/\r?\n/)) {
      if (!line) continue;
      tail.push(line);
      if (tail.length > BACKEND_TAIL_LINES) tail.shift();
    }
  };
  proc.stdout.on('data', collect);
  proc.stderr.on('data', collect);
  proc.stdin.on('error', () => { /* el backend ya terminó */ });
  backend = current;

  const startedAt = Date.now();
  return new Promise((resolve) => {
    let settled = false;
    const finish = (result) => {
      if (settled) return;
      settled = true;
      clearInterval(timer);
      if (result === 'ok') {
        log(`Backend respondió en ${((Date.now() - startedAt) / 1000).toFixed(1)} s (PID ${proc.pid})`);
        // A partir de aquí, si se cae, pantalla de error con Reintentar.
        current.exited.then(() => {
          if (!quitting && backend === current && generation === startGeneration) {
            log(`Últimas líneas del backend:\n${tail.slice(-20).join('\n')}`);
            showError('crashed');
          }
        });
      }
      resolve(result);
    };

    current.exited.then((code) => {
      if (settled) return;
      log(`Últimas líneas del backend:\n${tail.slice(-30).join('\n')}`);
      if (code === EXIT_ALREADY_RUNNING) finish('already-running');
      else if (tail.some((l) => /already in use|Address already in use|BindException/i.test(l))) finish('port-in-use');
      else finish('start-failed');
    });

    let polling = false;
    const timer = setInterval(async () => {
      if (settled || polling) return;
      if (generation !== startGeneration) { finish('start-failed'); return; }
      if (Date.now() - startedAt > START_TIMEOUT_MS) {
        log(`El backend no respondió en ${START_TIMEOUT_MS / 1000} s`);
        finish('timeout');
        stopBackend();
        return;
      }
      polling = true;
      const healthy = await checkHealth(port);
      polling = false;
      if (healthy && current.alive) finish('ok');
    }, HEALTH_POLL_MS);
  });
}

function checkHealth(port) {
  return new Promise((resolve) => {
    const req = http.get({ host: '127.0.0.1', port, path: '/api/health', timeout: 2_000 }, (res) => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', (c) => { body += c; });
      res.on('end', () => {
        try { resolve(res.statusCode === 200 && JSON.parse(body).status === 'ok'); } catch { resolve(false); }
      });
    });
    req.on('timeout', () => req.destroy());
    req.on('error', () => resolve(false));
  });
}

/** Apagado ordenado (cierra stdin) y, si no termina a tiempo, taskkill del árbol de procesos. */
async function stopBackend() {
  const current = backend;
  if (!current || !current.alive) return;
  log(`Deteniendo backend (PID ${current.proc.pid})`);
  try { current.proc.stdin.end(); } catch { /* ya cerrado */ }
  const stopped = await Promise.race([current.exited.then(() => true), delay(GRACEFUL_STOP_MS).then(() => false)]);
  if (!stopped) {
    log('El backend no se detuvo a tiempo: taskkill /T /F');
    killBackendNow();
    await Promise.race([current.exited, delay(3_000)]);
  }
}

function killBackendNow() {
  const current = backend;
  if (!current || !current.alive || !current.proc.pid) return;
  try {
    spawnSync('taskkill', ['/PID', String(current.proc.pid), '/T', '/F'], { windowsHide: true, timeout: 10_000 });
  } catch { /* nada más que hacer */ }
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// --- Puerto -------------------------------------------------------------------------------
function isPortFree(port) {
  return new Promise((resolve) => {
    const server = net.createServer();
    server.unref();
    server.once('error', () => resolve(false));
    server.listen({ port, host: '127.0.0.1', exclusive: true }, () => server.close(() => resolve(true)));
  });
}

/** El primero libre de 18080-18089 (mismo origen entre arranques = misma sesión guardada) o uno al azar. */
async function pickPort(exclude) {
  for (const port of PREFERRED_PORTS) {
    if (!exclude.has(port) && await isPortFree(port)) return port;
  }
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once('error', reject);
    server.listen({ port: 0, host: '127.0.0.1' }, () => {
      const { port } = server.address();
      server.close(() => resolve(port));
    });
  });
}
