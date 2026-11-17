// Preload mínimo (contextIsolation: true, nodeIntegration: false, sandbox: true).
// - Pantalla de carga/error (file://): expone Reintentar, Cerrar y Abrir logs, y onReady/proceed: main.js avisa que la
//   app está lista y la pantalla, al terminar su apertura y su fundido, pide pasar a ella.
// - App (http://127.0.0.1:PUERTO): expone exportPdf (Exportar CV -> PDF por IPC, ver main.js), saveJson (Exportar mis
//   datos), version (Acerca de) y desktop (Ajustes > Recordatorios y Sistema, y el estado que piden los recordatorios
//   de main.js), y avisa del
//   idioma, el tema y "Reducir animaciones" para que la pantalla de carga del próximo arranque salga igual.
// - En las dos: los botones laterales del mouse (atrás/adelante) no navegan.
'use strict';

const { contextBridge, ipcRenderer } = require('electron');

// Blink navega con el mouseup de los botones 3 (atrás) y 4 (adelante) si nadie lo cancela.
for (const type of ['mouseup', 'auxclick']) {
  window.addEventListener(type, (event) => {
    if (event.button === 3 || event.button === 4) event.preventDefault();
  }, true);
}

if (location.protocol === 'file:') {
  // El aviso puede llegar antes de que la página registre su función: se recuerda
  let ready = false;
  let onReady = null;
  ipcRenderer.on('cenit:ready', () => {
    ready = true;
    if (onReady) { const cb = onReady; onReady = null; cb(); }
  });
  contextBridge.exposeInMainWorld('cenitDesktop', {
    onReady: (cb) => {
      if (typeof cb !== 'function') return;
      if (ready) cb(); else onReady = cb;
    },
    proceed: () => ipcRenderer.send('cenit:proceed'),
    retry: () => ipcRenderer.send('cenit:retry'),
    quit: () => ipcRenderer.send('cenit:quit'),
    openLogs: () => ipcRenderer.send('cenit:open-logs'),
  });
} else {
  let snapshotProvider = null;
  let openRutaHandler = null;
  // Solo las claves conocidas con valores simples (lo demás no cruza a main.js)
  const pick = (obj, keys) => Object.fromEntries(keys
    .filter((k) => obj && typeof obj === 'object' && ['boolean', 'number'].includes(typeof obj[k]))
    .map((k) => [k, obj[k]]));
  contextBridge.exposeInMainWorld('cenitDesktop', {
    // Resuelve { ok: true, fileName } | { ok: false, canceled: true } | { ok: false, error }
    exportPdf: (name) => ipcRenderer.invoke('cenit:export-pdf', { name: typeof name === 'string' ? name : '' }),
    // Exportar mis datos (Ajustes): guarda el JSON con el diálogo del sistema. Mismo resultado que exportPdf
    saveJson: (name, text) => ipcRenderer.invoke('cenit:save-json', {
      name: typeof name === 'string' ? name : '', text: typeof text === 'string' ? text : '',
    }),
    // Versión de la app (package.json, vía app.getVersion() en main.js) para Ajustes > Acerca de
    version: () => ipcRenderer.invoke('cenit:version'),
    // Ajustes > Recordatorios y Sistema y el scheduler de recordatorios (main.js + reminders.js; lo usa
    // js/recordatorios.js). Se guarda en %APPDATA%\SkillMap\desktop-settings.json, sin tokens; main.js valida todo.
    desktop: {
      // { system: { background, openAtLogin }, reminders: { enabled, day, time, quietStart, quietEnd, types }, paused }
      getSettings: () => ipcRenderer.invoke('cenit:desktop-settings'),
      // Resuelven { ok, state } | { ok: false, error }
      setSystem: (patch) => ipcRenderer.invoke('cenit:set-system', pick(patch, ['background', 'openAtLogin'])),
      setReminders: (patch) => ipcRenderer.invoke('cenit:set-reminders', {
        ...pick(patch, ['enabled', 'day', 'time', 'quietStart', 'quietEnd']),
        ...(patch && typeof patch.types === 'object' ? { types: pick(patch.types, ['weekly', 'streak', 'met']) } : {}),
      }),
      // { ok: true } | { ok: false, blocked: 'system' | 'app' | 'unsupported' } | { ok: false, error }
      testReminder: () => ipcRenderer.invoke('cenit:reminder-test'),
      // Cuenta con sesión (id) o null al cerrar sesión: sin sesión no hay recordatorios
      setUser: (id) => ipcRenderer.send('cenit:reminder-user', id == null ? null : String(id)),
      // El avance o la meta cambiaron
      notifyChange: () => ipcRenderer.send('cenit:reminder-changed'),
      // fn() → estado para el recordatorio ({ userId, goal, count, streak, texts }) o null; puede ser async
      onReminderRequest: (fn) => { if (typeof fn === 'function') snapshotProvider = fn; },
      // Clic en un recordatorio: abrir Mi Ruta
      onOpenRuta: (fn) => { if (typeof fn === 'function') openRutaHandler = fn; },
    },
  });
  ipcRenderer.on('cenit:reminder-request', async (_event, id) => {
    let data = null;
    try { data = snapshotProvider ? await snapshotProvider() : null; } catch { data = null; }
    ipcRenderer.send('cenit:reminder-snapshot', id, data);
  });
  ipcRenderer.on('cenit:open-ruta', () => { try { openRutaHandler?.(); } catch { /* la app sigue igual */ } });
  window.addEventListener('DOMContentLoaded', () => {
    const root = document.documentElement;
    const send = () => ipcRenderer.send('cenit:ui-prefs', {
      lang: root.getAttribute('lang'),
      theme: root.getAttribute('data-theme'),
      motion: root.getAttribute('data-motion'),
    });
    send();
    new MutationObserver(send).observe(root, { attributes: true, attributeFilter: ['lang', 'data-theme', 'data-motion'] });
  });
}
