'use strict';

const { app, BrowserWindow, Tray, Menu, dialog, Notification, ipcMain, nativeImage } = require('electron');
const path = require('path');
const fs = require('fs');
const { solidColorPng } = require('./icon');
const { execFile, spawn } = require('child_process');
const { AGENT_BINARIES, commandFor, NEEDS_OMNIROUTE_SERVER } = require('./agentCommands');

/**
 * Terminal çıktısı artık görev tamamlanınca TEK seferde değil, iş sürerken CANLI olarak
 * görev loguna akıyor — web'deki log konsolundan "kişinin ne yaptığı" adım adım izlenebiliyor.
 *
 * Satırlar renderer'da xterm'in çizilmiş tamponundan okunuyor (bkz. task-window.js
 * `collectTerminalLines`), yani ANSI/yeniden-çizim gürültüsü kaynağında zaten eleniyor; burada
 * yalnızca gruplama ve boyut sınırları var: her log kaydı en çok `MAX_LOG_CHARS` karakter, bir
 * görevin toplam terminal logu en çok `MAX_TASK_LOG_BYTES` (sonrasında tek bir kırpıldı notu).
 */
/** Gömülü terminale yazılacak, renkli tek satır — `\r\n` gerektiren PTY biçiminde. */
const ESC = String.fromCharCode(27);
function ansiLine(color, text) {
  return `\r\n${ESC}[${color}m${text}${ESC}[0m\r\n`;
}

const LOG_FLUSH_DEBOUNCE_MS = 1200;
const MAX_LOG_CHARS = 48 * 1024;
const MAX_TASK_LOG_BYTES = 2 * 1024 * 1024;

function flushTerminalLog(taskId) {
  const state = activeTasks.get(taskId);
  if (!state) return;
  if (state.logFlushTimer) {
    clearTimeout(state.logFlushTimer);
    state.logFlushTimer = null;
  }
  if (!bridge || !state.pendingLines.length) return;

  const text = state.pendingLines.join('\n');
  state.pendingLines = [];

  if (state.loggedBytes >= MAX_TASK_LOG_BYTES) return; // kırpıldı notu zaten gönderildi
  state.loggedBytes += Buffer.byteLength(text, 'utf-8');
  if (state.loggedBytes >= MAX_TASK_LOG_BYTES) {
    bridge.sendTaskLog(taskId, 'TERMINAL', '… (terminal çıktısı boyut sınırına ulaştı, kırpıldı)');
    return;
  }
  // Tek bir dev kayıt yerine okunabilir parçalar — log konsolu satır satır akıyor.
  for (let i = 0; i < text.length; i += MAX_LOG_CHARS) {
    bridge.sendTaskLog(taskId, 'TERMINAL', text.slice(i, i + MAX_LOG_CHARS));
  }
}

function queueTerminalLog(taskId, lines) {
  const state = activeTasks.get(taskId);
  if (!state || !lines || !lines.length) return;
  state.pendingLines.push(...lines);
  if (!state.logFlushTimer) {
    state.logFlushTimer = setTimeout(() => flushTerminalLog(taskId), LOG_FLUSH_DEBOUNCE_MS);
  }
}

// Geliştirme modunda (`npm start` / `electron .`, doğrudan bu dizinden) bridge.js komşu
// `agentsaas-local-bridge/` dizininden geliyor. Paketlenmiş uygulamada (electron-builder) bu
// dizin `extraResources` ile `resources/agentsaas-local-bridge/`'e kopyalanıyor (bkz.
// package.json `build.extraResources`) — asar arşivinin İÇİNDE değil, çünkü kendi
// `node_modules/ws`'ine ihtiyacı var.
const bridgeModulePath = app.isPackaged
  ? path.join(process.resourcesPath, 'agentsaas-local-bridge', 'bridge.js')
  : path.join(__dirname, '..', 'agentsaas-local-bridge', 'bridge.js');
const { createBridge } = require(bridgeModulePath);

// PTY modülünü BURADA yükleyip bridge'e enjekte ediyoruz: bridge.js paketlenmiş
// uygulamada `resources/` altından yüklendiği için kendi `require`'ı uygulamanın node_modules'ını
// göremez (sessizce PTY'siz moda düşerdi). Buradan çözümlenince hem dev hem paketli çalışır.
let ptyModule = null;
try {
  ptyModule = require('@homebridge/node-pty-prebuilt-multiarch');
} catch (e) {
  console.log(`PTY modülü yüklenemedi, düz pipe moduna düşülüyor: ${e.message}`);
}

const CONFIG_PATH = path.join(app.getPath('userData'), 'config.json');
const DEFAULT_FRONTEND_URL = 'http://localhost:5173';

const TRAY_COLORS = {
  connected: [46, 204, 113, 255],
  connecting: [241, 196, 15, 255],
  disconnected: [149, 165, 166, 255],
};

let mainWindow = null;
let settingsWindow = null;
let dashboardWindow = null;
let tray = null;
let bridge = null;
let bridgeStatus = 'disconnected';

// taskId -> { window, taskType, title, prompt, projectDir: string|null, localTerminal:
// {write,close}|null, pendingLines, logFlushTimer, loggedBytes, resolve }. `projectDir` `null`
// iken görev henüz "hazırlanıyor"
// durumundadır (bkz. ensureProjectCheckout, Faz E5); `resolve` bridge.js'in onTaskDispatch'ten
// beklediği Promise'i çözer (bkz. Faz D planı).
const activeTasks = new Map();

// Son ~50 görev olayı — dashboard'un "bildirim geçmişi" listesi. Yalnızca bellekte
// (uygulama yeniden başlayınca sıfırlanır) — kalıcı bir depoya gerek yok, zaten backend/web
// görev geçmişinin tek doğruluk kaynağı.
const MAX_HISTORY = 50;
const taskHistory = [];

function pushHistoryEvent(entry) {
  const idx = taskHistory.findIndex((e) => e.taskId === entry.taskId);
  if (idx >= 0) taskHistory[idx] = { ...taskHistory[idx], ...entry };
  else {
    taskHistory.unshift(entry);
    if (taskHistory.length > MAX_HISTORY) taskHistory.length = MAX_HISTORY;
  }
  if (dashboardWindow && !dashboardWindow.isDestroyed()) {
    dashboardWindow.webContents.send('dashboard:taskEvent', taskHistory.find((e) => e.taskId === entry.taskId));
  }
}

function taskIdForWindow(webContents) {
  const win = BrowserWindow.fromWebContents(webContents);
  for (const [taskId, state] of activeTasks) {
    if (state.window === win) return taskId;
  }
  return null;
}

function loadConfig() {
  try {
    return JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf-8'));
  } catch {
    return null;
  }
}

function saveConfig(config) {
  fs.mkdirSync(path.dirname(CONFIG_PATH), { recursive: true });
  fs.writeFileSync(CONFIG_PATH, JSON.stringify(config, null, 2), 'utf-8');
}

function frontendUrlFor(config, suffix = '') {
  return (config?.frontendUrl || DEFAULT_FRONTEND_URL).replace(/\/$/, '') + suffix;
}

function createMainWindow(url) {
  if (mainWindow) {
    mainWindow.loadURL(url);
    mainWindow.show();
    mainWindow.focus();
    return;
  }
  // Kule web arayüzünü OLDUĞU GİBİ gösteren sıradan bir pencere — bkz. bridge.js Javadoc'u ve
  // plan dokümanındaki "arayüzü yeniden yazmıyoruz" kararı. Giriş, görev panosu, prompt
  // düzenleme, canlı terminal, onaylar — hepsi zaten web'de var, burada hiçbiri tekrar
  // yazılmıyor. contextIsolation+nodeIntegration:false standart Electron güvenlik pratiği.
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 860,
    title: 'Kule',
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
  mainWindow.loadURL(url);
  mainWindow.on('close', (e) => {
    // Pencereyi kapatmak uygulamayı SONLANDIRMAZ — tepsi ikonundan arka planda çalışmaya
    // devam eder (bridge bağlantısı kesilmez). Gerçek çıkış yalnızca tepsi menüsündeki "Çıkış".
    if (!app.isQuitting) {
      e.preventDefault();
      mainWindow.hide();
    }
  });
  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

function createSettingsWindow() {
  if (settingsWindow) {
    settingsWindow.show();
    settingsWindow.focus();
    return;
  }
  settingsWindow = new BrowserWindow({
    width: 480,
    height: 700,
    title: 'Kule — Runner Ayarları',
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      preload: path.join(__dirname, 'preload.js'),
    },
  });
  settingsWindow.setMenuBarVisibility(false);
  settingsWindow.loadFile(path.join(__dirname, 'settings.html'));
  settingsWindow.webContents.on('console-message', (e, level, message) => {
    console.log(`[settings] ${message}`);
  });
  settingsWindow.webContents.on('did-fail-load', (e, code, description) => {
    console.log(`[settings] yüklenemedi: ${description} (${code})`);
  });
  settingsWindow.on('closed', () => {
    settingsWindow = null;
  });
}

/** Bağlantı kurulunca (ayarlar kaydedilince VE zaten yapılandırılmış açılışlarda)
 * gösterilen kalıcı "ana ekran" — görev geçmişi + bağlantı durumu burada durur. */
function createDashboardWindow() {
  if (dashboardWindow) {
    dashboardWindow.show();
    dashboardWindow.focus();
    return;
  }
  dashboardWindow = new BrowserWindow({
    width: 420,
    height: 640,
    title: 'Kule',
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      preload: path.join(__dirname, 'preload.js'),
    },
  });
  dashboardWindow.setMenuBarVisibility(false);
  dashboardWindow.loadFile(path.join(__dirname, 'dashboard.html'));
  dashboardWindow.webContents.on('console-message', (e, level, message) => {
    console.log(`[dashboard] ${message}`);
  });
  dashboardWindow.on('close', (e) => {
    if (!app.isQuitting) {
      e.preventDefault();
      dashboardWindow.hide();
    }
  });
  dashboardWindow.on('closed', () => {
    dashboardWindow = null;
  });
}

function trayIconFor(status) {
  return nativeImage.createFromBuffer(solidColorPng(16, TRAY_COLORS[status] || TRAY_COLORS.disconnected));
}

function updateTrayStatus(config) {
  if (dashboardWindow && !dashboardWindow.isDestroyed()) {
    dashboardWindow.webContents.send('dashboard:status', bridgeStatus);
  }
  if (!tray) return;
  const label = bridgeStatus === 'connected' ? 'Bağlı' : bridgeStatus === 'connecting' ? 'Bağlanıyor…' : 'Bağlı değil';
  tray.setToolTip(`Kule Runner — ${label}`);
  tray.setImage(trayIconFor(bridgeStatus));
  tray.setContextMenu(buildTrayMenu(config));
}

/**
 * Onay diyaloğu — bridge.js'in `onConfirm` callback'i. CLI'daki `readline` sorusunun (bkz.
 * runner.js `askConfirmation`) native karşılığı: AYNI tetikleyiciler (write_file/run_command/
 * git_push riskli olduğunda ya da requireConfirmation açıkken), yalnızca sunum farklı.
 */
function confirmDialog(promptText) {
  return dialog
    .showMessageBox({
      type: 'warning',
      buttons: ['Reddet', 'Onayla'],
      defaultId: 0,
      cancelId: 0,
      title: 'Kule — Onay gerekiyor',
      message: promptText,
      noLink: true,
    })
    .then((res) => res.response === 1);
}

/**
 * Bir görev dağıtıldığında artık bloke eden bir soru SORMUYORUZ ama görevi web'e
 * bırakmıyoruz da — native bir "görevi çalıştır" penceresi açılıyor (bkz. task-window.html/js).
 * Kullanıcı orada aracı seçip gömülü terminalde çalıştırıyor, "Tamamlandı/Başarısız" dediğinde bu
 * fonksiyonun döndürdüğü Promise çözülüyor ve bridge.js bunu TASK_RESULT olarak backend'e
 * gönderiyor (bkz. AgentBridgeHandler.handleTaskResult, actorUserId=null yolu — runner-kaynaklı
 * tamamlamalar için zaten var olan, test edilmiş yol). Görev, pencere kapatılıp tamamlanmadan
 * bırakılırsa DISPATCHED kalır ve hâlâ web'den de tamamlanabilir (regresyon yok).
 */
function handleIncomingTask(config, taskId, taskType, title, prompt, projectInfo) {
  if (Notification.isSupported()) {
    const n = new Notification({
      title: 'Kule — Yeni görev atandı',
      body: `[${taskType}] ${title}`,
    });
    n.on('click', () => focusTaskWindow(taskId));
    n.show();
  }

  pushHistoryEvent({ taskId, taskType, title, status: 'preparing', receivedAt: Date.now() });

  return new Promise((resolve) => {
    const win = createTaskWindow(taskId, taskType, title, prompt);
    activeTasks.set(taskId, {
      window: win, taskType, title, prompt, projectDir: null, projectError: null, localTerminal: null,
      pendingLines: [], logFlushTimer: null, loggedBytes: 0, resolve,
    });

    // Görevin projesi gerçek bir repoUrl'e sahipse (arguments'tan geldi, bkz.
    // bridge.js handleTaskDispatch) O dizini otomatik klonlayıp hazırlıyoruz — task-window
    // Başlat/Git düğmelerini bu tamamlanana kadar devre dışı tutuyor (bkz. task:getInfo `ready`).
    const { projectId, repoUrl, defaultBranch } = projectInfo || {};
    bridge.ensureProjectCheckout(projectId, repoUrl, defaultBranch).then((res) => {
      const state = activeTasks.get(taskId);
      if (!state) return; // pencere kapatilip gorev iptal edilmis olabilir
      state.projectDir = res.dir;
      state.projectError = res.error;
      console.log(`[task ${taskId}] proje ${res.ok ? 'hazir' : 'HATALI'}: ${res.dir}${res.error ? ' - ' + res.error : ''}`);
      if (!state.window.isDestroyed()) {
        state.window.webContents.send('task:ready', { projectDir: res.dir, ok: res.ok, error: res.error });
        // Hata gomulu terminalde de gorunsun - kullanici "neden bos?" diye sormasin.
        if (res.error) {
          state.window.webContents.send(
            'task:output',
            ansiLine(31, `[Kule] ${res.error}`)
              + ansiLine(33, 'Depo ozel ise Windows Git Credential Manager ile bir kez giris '
                + 'yapman gerekebilir; yanlis/bos bir depo adresi de bu hatayi verir.'),
          );
        }
      }
      pushHistoryEvent({ taskId, status: res.ok ? 'waiting' : 'error' });
    });
  });
}

function focusTaskWindow(taskId) {
  const state = activeTasks.get(taskId);
  if (state && !state.window.isDestroyed()) {
    state.window.show();
    state.window.focus();
  }
}

function createTaskWindow(taskId, taskType, title, prompt) {
  const win = new BrowserWindow({
    // Dosya ağacı + kod görüntüleyici eklendikten sonra 640px dar kalıyordu.
    width: 1180,
    height: 820,
    minWidth: 720,
    minHeight: 520,
    title: `Kule — ${title}`,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      preload: path.join(__dirname, 'preload.js'),
    },
  });
  win.setMenuBarVisibility(false);
  win.loadFile(path.join(__dirname, 'task-window.html'));
  win.webContents.on('console-message', (e, level, message) => {
    console.log(`[task-window ${taskId}] ${message}`);
  });
  win.webContents.on('did-fail-load', (e, code, description) => {
    console.log(`[task-window ${taskId}] yüklenemedi: ${description} (${code})`);
  });
  win.on('closed', () => {
    const state = activeTasks.get(taskId);
    if (state) {
      if (state.localTerminal) state.localTerminal.close();
      // Görev tamamlanmadan pencere kapatıldıysa bile o ana kadarki terminal kaydı loga gitsin.
      flushTerminalLog(taskId);
      activeTasks.delete(taskId);
    }
  });
  return win;
}

function startBridge(config) {
  if (bridge) bridge.stop();
  try {
    bridge = createBridge(config, {
      onLog: (line) => console.log(line),
      onStatus: (status) => {
        bridgeStatus = status;
        updateTrayStatus(config);
        // Görev pencereleri de bağlantıyı görsün — "Tamamlandı"ya basmadan önce bağlantının
        // kopuk olduğunu bilmek, sonucun neden hemen gitmediğini açıklıyor.
        for (const state of activeTasks.values()) {
          if (!state.window.isDestroyed()) state.window.webContents.send('task:connection', status);
        }
      },
      onConfirm: confirmDialog,
      onTaskDispatch: (taskId, taskType, title, prompt, projectInfo) =>
        handleIncomingTask(config, taskId, taskType, title, prompt, projectInfo),
    }, { pty: ptyModule });
    bridge.start();
  } catch (e) {
    dialog.showErrorBox('Runner başlatılamadı', e.message);
  }
}

function buildTrayMenu(config) {
  return Menu.buildFromTemplate([
    { label: 'Ana Ekran', click: () => createDashboardWindow() },
    { label: "Web'i Aç", click: () => createMainWindow(frontendUrlFor(config)) },
    { label: 'Ayarlar', click: () => createSettingsWindow() },
    {
      label: 'Yeniden Bağlan',
      click: () => {
        const c = loadConfig();
        if (c) startBridge(c);
      },
    },
    { type: 'separator' },
    {
      label: 'Çıkış',
      click: () => {
        app.isQuitting = true;
        if (bridge) bridge.stop();
        app.quit();
      },
    },
  ]);
}

// Kilit alınamazsa (zaten bir kopya çalışıyor) bu ikinci başlatma hemen çıkar — aksi halde
// `npm start`/kısayolu birden fazla kez tetiklemek (ör. "hiçbir şey olmuyor" diye tekrar tekrar
// denemek) her seferinde YENİ bir tepsi ikonu + bridge bağlantısı yığar; hepsi aynı runner
// token'ıyla bağlanmaya çalışır, biri "Ayarlar" açar ötekiler görünmez pencerelerde kalır — canlı
// testte tam olarak bu karışıklık yaşandı.
const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (dashboardWindow) {
      dashboardWindow.show();
      dashboardWindow.focus();
    } else if (mainWindow) {
      mainWindow.show();
      mainWindow.focus();
    } else if (settingsWindow) {
      settingsWindow.show();
      settingsWindow.focus();
    }
  });

  app.whenReady().then(() => {
    const config = loadConfig();
    tray = new Tray(trayIconFor('disconnected'));
    tray.setContextMenu(buildTrayMenu(config));
    tray.on('click', () => createDashboardWindow());
    updateTrayStatus(config);

    if (config && config.backendUrl && config.bridgeToken) {
      // Bridge arka planda başlar VE Dashboard (native "ana ekran") açılır — web giriş
      // ekranı yerine (bkz. createMainWindow) burada görevler/bağlantı durumu görünür; web'e
      // gitmek isteyen ayrıca "Web'i Aç"ı seçer.
      startBridge(config);
      createDashboardWindow();
    } else {
      createSettingsWindow();
    }
  });
}

// Pencereler kapansa/gizlense de tepsi ikonu (ve bridge bağlantısı) çalışmaya devam eder —
// bu yüzden macOS/Linux'taki "tüm pencereler kapanınca uygulamadan çık" varsayılanı BİLİNÇLİ
// olarak devre dışı.
app.on('window-all-closed', () => {});

ipcMain.handle('config:load', () => loadConfig());
ipcMain.handle('config:save', (event, config) => {
  saveConfig(config);
  app.setLoginItemSettings({ openAtLogin: !!config.startAtLogin });
  startBridge(config);
  if (tray) tray.setContextMenu(buildTrayMenu(config));
  // "Kaydet ve Bağlan"a basınca kullanıcıyı asılı bırakmıyoruz — ayarlar penceresi
  // kapanıp Dashboard açılıyor, görevler oradan itibaren görünür olacak.
  createDashboardWindow();
  if (settingsWindow) settingsWindow.close();
  return true;
});

ipcMain.handle('dashboard:getHistory', () => taskHistory);
ipcMain.handle('dashboard:openSettings', () => createSettingsWindow());
ipcMain.handle('dashboard:openTask', (event, taskId) => {
  const state = activeTasks.get(taskId);
  if (state && !state.window.isDestroyed()) {
    state.window.show();
    state.window.focus();
    return true;
  }
  const entry = taskHistory.find((e) => e.taskId === taskId);
  if (entry) {
    dialog.showMessageBox({
      type: 'info',
      title: entry.title,
      message: `[${entry.taskType}] ${entry.title}`,
      detail: `Durum: ${entry.status}${entry.message ? `\n\n${entry.message}` : ''}`,
    });
  }
  return false;
});

// task-window.js'in preload.js üzerinden çağırdığı görev-çalıştırma IPC'leri. Her çağrı
// event.sender'ın ait olduğu pencereden taskId'yi bulup ilgili activeTasks kaydına yönlendiriyor
// — renderer taskId'yi hiç bilmiyor, main.js'in kendi state'i tek doğruluk kaynağı.
ipcMain.handle('task:getInfo', async (event) => {
  const taskId = taskIdForWindow(event.sender);
  const state = activeTasks.get(taskId);
  if (!state) return null;
  // Hangi ajanların bu makinede gerçekten kurulu olduğunu da gönderiyoruz — seçici
  // kurulu olmayanları devre dışı gösterir.
  const available = bridge ? await bridge.detectAgents(AGENT_BINARIES) : {};
  return {
    taskId,
    taskType: state.taskType,
    title: state.title,
    prompt: state.prompt,
    projectDir: state.projectDir,
    projectError: state.projectError || null,
    ready: !!state.projectDir,
    available,
    hasPty: bridge ? bridge.hasPty() : false,
  };
});

// ─── Proje dosya ağacı + kod görüntüleyici ───────────────────────────────────────────
//
// Görev penceresinden projenin klasör yapısına bakıp dosya açabilmek için. İki kural:
//  1. HER yol görevin kendi proje dizinine hapsedilir (`resolveInProject`) — renderer'dan gelen
//     `../` içeren bir yol asla dışarı çıkamaz.
//  2. Vurgulama BURADA (ana süreçte) yapılır: renderer `nodeIntegration:false` olduğu için
//     highlight.js'i require edemez; hazır HTML gönderiyoruz, renderer yalnızca temayı yüklüyor.
const hljs = require('highlight.js/lib/common');

const MAX_VIEW_BYTES = 1024 * 1024;
const EXT_LANG = {
  js: 'javascript', mjs: 'javascript', cjs: 'javascript', jsx: 'javascript',
  ts: 'typescript', tsx: 'typescript', java: 'java', kt: 'kotlin', py: 'python',
  rb: 'ruby', go: 'go', rs: 'rust', php: 'php', cs: 'csharp', c: 'c', h: 'c',
  cpp: 'cpp', hpp: 'cpp', cc: 'cpp', swift: 'swift', sh: 'bash', bash: 'bash',
  ps1: 'powershell', sql: 'sql', json: 'json', xml: 'xml', html: 'xml', vue: 'xml',
  css: 'css', scss: 'scss', less: 'less', md: 'markdown', yml: 'yaml', yaml: 'yaml',
  ini: 'ini', properties: 'ini', toml: 'ini', dockerfile: 'dockerfile', diff: 'diff', patch: 'diff',
};

function resolveInProject(state, relPath) {
  if (!state || !state.projectDir) return null;
  const root = path.resolve(state.projectDir);
  const target = path.resolve(root, relPath || '.');
  if (target !== root && !target.startsWith(root + path.sep)) return null; // `../` kaçışı
  return target;
}

function escapeHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

ipcMain.handle('project:list', async (event, relPath) => {
  const state = activeTasks.get(taskIdForWindow(event.sender));
  const dir = resolveInProject(state, relPath);
  if (!dir) return { error: 'Proje dizini hazır değil' };
  try {
    const items = await fs.promises.readdir(dir, { withFileTypes: true });
    const entries = items
      .map((d) => ({ name: d.name, dir: d.isDirectory() }))
      .sort((a, b) => (a.dir !== b.dir ? (a.dir ? -1 : 1) : a.name.localeCompare(b.name, 'tr')));
    return { path: relPath || '', entries };
  } catch (e) {
    return { error: e.message };
  }
});

ipcMain.handle('project:read', async (event, relPath) => {
  const state = activeTasks.get(taskIdForWindow(event.sender));
  const file = resolveInProject(state, relPath);
  if (!file) return { error: 'Proje dizini hazır değil' };
  try {
    const stat = await fs.promises.stat(file);
    if (stat.isDirectory()) return { error: 'Bu bir klasör' };
    if (stat.size > MAX_VIEW_BYTES) {
      return { error: `Dosya çok büyük (${Math.round(stat.size / 1024)} KB) — görüntüleyici sınırı 1 MB` };
    }
    const buf = await fs.promises.readFile(file);
    if (buf.subarray(0, 8000).includes(0)) return { error: 'İkili (binary) dosya — görüntülenemiyor' };

    const text = buf.toString('utf-8');
    const ext = path.extname(file).slice(1).toLowerCase()
      || path.basename(file).toLowerCase(); // uzantısız: Dockerfile, Makefile…
    const lang = EXT_LANG[ext];
    // ignoreIllegals: yarım/karışık dosyalarda vurgulayıcı hata fırlatıp dosyayı açılmaz kılmasın.
    const html = lang && hljs.getLanguage(lang)
      ? hljs.highlight(text, { language: lang, ignoreIllegals: true }).value
      : escapeHtml(text);
    return { path: relPath, html, lines: text.split('\n').length, language: lang || 'düz metin' };
  } catch (e) {
    return { error: e.message };
  }
});

// ─── Omniroute ön koşulu: kendi yönlendirici sunucusu ────────────────────────────────────────
//
// Omniroute diğer ajanlardan farklı: CLI tek başına bir model çalıştırmıyor, yerelde çalışan
// kendi sunucusuna konuşuyor. Sunucu kapalıyken `repl`/`chat` yalnızca "Server not running"
// yazıp çıkıyor — kullanıcı için "seçtim, başlattım, hiçbir şey olmadı" gibi görünüyordu.
// Bu yüzden ajanı başlatmadan önce kontrol edip, kapalıysa (kullanıcının onayıyla) başlatıyoruz.
// Sunucu ilk açılışta ağırdır (model kataloğu ısınması, WS daemon'ları); HTTP dinlemeye
// başlaması dakikaları bulabilir. Kısa bir zaman aşımı "başlatılamadı" yanılgısı verir.
const OMNIROUTE_READY_TIMEOUT_MS = 180000;

function runOmniroute(args, timeoutMs) {
  return new Promise((resolve) => {
    const { file, args: argv } = bridge.resolveExecutable('omniroute', args);
    execFile(file, argv, { timeout: timeoutMs, windowsHide: true }, (error, stdout, stderr) => {
      resolve({ error, output: `${stdout || ''}${stderr || ''}` });
    });
  });
}

/** Omniroute'un KENDİ sağlık komutunu tek doğruluk kaynağı olarak kullanıyoruz. */
async function omnirouteServerUp() {
  const { error, output } = await runOmniroute(['-q', 'health'], 20000);
  if (error && !output) return false;
  return !/server not running/i.test(output);
}

/**
 * @returns {Promise<boolean>} ajan başlatılabilir mi. Durum/ilerleme doğrudan gömülü terminale
 * yazılıyor — kullanıcı ne olduğunu orada görüyor.
 */
async function ensureOmnirouteServer(state, onData) {
  onData('\r\n\x1b[90m[Kule] OmniRoute sunucusu kontrol ediliyor…\x1b[0m\r\n');
  if (await omnirouteServerUp()) return true;

  const { response } = await dialog.showMessageBox(state.window, {
    type: 'question',
    buttons: ['Sunucuyu başlat', 'Vazgeç'],
    defaultId: 0,
    cancelId: 1,
    title: 'OmniRoute sunucusu çalışmıyor',
    message: 'OmniRoute kendi yerel yönlendirici sunucusu üzerinden çalışır ve şu an kapalı.',
    detail: 'Kule sunucuyu arka planda başlatabilir (omniroute serve --daemon); ilk açılış '
      + 'birkaç dakika sürebilir.\n\nSağlayıcı bağlantıların yoksa ya da API anahtarların '
      + 'geçersizse sunucu açılsa bile istekler boşa gider — bunları kendi terminalinde '
      + '`omniroute setup` / `omniroute providers` ile yönetmen gerekir.',
  });
  if (response !== 0) {
    onData('\x1b[33m[Kule] İptal edildi — sunucuyu elle başlatmak için: omniroute serve --log\x1b[0m\r\n');
    return false;
  }

  onData('\x1b[90m[Kule] omniroute serve --daemon --no-open başlatılıyor '
    + '(ilk açılış birkaç dakika sürebilir)…\x1b[0m\r\n');
  const { file, args } = bridge.resolveExecutable('omniroute', ['serve', '--daemon', '--no-open', '--no-tray']);
  try {
    // detached: sunucu, Kule kapansa bile kullanıcının kendi süreci olarak yaşamaya devam etsin.
    spawn(file, args, { detached: true, stdio: 'ignore', windowsHide: true }).unref();
  } catch (e) {
    onData(`\x1b[31m[Kule] Sunucu başlatılamadı: ${e.message}\x1b[0m\r\n`);
    return false;
  }

  const deadline = Date.now() + OMNIROUTE_READY_TIMEOUT_MS;
  while (Date.now() < deadline) {
    await new Promise((r) => setTimeout(r, 2500));
    if (await omnirouteServerUp()) {
      onData('\x1b[32m[Kule] OmniRoute sunucusu hazır.\x1b[0m\r\n');
      return true;
    }
    onData('\x1b[90m.\x1b[0m');
  }

  onData('\r\n\x1b[31m[Kule] Sunucu süre içinde yanıt vermedi.\x1b[0m\r\n'
    + '\x1b[33mKendi terminalinde `omniroute serve --log` ile çalıştırıp hatayı gör; '
    + 'kurulum yapılmamışsa `omniroute setup` gerekir.\x1b[0m\r\n');
  return false;
}

/**
 * Sunucu ayakta olsa bile yönlendirme yapılandırılmamış olabilir (combo yok, bağlı model yok,
 * anahtar geçersiz) — o zaman ajan açılır ama her istek boşa gider. `simulate` tam olarak bunun
 * için var: upstream'e İSTEK GÖNDERMEDEN hangi sağlayıcının seçileceğini söylüyor, yani kota
 * harcamadan önden uyarabiliyoruz. Bloke etmiyor, yalnızca terminale not düşüyor.
 */
async function warnIfOmnirouteRoutingUnusable(onData, prompt) {
  const { output } = await runOmniroute(['-q', 'simulate', (prompt || 'test').slice(0, 200)], 45000);
  const broken = /no matching combo/i.test(output)
    || /no connected models/i.test(output)
    || /empty pool/i.test(output)
    || /no (available|connected) providers?/i.test(output);
  if (broken) {
    const hint = output.split(/\r?\n/).map((l) => l.trim()).filter(Boolean).pop() || '';
    onData(`\x1b[33m[Kule] Uyarı: OmniRoute yönlendirmesi şu an istek karşılayamıyor — ${hint}\x1b[0m\r\n`
      + '\x1b[33mSağlayıcı/anahtar/combo ayarları için: omniroute providers · omniroute combo\x1b[0m\r\n');
  }
}

/** Aynı pencerede yeni bir oturum açılırken önceki süreç bırakılmamalı (orphan process). */
function replaceSession(state, session) {
  if (state.localTerminal) state.localTerminal.close();
  state.localTerminal = session;
}

// Çıktı yalnızca pencereye yazılır; göreve KALICI olarak yazılacak metni renderer, xterm'in
// çizilmiş tamponundan geri bildiriyor (bkz. Faz G notu) — burada ayrıca transkript tutmuyoruz.
function sessionOutputHandler(state) {
  return (data) => {
    if (!state.window.isDestroyed()) state.window.webContents.send('task:output', data);
  };
}

// Ajanı DOĞRUDAN argv ile çalıştırır — kabuğa komut "yazmak" yok, dolayısıyla
// prompt'taki tırnak/çok satır sorun çıkarmıyor. PTY varsa gerçek interaktif TUI açılır.
ipcMain.handle('task:startAgent', async (event, agent, interactive, size) => {
  const taskId = taskIdForWindow(event.sender);
  const state = activeTasks.get(taskId);
  if (!state || !bridge || !state.projectDir) return { started: false, reason: 'not-ready' };

  const command = commandFor(agent, state.prompt, { interactive });
  if (!command) return { started: false, reason: 'no-command' };

  const onData = sessionOutputHandler(state);
  if (agent === NEEDS_OMNIROUTE_SERVER) {
    if (!(await ensureOmnirouteServer(state, onData))) {
      return { started: false, reason: 'omniroute-server-down' };
    }
    await warnIfOmnirouteRoutingUnusable(onData, state.prompt);
  }
  if (state.window.isDestroyed()) return { started: false, reason: 'window-closed' };
  onData(`\r\n\x1b[36m$ ${command.file} ${command.args.map((a) => (a.includes(' ') ? `"${a}"` : a)).join(' ')}\x1b[0m\r\n`);
  try {
    replaceSession(state, bridge.openAgentSession(
      command.file,
      command.args,
      onData,
      () => onData('\r\n\x1b[90m[oturum sonlandı]\x1b[0m\r\n'),
      state.projectDir,
      size,
    ));
  } catch (e) {
    // Spawn hatası (ör. araç kaldırılmış) IPC'de sessizce reddedilirse kullanıcı "başlat'a
    // bastım, hiçbir şey olmadı" görür — hatayı terminalin kendisine yazıyoruz.
    onData(`\r\n\x1b[31m[Kule] ${command.file} başlatılamadı: ${e.message}\x1b[0m\r\n`);
    return { started: false, reason: 'spawn-failed' };
  }
  return { started: true, isPty: state.localTerminal.isPty };
});

/** Düz kabuk — elle komut/git işi için (ajan oturumundan bağımsız). */
ipcMain.handle('task:startShell', (event, size) => {
  const taskId = taskIdForWindow(event.sender);
  const state = activeTasks.get(taskId);
  if (!state || !bridge || !state.projectDir) return { started: false, reason: 'not-ready' };
  const onData = sessionOutputHandler(state);
  try {
    replaceSession(state, bridge.openLocalTerminal(onData, state.projectDir, size));
  } catch (e) {
    onData(`\r\n\x1b[31m[Kule] Kabuk başlatılamadı: ${e.message}\x1b[0m\r\n`);
    return { started: false, reason: 'spawn-failed' };
  }
  return { started: true, isPty: state.localTerminal.isPty };
});

ipcMain.on('task:sendInput', (event, data) => {
  const taskId = taskIdForWindow(event.sender);
  const state = activeTasks.get(taskId);
  if (state && state.localTerminal) state.localTerminal.write(data);
});

ipcMain.on('task:resize', (event, cols, rows) => {
  const taskId = taskIdForWindow(event.sender);
  const state = activeTasks.get(taskId);
  if (state && state.localTerminal) state.localTerminal.resize(cols, rows);
});

ipcMain.handle('task:runGit', async (event, name, args) => {
  const taskId = taskIdForWindow(event.sender);
  const state = activeTasks.get(taskId);
  if (!state || !bridge || !state.projectDir) return { success: false, output: 'Görev oturumu (henüz) hazır değil' };

  const result = await bridge.runTool(name, args, state.projectDir);
  // Git çıktısını da gömülü terminale yazıyoruz (ajan oturumlarıyla AYNI yol) — böylece görev
  // loguna akan terminal kaydı git aksiyonlarını da içeriyor, ayrı bir mekanizma gerekmiyor.
  const text = `\r\n$ ${name} ${JSON.stringify(args || {})}\r\n${result.output}\r\n`;
  if (!state.window.isDestroyed()) state.window.webContents.send('task:output', text);
  return result;
});

// Renderer, terminalin kesinleşmiş satırlarını periyodik olarak buraya iter; tamamlamadan
// hemen önce `task:flushTerminal` ile kalan (ekranda duran) satırlar da zorlanır.
ipcMain.on('task:terminalLines', (event, lines) => {
  queueTerminalLog(taskIdForWindow(event.sender), lines);
});

ipcMain.handle('task:flushTerminal', (event, lines) => {
  const taskId = taskIdForWindow(event.sender);
  queueTerminalLog(taskId, lines);
  flushTerminalLog(taskId);
  return true;
});

// Bulut modelinden yardim: commit mesaji uretimi ve kod incelemesi. Diff'i BURADA
// topluyoruz (git bridge'in TOOL_HANDLERS'indan, gorevin proje dizininde), metni backend'e
// bridge uzerinden gonderiyoruz; backend org'un API ajanini kullanip cevabi geri veriyor.
async function projectDiff(state) {
  const staged = await bridge.runTool('git_diff', { staged: true }, state.projectDir);
  const unstaged = await bridge.runTool('git_diff', {}, state.projectDir);
  const parts = [];
  if (staged.success && staged.output && staged.output.trim()) parts.push('--- staged ---\n' + staged.output);
  if (unstaged.success && unstaged.output && unstaged.output.trim()) parts.push('--- unstaged ---\n' + unstaged.output);
  return parts.join('\n');
}

ipcMain.handle('task:aiAssist', async (event, kind) => {
  const taskId = taskIdForWindow(event.sender);
  const state = activeTasks.get(taskId);
  if (!state || !bridge || !state.projectDir) return { success: false, text: 'Gorev oturumu hazir degil' };

  const diff = await projectDiff(state);
  if (!diff.trim()) return { success: false, text: 'Degisiklik yok - git diff bos.' };

  const onData = sessionOutputHandler(state);
  onData(ansiLine(90, kind === 'REVIEW' ? '[Kule] Kod incelemesi isteniyor...' : '[Kule] Commit mesaji uretiliyor...'));
  const res = await bridge.requestAiAssist(kind, diff, taskId);
  onData(ansiLine(res.success ? 36 : 31, `[${kind}] ${res.text}`));
  return res;
});

ipcMain.handle('task:updatePrompt', (event, prompt) => {
  const taskId = taskIdForWindow(event.sender);
  const state = activeTasks.get(taskId);
  if (!state || !bridge) return false;
  state.prompt = prompt;
  bridge.updateTaskPrompt(taskId, prompt);
  return true;
});

ipcMain.handle('task:complete', (event, result) => {
  const taskId = taskIdForWindow(event.sender);
  const state = activeTasks.get(taskId);
  if (!state) return { ok: false, reason: 'no-task' };

  // Bağlantı kopukken sonuç bridge'in kalıcı kuyruğuna girer (bkz. bridge.js
  // DURABLE_TYPES) ve yeniden bağlanınca gönderilir. Kullanıcıya bunu bildirmek gerekir:
  // aksi halde görev web arayüzünde bir süre DISPATCHED görünür ve nedeni anlaşılmaz.
  const online = bridgeStatus === 'connected';

  if (state.localTerminal) {
    state.localTerminal.close();
    state.localTerminal = null;
  }
  // Terminal kaydı iş sürerken zaten akmıştır; burada yalnızca renderer'ın son
  // `flushTerminal` çağrısından artakalan varsa tamamlama mesajından ÖNCE kapatılır.
  flushTerminalLog(taskId);
  state.resolve({ status: result.status, message: result.message, usedAgent: result.usedAgent });
  pushHistoryEvent({
    taskId,
    status: result.status === 'COMPLETED' ? 'completed' : 'failed',
    message: result.message,
  });
  activeTasks.delete(taskId);
  return { ok: true, queued: !online };
});
