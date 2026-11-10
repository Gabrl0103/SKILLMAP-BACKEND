// Preload mínimo (contextIsolation: true, nodeIntegration: false, sandbox: true).
// - Pantalla de carga/error (file://): expone Reintentar, Cerrar y Abrir logs, y onReady/proceed: main.js avisa que la
//   app está lista y la pantalla, al terminar su apertura y su fundido, pide pasar a ella.
// - App (http://127.0.0.1:PUERTO): expone exportPdf (Exportar CV -> PDF por IPC, ver main.js), saveJson (Exportar mis
//   datos) y version (Acerca de), y avisa del
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
  contextBridge.exposeInMainWorld('cenitDesktop', {
    // Resuelve { ok: true, fileName } | { ok: false, canceled: true } | { ok: false, error }
    exportPdf: (name) => ipcRenderer.invoke('cenit:export-pdf', { name: typeof name === 'string' ? name : '' }),
    // Exportar mis datos (Ajustes): guarda el JSON con el diálogo del sistema. Mismo resultado que exportPdf
    saveJson: (name, text) => ipcRenderer.invoke('cenit:save-json', {
      name: typeof name === 'string' ? name : '', text: typeof text === 'string' ? text : '',
    }),
    // Versión de la app (package.json, vía app.getVersion() en main.js) para Ajustes > Acerca de
    version: () => ipcRenderer.invoke('cenit:version'),
  });
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
