'use strict';

const { contextBridge, ipcRenderer } = require('electron');

// nodeIntegration:false + contextIsolation:true altında ayarlar penceresine YALNIZCA bu iki
// dar, güvenli fonksiyonu açar — renderer'ın Node/Electron API'lerine doğrudan erişimi yok.
contextBridge.exposeInMainWorld('kuleDesktop', {
  loadConfig: () => ipcRenderer.invoke('config:load'),
  saveConfig: (config) => ipcRenderer.invoke('config:save', config),
});

// Aynı dosya, native görev penceresi (task-window.html) tarafından da kullanılıyor — ayarlar
// penceresi bu metotları hiç çağırmaz, zararsız. Her IPC çağrısı `main.js`'de o pencereye
// (BrowserWindow.fromWebContents) bağlı olduğu taskId'ye yönlendiriliyor, renderer taskId'yi
// hiç bilmek zorunda değil (bkz. Faz D planı).
contextBridge.exposeInMainWorld('kuleTask', {
  getInfo: () => ipcRenderer.invoke('task:getInfo'),
  // Ajanı doğrudan argv ile çalıştırır (interaktif TUI ya da headless); `startShell`
  // ise elle iş için düz kabuk açar. İkisi de aynı terminal panelini besler.
  startAgent: (agent, interactive, size) => ipcRenderer.invoke('task:startAgent', agent, interactive, size),
  startShell: (size) => ipcRenderer.invoke('task:startShell', size),
  sendInput: (data) => ipcRenderer.send('task:sendInput', data),
  resize: (cols, rows) => ipcRenderer.send('task:resize', cols, rows),
  complete: (result) => ipcRenderer.invoke('task:complete', result),
  onOutput: (cb) => ipcRenderer.on('task:output', (_event, data) => cb(data)),
  // Faz E5: proje klonu/hazırlığı bitince main.js bunu iter — o ana kadar Başlat/Git düğmeleri
  // renderer tarafında devre dışı tutulur.
  onReady: (cb) => ipcRenderer.on('task:ready', (_event, info) => cb(info)),
  // Bridge bağlantı durumu ('connected' | 'connecting' | 'disconnected').
  onConnection: (cb) => ipcRenderer.on('task:connection', (_event, status) => cb(status)),
  // Git hızlı-aksiyonları (bkz. main.js `task:runGit`) — bridge.js'in TOOL_HANDLERS'ındaki AYNI
  // git_* araçlarını, bir AI ajanı TOOL_CALL ile çağırıyormuş gibi doğrudan çalıştırır.
  runGit: (name, args) => ipcRenderer.invoke('task:runGit', name, args),
  // Faz E1: prompt artık salt-okunur değil — bridge.js'in yeni TASK_PROMPT_UPDATE mesajıyla
  // (kullanıcı-JWT'siz) kalıcı olarak kaydedilir.
  updatePrompt: (prompt) => ipcRenderer.invoke('task:updatePrompt', prompt),
  // Terminalin KESİNLEŞMİŞ (scrollback'e düşmüş) satırları — görev logu canlı aksın diye
  // renderer periyodik olarak iter, `flushTerminal` ise tamamlamadan hemen önce kalanı zorlar.
  sendTerminalLines: (lines) => ipcRenderer.send('task:terminalLines', lines),
  flushTerminal: (lines) => ipcRenderer.invoke('task:flushTerminal', lines),
  // Proje ağacı + kod görüntüleyici. Yollar görevin proje dizinine göre GÖRECELİ ve
  // ana süreçte o dizine hapsediliyor (bkz. main.js `resolveInProject`).
  // Bulut modelinden yardım: 'COMMIT_MESSAGE' | 'REVIEW' (bkz. main.js `task:aiAssist`).
  aiAssist: (kind) => ipcRenderer.invoke('task:aiAssist', kind),
  listDir: (relPath) => ipcRenderer.invoke('project:list', relPath),
  readFile: (relPath) => ipcRenderer.invoke('project:read', relPath),
});

// Faz E4: bağlantı kurulunca açılan kalıcı "ana ekran" — görev geçmişi/bildirimler burada durur.
contextBridge.exposeInMainWorld('kuleDashboard', {
  getHistory: () => ipcRenderer.invoke('dashboard:getHistory'),
  openSettings: () => ipcRenderer.invoke('dashboard:openSettings'),
  openTask: (taskId) => ipcRenderer.invoke('dashboard:openTask', taskId),
  onStatus: (cb) => ipcRenderer.on('dashboard:status', (_event, status) => cb(status)),
  onTaskEvent: (cb) => ipcRenderer.on('dashboard:taskEvent', (_event, entry) => cb(entry)),
});
