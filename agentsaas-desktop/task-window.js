'use strict';

// Araç etiketleri main.js'teki agentCommands.js ile AYNI anahtarları kullanır; hangi araç için
// gerçek bir komut olduğu ve hangisinin kurulu olduğu artık burada VARSAYILMIYOR — main.js
// `getInfo()` ile `available` (where/which taraması) gönderiyor (bkz. Faz F3).
const TOOLS = [
  { value: 'CLAUDE_CODE', label: 'Claude Code' },
  { value: 'ANTIGRAVITY', label: 'Antigravity' },
  { value: 'HERMES', label: 'Hermes' },
  { value: 'OPENCLAW', label: 'OpenClaw' },
  { value: 'OMNIROUTE', label: 'Omniroute' },
  { value: 'MANUAL', label: 'Manuel (sadece shell)' },
];
/** Otomatik başlatma komutu OLMAYAN araçlar — bunlarda yalnızca Shell anlamlı. */
const NO_COMMAND = new Set(['MANUAL']);
/** Ajanın kendisinden başka bir ön koşulu olanlar (bkz. main.js `ensureOmnirouteServer`). */
const NOTES = {
  OMNIROUTE: 'OmniRoute kendi yerel sunucusu üzerinden çalışır — kapalıysa Kule başlatmayı önerir.',
};

const titleEl = document.getElementById('title');
const chipEl = document.getElementById('state-chip');
const promptEl = document.getElementById('prompt');
const promptDetails = document.getElementById('prompt-details');
const promptSaveBtn = document.getElementById('prompt-save');
const toolEl = document.getElementById('tool');
const interactiveEl = document.getElementById('interactive');
const noteEl = document.getElementById('note');
const startBtn = document.getElementById('start');
const shellBtn = document.getElementById('shell');
const messageEl = document.getElementById('message');
const completeBtn = document.getElementById('complete');
const failBtn = document.getElementById('fail');
const gitArgEl = document.getElementById('git-arg');
const gitButtonIds = ['git-status', 'git-diff', 'git-log', 'git-branches', 'git-add', 'git-commit',
  'git-checkout', 'git-branch', 'git-merge', 'git-pull', 'git-push', 'ai-commit'];
const treeEl = document.getElementById('tree');
const tabsEl = document.getElementById('tabs');
const codeEl = document.getElementById('code');
const terminalEl = document.getElementById('terminal');
const filesRootEl = document.getElementById('files-root');

for (const t of TOOLS) {
  const opt = document.createElement('option');
  opt.value = t.value;
  opt.textContent = t.label;
  toolEl.appendChild(opt);
}

const term = new Terminal({
  convertEol: false,
  fontSize: 13,
  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
  // Görev loguna akıtılan satırlar bu scrollback'ten okunuyor (bkz. collectTerminalLines) —
  // iki akış arasında çok çıktı üreten bir ajan satır kaybetmesin diye cömert tutuldu.
  scrollback: 5000,
  theme: { background: '#0b0e14', foreground: '#d6dee8' },
});
const fitAddon = new FitAddon.FitAddon();
term.loadAddon(fitAddon);
term.open(document.getElementById('terminal'));
fitAddon.fit();

let ready = false;
let available = {};
let sessionOpen = false;

function size() {
  return { cols: term.cols, rows: term.rows };
}

// Pencere boyutu değişince hem xterm'i hem KARŞI TARAFTAKİ PTY'yi yeniden boyutlandır — TUI'lerin
// doğru çizmesi için şart (PTY yoksa karşı taraf sessizce yok sayar).
window.addEventListener('resize', () => {
  // Kod sekmesi öndeyken terminal `display:none` — ölçüm anlamsız (ve bozuk) olur; sekmeye
  // dönülünce `showTerminal()` zaten yeniden ölçüyor.
  if (terminalEl.classList.contains('hidden')) return;
  fitAddon.fit();
  if (sessionOpen) window.kuleTask.resize(term.cols, term.rows);
});
term.onResize(({ cols, rows }) => {
  if (sessionOpen) window.kuleTask.resize(cols, rows);
});

function currentToolAvailable() {
  const tool = toolEl.value;
  if (NO_COMMAND.has(tool)) return false;
  return available[tool] !== false;
}

function updateNote() {
  const tool = toolEl.value;
  if (NO_COMMAND.has(tool)) {
    noteEl.textContent = 'Bu araç için otomatik başlatma komutu yok — "Shell" ile elle çalışabilirsin.';
  } else if (available[tool] === false) {
    noteEl.textContent = `${tool} bu makinede kurulu değil — kurup uygulamayı yeniden başlat.`;
  } else {
    noteEl.textContent = NOTES[tool] || '';
  }
  startBtn.disabled = !ready || !currentToolAvailable();
}

function setReady(r, projectDir, projectError) {
  ready = r;
  shellBtn.disabled = !ready;
  for (const id of gitButtonIds) document.getElementById(id).disabled = !ready;
  chipEl.textContent = projectError ? 'proje hatası' : (ready ? 'hazır' : 'hazırlanıyor');
  chipEl.className = 'chip ' + (projectError ? 'bad' : (ready ? 'ready' : 'prep'));
  chipEl.title = projectError || '';
  updateNote();
  if (ready) {
    if (projectDir) {
      // Dar başlıkta tam yol okunmuyor — son iki segment yeterli, tamamı title'da duruyor.
      filesRootEl.textContent = projectDir.split(/[\\/]/).slice(-2).join('/');
      filesRootEl.title = projectDir;
    }
    // Klonlama başarısızsa dizin BOŞ olur; "hiçbir şey yok" yerine nedenini göster.
    if (projectError) showTreeMessage(projectError);
    else loadTree();
  }
}

function showTreeMessage(text) {
  treeEl.textContent = '';
  const msg = document.createElement('div');
  msg.className = 'tree-msg';
  msg.textContent = text;
  treeEl.appendChild(msg);
}

// ─── Proje ağacı ─────────────────────────────────────────────────────────────────────
//
// Tembel yükleme: yalnızca açılan klasörün içeriği isteniyor. `node_modules`/`.git` gibi devasa
// klasörler böylece hiç taranmıyor — açılmadıkça bedeli yok.

/** Klasör satırının altına kendi çocuk kabını ekler; ikinci tıklamada yeniden okumaz. */
async function toggleDir(row, relPath) {
  const open = row.dataset.open === '1';
  if (open) {
    row.dataset.open = '0';
    row.querySelector('.tw').textContent = '▸';
    if (row.nextElementSibling && row.nextElementSibling.dataset.childrenOf === relPath) {
      row.nextElementSibling.style.display = 'none';
    }
    return;
  }
  row.dataset.open = '1';
  row.querySelector('.tw').textContent = '▾';

  if (row.nextElementSibling && row.nextElementSibling.dataset.childrenOf === relPath) {
    row.nextElementSibling.style.display = '';
    return;
  }
  const box = document.createElement('div');
  box.dataset.childrenOf = relPath;
  row.after(box);
  await renderInto(box, relPath, Number(row.dataset.depth) + 1);
}

async function renderInto(box, relPath, depth) {
  const res = await window.kuleTask.listDir(relPath);
  box.textContent = '';
  if (!res || res.error) {
    const msg = document.createElement('div');
    msg.className = 'tree-msg';
    msg.textContent = (res && res.error) || 'okunamadı';
    box.appendChild(msg);
    return;
  }
  for (const entry of res.entries) {
    const childPath = relPath ? `${relPath}/${entry.name}` : entry.name;
    const row = document.createElement('div');
    row.className = 'node' + (entry.dir ? ' dir' : '');
    row.dataset.depth = String(depth);
    row.dataset.path = childPath;
    row.style.paddingLeft = `${8 + depth * 12}px`;

    const tw = document.createElement('span');
    tw.className = 'tw';
    tw.textContent = entry.dir ? '▸' : '';
    const nm = document.createElement('span');
    nm.className = 'nm';
    nm.textContent = entry.name;
    row.append(tw, nm);

    row.addEventListener('click', () => (entry.dir ? toggleDir(row, childPath) : openFile(childPath)));
    box.appendChild(row);
  }
  if (!res.entries.length) {
    const msg = document.createElement('div');
    msg.className = 'tree-msg';
    msg.style.paddingLeft = `${8 + depth * 12}px`;
    msg.textContent = '(boş)';
    box.appendChild(msg);
  }
}

async function loadTree() {
  treeEl.textContent = '';
  const box = document.createElement('div');
  treeEl.appendChild(box);
  await renderInto(box, '', 0);
}

document.getElementById('files-refresh').addEventListener('click', () => { if (ready) loadTree(); });
document.getElementById('toggle-files').addEventListener('click', () => {
  document.body.classList.toggle('no-files');
  fitAddon.fit();
});

// ─── Sekmeler: Terminal + açılan dosyalar ────────────────────────────────────────────
//
// Terminal DOM'da hep duruyor, yalnızca gizleniyor — PTY oturumu sekme değişiminde bozulmamalı.
// Geri gösterildiğinde yeniden ölçülüp karşı taraftaki PTY de boyutlandırılıyor.

/** path -> {html, lines, language} — açık dosyaların içeriği (yeniden okumadan sekme değişimi). */
const openFiles = new Map();
let activeTab = 'terminal';

function showTerminal() {
  activeTab = 'terminal';
  terminalEl.classList.remove('hidden');
  codeEl.classList.add('hidden');
  renderTabs();
  fitAddon.fit();
  if (sessionOpen) window.kuleTask.resize(term.cols, term.rows);
  term.focus();
}

function showFile(p) {
  const data = openFiles.get(p);
  if (!data) return;
  activeTab = p;
  terminalEl.classList.add('hidden');
  codeEl.classList.remove('hidden');
  renderTabs();
  renderCode(data);
}

function renderCode(data) {
  codeEl.textContent = '';
  if (data.error) {
    const msg = document.createElement('div');
    msg.className = 'code-msg';
    msg.textContent = data.error;
    codeEl.appendChild(msg);
    return;
  }
  const rowBox = document.createElement('div');
  rowBox.className = 'code-row';

  const gutter = document.createElement('pre');
  gutter.className = 'gutter';
  gutter.textContent = Array.from({ length: data.lines }, (_, i) => i + 1).join('\n');

  const pre = document.createElement('pre');
  const code = document.createElement('code');
  // `html` ana süreçte highlight.js tarafından üretildi; hljs tüm metni kaçırarak (escape)
  // yazar, yani dosya içeriği buraya işaretleme olarak sızamaz.
  code.innerHTML = data.html;
  pre.appendChild(code);

  rowBox.append(gutter, pre);
  codeEl.appendChild(rowBox);
  codeEl.scrollTop = 0;
}

function renderTabs() {
  tabsEl.textContent = '';
  const mk = (id, label, closable) => {
    const tab = document.createElement('div');
    tab.className = 'tab' + (activeTab === id ? ' on' : '');
    tab.title = closable ? id : 'Gömülü terminal'; // aynı adlı dosyalar ayırt edilebilsin
    const name = document.createElement('span');
    name.textContent = label;
    name.addEventListener('click', () => (id === 'terminal' ? showTerminal() : showFile(id)));
    tab.appendChild(name);
    if (closable) {
      const x = document.createElement('span');
      x.className = 'x';
      x.textContent = '×';
      x.addEventListener('click', (e) => {
        e.stopPropagation();
        openFiles.delete(id);
        if (activeTab === id) {
          const next = Array.from(openFiles.keys()).pop();
          if (next) showFile(next);
          else showTerminal();
        } else renderTabs();
      });
      tab.appendChild(x);
    }
    tabsEl.appendChild(tab);
  };
  mk('terminal', 'Terminal', false);
  for (const p of openFiles.keys()) mk(p, p.split('/').pop(), true);
}

async function openFile(relPath) {
  for (const n of treeEl.querySelectorAll('.node.on')) n.classList.remove('on');
  const row = treeEl.querySelector(`.node[data-path="${CSS.escape(relPath)}"]`);
  if (row) row.classList.add('on');

  openFiles.set(relPath, { error: 'Yükleniyor…' });
  showFile(relPath);
  const res = await window.kuleTask.readFile(relPath);
  openFiles.set(relPath, res);
  if (activeTab === relPath) showFile(relPath);
}

renderTabs();

toolEl.addEventListener('change', updateNote);

startBtn.addEventListener('click', async () => {
  // Başlatma her zaman anlık değil — Omniroute'ta önce sunucu kontrolü/başlatması var (bkz.
  // main.js `ensureOmnirouteServer`). Bu sırada ikinci bir tıklama ikinci bir akış başlatmasın.
  const label = startBtn.textContent;
  startBtn.disabled = true;
  startBtn.textContent = 'Başlatılıyor…';
  showTerminal(); // çıktı gizli bir sekmeye akmasın
  try {
    const res = await window.kuleTask.startAgent(toolEl.value, interactiveEl.checked, size());
    if (res && res.started) {
      sessionOpen = true;
      window.kuleTask.resize(term.cols, term.rows);
      term.focus();
    }
  } finally {
    startBtn.textContent = label;
    updateNote(); // düğmenin tekrar açılıp açılmayacağına uygunluk durumu karar verir
  }
});

shellBtn.addEventListener('click', async () => {
  showTerminal();
  const res = await window.kuleTask.startShell(size());
  if (res && res.started) {
    sessionOpen = true;
    window.kuleTask.resize(term.cols, term.rows);
    term.focus();
  }
});

term.onData((data) => {
  if (sessionOpen) window.kuleTask.sendInput(data);
});

window.kuleTask.onOutput((data) => term.write(data));

// ─── Terminal çıktısını canlı olarak görev loguna akıtma ─────────────────────────────
//
// Ham PTY akışını olduğu gibi göndermek işe yaramaz: interaktif TUI'ler aynı satırları imleç
// hareketleriyle defalarca yeniden çizer, log okunamaz hale gelir. Bunun yerine xterm'in KENDİ
// çizilmiş tamponunu okuyoruz — yani "kullanıcının ekranda gerçekten gördüğü" metni. Yeniden
// çizimler zaten birleşmiş, ANSI zaten uygulanmış olur.
//
// Sınır nerede? `baseY`'nin ALTI kesinleşmiştir (scrollback'e düşmüş, bir daha değişmez).
// Ama yalnızca onu göndermek yetmez: bir ekranı doldurmayan kısa oturumlarda `baseY` 0
// kalır ve log ancak görev tamamlanınca yazılırdı, yani canlı olmazdı.
//
// Bu yüzden ikinci bir sınır var: imlecin ÜSTÜNDEKİ görünür satırlar. Onlar hâlâ yeniden
// çizilebilir, bu yüzden ancak çıktı DURULDUĞUNDA (iki ölçüm boyunca değişmediğinde)
// gönderilir. İmleç satırının kendisi hiç gönderilmez — kullanıcının o an yazmakta olduğu
// komut orada duruyor, tamamlanmadan loga geçmemeli.
const TERMINAL_FLUSH_MS = 2000;
let sentLines = 0;
let lastTailSig = '';

/** [sentLines, end) aralığını, sarılmış satırları birleştirerek metne çevirir. */
function readLines(end) {
  const buf = term.buffer.normal;
  const lines = [];
  for (let i = sentLines; i < end; i++) {
    const line = buf.getLine(i);
    if (!line) continue;
    const text = line.translateToString(true);
    // Uzun bir mantıksal satır ekrana birden fazla satır olarak sarılmış olabilir — birleştir.
    if (line.isWrapped && lines.length) lines[lines.length - 1] += text;
    else lines.push(text);
  }
  return lines;
}

function collectTerminalLines(final) {
  // `buffer.normal`: `vim` gibi alternatif ekran kullanan programlar çalışırken bile normal
  // tampon (ve dolayısıyla gönderilmiş satır sayacı) bozulmadan durur.
  const buf = term.buffer.normal;
  const end = final ? buf.length : buf.baseY;
  const lines = readLines(end);
  if (end > sentLines) sentLines = end;
  while (final && lines.length && !lines[lines.length - 1].trim()) lines.pop();
  return lines;
}

/** Kesinleşmiş satırlar + (durulmuşsa) imleç üstündeki görünür satırlar. */
function collectLiveLines() {
  const settled = collectTerminalLines(false);

  const buf = term.buffer.normal;
  const cursorAbs = buf.baseY + buf.cursorY;
  const tail = readLines(cursorAbs);
  const sig = tail.join('\n');
  if (sig && sig === lastTailSig) {
    lastTailSig = '';
    sentLines = cursorAbs;
    return settled.concat(tail);
  }
  lastTailSig = sig;
  return settled;
}

setInterval(() => {
  const lines = collectLiveLines();
  if (lines.length) window.kuleTask.sendTerminalLines(lines);
}, TERMINAL_FLUSH_MS);
window.kuleTask.onReady((info) => setReady(true, info && info.projectDir, info && info.error));

promptSaveBtn.addEventListener('click', async () => {
  promptSaveBtn.disabled = true;
  await window.kuleTask.updatePrompt(promptEl.value);
  promptSaveBtn.textContent = 'Kaydedildi ✓';
  setTimeout(() => {
    promptSaveBtn.textContent = 'Kaydet';
    promptSaveBtn.disabled = false;
  }, 1200);
});

// Git hızlı-aksiyonları — bridge.js'in TOOL_HANDLERS'ındaki git_* araçlarını görevin PROJE
// dizininde çalıştırır (bkz. main.js `task:runGit`); riskli olanlarda native onay diyaloğu
// bridge.js'in `askConfirmation`'ı üzerinden çıkar.
function runGit(name, args) {
  showTerminal(); // git çıktısı da terminale yazılıyor — kod sekmesindeyken kaçırılmasın
  window.kuleTask.runGit(name, args);
}
document.getElementById('git-status').addEventListener('click', () => runGit('git_status', {}));
document.getElementById('git-diff').addEventListener('click', () => runGit('git_diff', {}));
document.getElementById('git-log').addEventListener('click', () => runGit('git_log', {}));
document.getElementById('git-branches').addEventListener('click', () => runGit('git_branch_list', {}));
document.getElementById('git-add').addEventListener('click', () => runGit('git_add', {}));
document.getElementById('git-commit').addEventListener('click', () => runGit('git_commit', { message: gitArgEl.value }));
document.getElementById('git-checkout').addEventListener('click', () => runGit('git_checkout', { branch_or_path: gitArgEl.value }));
document.getElementById('git-branch').addEventListener('click', () => runGit('git_create_branch', { branch_name: gitArgEl.value }));
document.getElementById('git-merge').addEventListener('click', () => runGit('git_merge', { branch: gitArgEl.value }));
document.getElementById('git-pull').addEventListener('click', () => runGit('git_pull', {}));
document.getElementById('git-push').addEventListener('click', () => runGit('git_push', {}));

// Commit mesajı üretimi — `git diff`'i bulut modeline yollayıp sonucu git-arg
// kutusuna yazar, yani doğrudan Commit düğmesiyle kullanılabilir hale gelir.
document.getElementById('ai-commit').addEventListener('click', async (e) => {
  const btn = e.currentTarget;
  btn.disabled = true;
  showTerminal();
  try {
    const res = await window.kuleTask.aiAssist('COMMIT_MESSAGE');
    // Yalnızca ilk satırı input'a koyuyoruz; gövdenin tamamı terminalde görünüyor.
    if (res && res.success) gitArgEl.value = (res.text || '').split('\n')[0].trim();
  } finally {
    btn.disabled = ready ? false : true;
  }
});

async function finish(status) {
  completeBtn.disabled = true;
  failBtn.disabled = true;
  try {
    // "incele" işaretliyse, tamamlamadan ÖNCE bulut modeli değişiklikleri inceler ve
    // bulguları göreve kalıcı bir REVIEW logu olarak yazar (ikinci göz). Başarısız olursa
    // tamamlamayı engellemez — yardımcı bir adım.
    if (document.getElementById('review-on-complete').checked) {
      noteEl.textContent = 'Değişiklikler inceleniyor…';
      await window.kuleTask.aiAssist('REVIEW');
      noteEl.textContent = '';
    }
    // Ekranda kalan (henüz scrollback'e düşmemiş) son satırlar da loga girsin — tamamlama
    // mesajından ÖNCE, sırayla.
    await window.kuleTask.flushTerminal(collectTerminalLines(true));
    const res = await window.kuleTask.complete({
      status,
      message: messageEl.value,
      usedAgent: toolEl.value,
    });
    // Bağlantı kopukken sonuç kuyruğa alınır; pencereyi sessizce kapatmak yerine söylüyoruz,
    // yoksa görev web'de bir süre DISPATCHED görünür ve kullanıcı nedenini bilmez.
    if (res && res.queued) {
      noteEl.textContent = 'Bağlantı kopuk — sonuç kuyruğa alındı, bağlantı gelince gönderilecek. '
        + 'Uygulamayı şimdi kapatma.';
      completeBtn.disabled = false;
      failBtn.disabled = false;
      return;
    }
    window.close();
  } catch (e) {
    // Hata durumunda düğmeler yeniden açılmalı; aksi halde pencere kilitli kalır.
    noteEl.textContent = `Tamamlanamadı: ${e && e.message ? e.message : e}`;
    completeBtn.disabled = false;
    failBtn.disabled = false;
  }
}

// Bağlantı durumu rozeti — kopukken "Tamamlandı"nın neden anında gitmediği anlaşılsın.
window.kuleTask.onConnection((status) => {
  if (status === 'connected') {
    if (chipEl.textContent === 'bağlantı yok') setReady(ready);
    return;
  }
  chipEl.textContent = 'bağlantı yok';
  chipEl.className = 'chip bad';
});
completeBtn.addEventListener('click', () => finish('COMPLETED'));
failBtn.addEventListener('click', () => finish('FAILED'));

window.kuleTask.getInfo().then((task) => {
  titleEl.textContent = `[${task.taskType}] ${task.title}`;
  promptEl.value = task.prompt || task.title;
  available = task.available || {};

  // Kurulu olmayan araçları seçicide açıkça işaretle ve seçilemez yap.
  for (const opt of toolEl.options) {
    if (available[opt.value] === false && !NO_COMMAND.has(opt.value)) {
      opt.textContent += ' (kurulu değil)';
      opt.disabled = true;
    }
  }
  const firstUsable = Array.from(toolEl.options).find((o) => !o.disabled && !NO_COMMAND.has(o.value));
  if (firstUsable) toolEl.value = firstUsable.value;

  setReady(task.ready, task.projectDir, task.projectError);
  console.log(`task-window hazır: taskId=${task.taskId}, ready=${task.ready}, pty=${task.hasPty}`);
});
