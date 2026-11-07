// Preload mínimo (contextIsolation: true, nodeIntegration: false, sandbox: true).
// - Pantalla de carga/error (file://): expone Reintentar, Cerrar y Abrir logs.
// - App (http://127.0.0.1:PUERTO): no expone nada; solo avisa del idioma y el tema elegidos para que
//   la pantalla de carga del próximo arranque salga igual.
'use strict';

const { contextBridge, ipcRenderer } = require('electron');

if (location.protocol === 'file:') {
  contextBridge.exposeInMainWorld('cenitDesktop', {
    retry: () => ipcRenderer.send('cenit:retry'),
    quit: () => ipcRenderer.send('cenit:quit'),
    openLogs: () => ipcRenderer.send('cenit:open-logs'),
  });
} else {
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
