// Preload mínimo (contextIsolation: true, nodeIntegration: false, sandbox: true).
// - Pantalla de carga/error (file://): expone Reintentar, Cerrar y Abrir logs.
// - App (http://127.0.0.1:PUERTO): expone solo exportPdf (Exportar CV -> PDF por IPC, ver main.js) y avisa del
//   idioma y el tema elegidos para que la pantalla de carga del próximo arranque salga igual.
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
  contextBridge.exposeInMainWorld('cenitDesktop', {
    retry: () => ipcRenderer.send('cenit:retry'),
    quit: () => ipcRenderer.send('cenit:quit'),
    openLogs: () => ipcRenderer.send('cenit:open-logs'),
  });
} else {
  contextBridge.exposeInMainWorld('cenitDesktop', {
    // Resuelve { ok: true, fileName } | { ok: false, canceled: true } | { ok: false, error }
    exportPdf: (name) => ipcRenderer.invoke('cenit:export-pdf', { name: typeof name === 'string' ? name : '' }),
  });
  window.addEventListener('DOMContentLoaded', () => {
    const root = document.documentElement;
    const send = () => ipcRenderer.send('cenit:ui-prefs', {
      lang: root.getAttribute('lang'),
      theme: root.getAttribute('data-theme'),
    });
    send();
    new MutationObserver(send).observe(root, { attributes: true, attributeFilter: ['lang', 'data-theme'] });
  });
}
