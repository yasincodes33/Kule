'use strict';

const WebSocket = require('ws');
const fs = require('fs');
const path = require('path');
const { execFile, execFileSync, spawn } = require('child_process');
const { randomUUID } = require('crypto');

/**
 * Gerçek PTY — OPSİYONEL bağımlılık. `agentsaas-desktop` bunu kendi node_modules'ında
 * taşır (prebuilt, derleyici gerektirmez); düz CLI runner (`node runner.js`) ise hiçbir yeni
 * bağımlılık almak zorunda kalmasın diye `require` bilinçli olarak try/catch içinde.
 *
 * PTY varken `claude`/`agy`/`hermes chat` gibi ajanların GERÇEK interaktif TUI'si çalışır
 * (`process.stdout.isTTY === true`, ANSI/imleç kontrolü, boyutlandırma). Yoksa eski
 * `child_process.spawn` (pipe) yoluna düşülür — tek kayıp tam TTY sadakati.
 */
let localPtyModule = null;
try {
  localPtyModule = require('@homebridge/node-pty-prebuilt-multiarch');
} catch {
  localPtyModule = null;
}

/**
 * Runner'ın çekirdek mantığı — UI'dan (terminal/CLI ya da Electron) bilinçli olarak bağımsız.
 * Hem `runner.js` (ince bir CLI sarmalayıcı, `readline` ile) HEM DE `agentsaas-desktop`
 * (Electron, native dialog'larla) bu AYNI modülü kullanır — mantık iki yerde kopyalanmaz.
 *
 * `callbacks`:
 *   - onLog(line): insan-okunur bir durum/olay satırı (CLI console.log'a, Electron bir log
 *     listesine yazdırabilir).
 *   - onStatus(status): 'connecting' | 'connected' | 'disconnected' — tepsi ikonu/ayarlar
 *     penceresi için.
 *   - onConfirm(promptText) => Promise<boolean>: riskli bir işlem (write_file/run_command/
 *     git_push vb.) onay istediğinde çağrılır. CLI'da bir readline sorusu, Electron'da native
 *     bir dialog.
 *   - onTaskDispatch(taskId, taskType, title) => Promise<{status, message, usedAgent}|null|
 *     undefined>: bir görev dağıtıldığında çağrılır. `null`/`undefined` dönerse (ya da hiç
 *     sağlanmamışsa) BLOKE EDEN bir TASK_RESULT gönderilmez — görev artık web'den de
 *     tamamlanabildiği için (bkz. TaskOrchestrationService.completeTask) bu YETERLİ bir davranış;
 *     Electron'da bu callback yalnızca bir bildirim gösterip hemen döner.
 *
 * `createBridge(config, callbacks)` yalnızca bir nesne döndürür — `start()` çağrılana kadar
 * hiçbir bağlantı/side-effect başlamaz (modül import etmenin kendisi zararsız).
 */
function createBridge(config, callbacks, options) {
  const onLog = callbacks.onLog || (() => {});
  const onStatus = callbacks.onStatus || (() => {});
  const onConfirm = callbacks.onConfirm || (async () => false);
  const onTaskDispatch = callbacks.onTaskDispatch;

  // PTY modülü DIŞARIDAN da verilebilir (`options.pty`). Bu şart, çünkü paketlenmiş Electron
  // uygulamasında bridge.js `resources/agentsaas-local-bridge/` altından yükleniyor ve oradan
  // yapılan bir `require` uygulamanın asar içindeki node_modules'ını göremez — yukarıdaki yerel
  // require yalnızca düz CLI kullanımında (node runner.js) devreye girer.
  const ptyModule = (options && options.pty) || localPtyModule;

  const PROJECT_ROOT = path.resolve(config.projectRoot);
  if (!fs.existsSync(PROJECT_ROOT) || !fs.statSync(PROJECT_ROOT).isDirectory()) {
    throw new Error(`projectRoot geçerli bir klasör değil: ${PROJECT_ROOT}`);
  }

  function askConfirmation(promptText) {
    return onConfirm(promptText);
  }

  // ============ Path Sandboxing ============

  /** Verilen göreli yolu proje köküne göre çözer; kök dışına çıkan yolları reddeder. */
  function resolveSafePath(relativePath) {
    const resolved = path.resolve(PROJECT_ROOT, relativePath || '.');
    // Duz startsWith yetmiyor: kok 'C:\proj' iken 'C:\proj-gizli' de oneki sagladigi icin
    // sandbox disina cikilabiliyordu. path.relative ile gercek kapsama kontrolu yapiliyor.
    const rel = path.relative(PROJECT_ROOT, resolved);
    if (rel.startsWith('..') || path.isAbsolute(rel)) {
      throw new Error(`Güvenlik ihlali: '${relativePath}' proje kökünün dışına çıkıyor`);
    }
    return resolved;
  }

  /**
   * execFile kabuk enjeksiyonunu engelliyor ama ARGUMAN enjeksiyonunu engellemiyor:
   * '--upload-pack=...' gibi bir deger git'e ayri bir secenek olarak gecer ve
   * resolveSafePath bunu yakalamaz (kok altinda bir yola cozulur). Modelden gelen
   * hicbir serbest metin degeri '-' ile baslayamaz.
   */
  function assertNotOption(value, fieldName) {
    if (typeof value === 'string' && value.startsWith('-')) {
      throw new Error(`Guvenlik ihlali: '${fieldName}' bir git secenegi gibi gorunuyor: ${value}`);
    }
    return value;
  }

  // ============ Araç Uygulamaları ============

  async function toolReadFile(args) {
    const filePath = resolveSafePath(args.path);
    if (!fs.existsSync(filePath)) {
      return { success: false, output: `Dosya bulunamadı: ${args.path}` };
    }
    if (fs.statSync(filePath).isDirectory()) {
      return { success: false, output: `'${args.path}' bir dosya değil, klasör` };
    }
    const content = fs.readFileSync(filePath, 'utf-8');
    return { success: true, output: content };
  }

  async function toolWriteFile(args) {
    const filePath = resolveSafePath(args.path);

    if (config.requireConfirmation) {
      const preview = (args.content || '').slice(0, 300);
      const confirmed = await askConfirmation(
        `Şu dosyaya yazılacak: ${args.path}\n--- İçerik önizleme ---\n${preview}${args.content.length > 300 ? '\n... (devamı var)' : ''}\n---`
      );
      if (!confirmed) {
        return { success: false, output: 'Kullanıcı tarafından reddedildi' };
      }
    }

    fs.mkdirSync(path.dirname(filePath), { recursive: true });
    fs.writeFileSync(filePath, args.content ?? '', 'utf-8');
    return { success: true, output: `Yazıldı: ${args.path}` };
  }

  async function toolListDirectory(args) {
    const dirPath = resolveSafePath(args.path);
    if (!fs.existsSync(dirPath)) {
      return { success: false, output: `Klasör bulunamadı: ${args.path || '.'}` };
    }
    const entries = fs.readdirSync(dirPath, { withFileTypes: true });
    const listing = entries.map((e) => (e.isDirectory() ? `${e.name}/` : e.name)).join('\n');
    return { success: true, output: listing || '(boş klasör)' };
  }

  async function toolRunCommand(args) {
    const command = args.command || '';

    const isDangerous = command.includes('--force') || command.includes('--hard') || command.includes('rm -rf');

    if (isDangerous || config.requireConfirmation) {
      const confirmed = await askConfirmation(`Şu komut çalıştırılacak: ${command}`);
      if (!confirmed) {
        return { success: false, output: 'Kullanıcı tarafından reddedildi' };
      }
    }

    return new Promise((resolve) => {
      // execFile + shell:true kullanıyoruz (komut string olarak geldiği için); üretimde daha
      // sıkı bir allowlist/parser eklemek isterseniz burası genişletilecek nokta.
      execFile(command, { shell: true, cwd: PROJECT_ROOT, timeout: config.commandTimeoutMs || 30000 },
        (error, stdout, stderr) => {
          if (error) {
            resolve({ success: false, output: `Hata: ${error.message}\n${stderr}` });
          } else {
            resolve({ success: true, output: stdout || '(çıktı yok)' });
          }
        });
    });
  }

  // Salt-okunur ve yerel git komutları hızlı; push/pull ise ağ işi. Tek bir
  // commandTimeoutMs'e sıkıştırmak push/pull'u erken kesiyordu. Backend'deki
  // DurationClass (FAST=30sn / SLOW=10dk) ile hizalı.
  const GIT_LOCAL_TIMEOUT_MS = config.commandTimeoutMs || 30000;
  const GIT_NETWORK_TIMEOUT_MS = config.gitNetworkTimeoutMs || 600000;

  // `cwd` opsiyonel (varsayılan PROJECT_ROOT) — AI ajanının TOOL_CALL yolu (handleToolCall) hiç
  // geçirmediği için davranışı birebir eskisi gibi kalıyor. Yalnızca Electron'un native görev
  // paneli (bkz. runTool), bir görevin PROJESİNE özel klonlanmış dizini (ensureProjectCheckout)
  // buraya geçiriyor (Faz E5) — birden fazla görev/proje aynı anda açıkken her biri KENDİ
  // dizininde çalışsın diye, PROJECT_ROOT gibi paylaşılan bir mutable state DEĞİL.
  function execGit(args, timeoutMs, cwd) {
    return new Promise((resolve) => {
      execFile('git', args, { cwd: cwd || PROJECT_ROOT, timeout: timeoutMs || GIT_LOCAL_TIMEOUT_MS }, (error, stdout, stderr) => {
        if (error) {
          resolve({ success: false, output: `Hata: ${error.message}\n${stderr}` });
        } else {
          resolve({ success: true, output: stdout || '(çıktı yok)' });
        }
      });
    });
  }

  /** Handler'ları saran ortak hata yakalayıcı — guard'ların fırlattığı hatayı araç sonucuna
   * çevirir. İkinci `cwd` parametresini olduğu gibi ileri taşır (bkz. execGit Javadoc'u). */
  function guarded(fn) {
    return async (args, cwd) => {
      try {
        return await fn(args, cwd);
      } catch (e) {
        return { success: false, output: e.message };
      }
    };
  }

  const REJECTED = { success: false, output: 'Kullanıcı tarafından reddedildi' };

  // ---- Salt-okunur git araçları: onay gerektirmez ----

  async function toolGitStatus(args, cwd) {
    return execGit(['status'], null, cwd);
  }

  async function toolGitDiff(args, cwd) {
    const gitArgs = ['diff'];
    if (args.path) {
      assertNotOption(args.path, 'path');
      resolveSafePath(args.path);
      gitArgs.push('--', args.path);
    }
    return execGit(gitArgs, null, cwd);
  }

  async function toolGitLog(args, cwd) {
    const raw = args.max_count === undefined || args.max_count === null ? 10 : args.max_count;
    const maxCount = Number.parseInt(raw, 10);
    if (!Number.isInteger(maxCount) || maxCount < 1 || maxCount > 1000) {
      return { success: false, output: `Geçersiz argüman: max_count (1-1000 arası tam sayı olmalı): ${raw}` };
    }
    return execGit(['log', `-${maxCount}`], null, cwd);
  }

  async function toolGitBranchList(args, cwd) {
    return execGit(['branch', '-a'], null, cwd);
  }

  // ---- Yazma araçları: requireConfirmation kontrolünden geçer ----

  async function toolGitAdd(args, cwd) {
    const pathArg = args.path || '.';
    assertNotOption(pathArg, 'path');
    resolveSafePath(pathArg);

    if (config.requireConfirmation && !(await askConfirmation(`Git add çalıştırılacak: git add -- ${pathArg}`))) {
      return REJECTED;
    }
    return execGit(['add', '--', pathArg], null, cwd);
  }

  async function toolGitCommit(args, cwd) {
    const message = args.message || 'Update';
    if (config.requireConfirmation && !(await askConfirmation(`Git commit çalıştırılacak. Mesaj: "${message}"`))) {
      return REJECTED;
    }
    return execGit(['commit', '-m', message], null, cwd);
  }

  async function toolGitPush(args, cwd) {
    const remote = assertNotOption(args.remote || 'origin', 'remote');
    const branch = args.branch ? assertNotOption(args.branch, 'branch') : null;
    const isForce = args.force === true;

    const gitArgs = ['push'];
    if (isForce) gitArgs.push('--force');
    gitArgs.push(remote);
    if (branch) gitArgs.push(branch);

    if (isForce || config.requireConfirmation) {
      if (!(await askConfirmation(`DİKKAT: Git push çalıştırılacak: git ${gitArgs.join(' ')}`))) {
        return REJECTED;
      }
    }
    return execGit(gitArgs, GIT_NETWORK_TIMEOUT_MS, cwd);
  }

  async function toolGitPull(args, cwd) {
    const remote = assertNotOption(args.remote || 'origin', 'remote');
    const branch = args.branch ? assertNotOption(args.branch, 'branch') : null;

    const gitArgs = ['pull', remote];
    if (branch) gitArgs.push(branch);

    if (config.requireConfirmation && !(await askConfirmation(`Git pull çalıştırılacak: git ${gitArgs.join(' ')}`))) {
      return REJECTED;
    }
    return execGit(gitArgs, GIT_NETWORK_TIMEOUT_MS, cwd);
  }

  async function toolGitCheckout(args, cwd) {
    const target = args.branch_or_path;
    if (!target) return { success: false, output: 'Geçersiz argüman: branch_or_path' };
    assertNotOption(target, 'branch_or_path');

    let isPathRestore = false;
    try {
      isPathRestore = fs.existsSync(resolveSafePath(target));
    } catch {
      isPathRestore = false;
    }

    if (isPathRestore) {
      const confirmed = await askConfirmation(
        `DİKKAT: '${target}' yolundaki commit edilmemiş değişiklikler geri alınamaz şekilde silinecek: git checkout -- ${target}`
      );
      if (!confirmed) return REJECTED;
      return execGit(['checkout', '--', target], null, cwd);
    }

    if (config.requireConfirmation && !(await askConfirmation(`Git checkout çalıştırılacak: git checkout ${target}`))) {
      return REJECTED;
    }
    return execGit(['checkout', target], null, cwd);
  }

  async function toolGitMerge(args, cwd) {
    const branch = args.branch;
    if (!branch) return { success: false, output: 'Geçersiz argüman: branch' };
    assertNotOption(branch, 'branch');

    if (config.requireConfirmation && !(await askConfirmation(`Git merge çalıştırılacak: git merge ${branch}`))) {
      return REJECTED;
    }
    return execGit(['merge', branch], null, cwd);
  }

  async function toolGitCreateBranch(args, cwd) {
    const branchName = args.branch_name;
    const checkout = args.checkout !== false;
    if (!branchName) return { success: false, output: 'Geçersiz argüman: branch_name' };
    assertNotOption(branchName, 'branch_name');

    if (config.requireConfirmation
        && !(await askConfirmation(`Yeni branch oluşturulacak: ${branchName} (Checkout: ${checkout})`))) {
      return REJECTED;
    }
    return checkout ? execGit(['checkout', '-b', branchName], null, cwd) : execGit(['branch', branchName], null, cwd);
  }

  const TOOL_HANDLERS = {
    read_file: toolReadFile,
    write_file: toolWriteFile,
    list_directory: toolListDirectory,
    run_command: toolRunCommand,
    git_status: guarded(toolGitStatus),
    git_diff: guarded(toolGitDiff),
    git_log: guarded(toolGitLog),
    git_branch_list: guarded(toolGitBranchList),
    git_add: guarded(toolGitAdd),
    git_commit: guarded(toolGitCommit),
    git_push: guarded(toolGitPush),
    git_pull: guarded(toolGitPull),
    git_checkout: guarded(toolGitCheckout),
    git_merge: guarded(toolGitMerge),
    git_create_branch: guarded(toolGitCreateBranch),
  };

  // ============ Canlı Terminal / Ajan Oturumları ============
  //
  // PTY varsa (bkz. dosya başındaki opsiyonel require) gerçek bir sözde-terminal
  // kullanılıyor: TTY algılanır, ANSI/imleç kontrolü ve boyutlandırma çalışır, yani ajanların
  // (claude/agy/hermes) interaktif TUI'si gömülü terminalde açılabilir. PTY yoksa eski
  // child_process.spawn (pipe) yoluna düşülür.
  const terminalSessions = new Map();

  const DEFAULT_SHELL = process.platform === 'win32'
    ? 'cmd.exe'
    : (process.env.SHELL || '/bin/bash');

  /** `where`/`which` sonuçları (ilk çağrıda bir kez, ~5ms'lik bir alt süreç) — null = bulunamadı. */
  const executableCache = new Map();

  /**
   * çevirir. Windows'ta iki ayrı tuzak vardır:
   *
   *  1. node-pty (ConPTY) `CreateProcess`'i mutlak yolla çağırıyor — PATH taraması YOK. Düz
   *     `pty.spawn('agy', ...)` "File not found" ile ölüyor (oysa `child_process.spawn` bulurdu).
   *  2. npm ile kurulan araçlar (`openclaw`, `omniroute`) `.exe` değil `.cmd` sarmalayıcı;
   *     `.cmd` doğrudan çalıştırılamaz, `cmd.exe /c` ile sarılması gerekir. `where` bunların
   *     UZANTISIZ (git-bash için sh script'i) sürümünü ÖNCE listeliyor — onu atlamak şart.
   */
  function lookupExecutable(name) {
    if (executableCache.has(name)) return executableCache.get(name);
    let resolvedPath = null;
    try {
      const lookup = process.platform === 'win32' ? 'where' : 'which';
      // stderr 'ignore': `where` bulamadığında "INFO: Could not find files..." yazıyor ve
      // varsayılan `inherit` bunu CLI runner'ın konsoluna sızdırıyor.
      const lines = execFileSync(lookup, [name], { timeout: 5000, encoding: 'utf-8', stdio: ['ignore', 'pipe', 'ignore'] })
        .split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
      if (process.platform === 'win32') {
        const runnable = (ext) => lines.find((l) => l.toLowerCase().endsWith(ext));
        resolvedPath = runnable('.exe') || runnable('.com') || runnable('.cmd') || runnable('.bat') || null;
      } else {
        resolvedPath = lines[0] || null;
      }
    } catch {
      resolvedPath = null; // kurulu değil (ya da `where`/`which` yok)
    }
    executableCache.set(name, resolvedPath);
    return resolvedPath;
  }

  function resolveExecutable(file, args) {
    if (process.platform !== 'win32') return { file, args };
    // Zaten mutlak bir yol verilmişse dokunma (yalnızca .cmd sarmalama gerekebilir).
    const resolvedPath = path.isAbsolute(file) ? file : lookupExecutable(file);
    if (!resolvedPath) return { file, args }; // spawn kendi net hatasını versin
    if (/\.(cmd|bat)$/i.test(resolvedPath)) {
      const comspec = process.env.ComSpec || 'cmd.exe';
      return { file: comspec, args: ['/c', resolvedPath, ...args] };
    }
    return { file: resolvedPath, args };
  }

  /**
   * Uzak (backend-relayed, `handleTerminalOpen`) ve yerel (Electron görev paneli,
   * `openLocalTerminal`/`openAgentSession`) oturumların paylaştığı tek çekirdek.
   *
   * `file`/`args` verilmezse düz bir kabuk açılır. Ajanlar argv ile DOĞRUDAN spawn edilebilsin
   * diye parametrik: böylece prompt'u kabuğa metin olarak "yazmak" (ve tırnak/çok satır
   * kaçışıyla uğraşmak) gerekmiyor.
   */
  function spawnSession(options, onData, onExit) {
    const { file, args, cwd, cols, rows } = options || {};
    // node-pty Windows'ta PATH TARAMASI YAPMAZ (`CreateProcess` mutlak yol bekler) ve `.cmd`
    // sarmalayıcılarını doğrudan çalıştıramaz — bu yüzden argv'yi spawn'dan ÖNCE çözüyoruz.
    const resolved = resolveExecutable(file || DEFAULT_SHELL, args || []);
    const targetFile = resolved.file;
    const targetArgs = resolved.args;
    const targetCwd = cwd || PROJECT_ROOT;

    if (ptyModule) {
      const term = ptyModule.spawn(targetFile, targetArgs, {
        name: 'xterm-256color',
        cols: cols || 80,
        rows: rows || 24,
        cwd: targetCwd,
        env: process.env,
      });
      term.onData((data) => onData(data));
      term.onExit(() => onExit && onExit());
      return {
        isPty: true,
        // PTY'de ham `\r` DOĞRU olan — çeviri yapılmamalı (aşağıdaki geri düşüş yolundaki
        // `\r`→`\n` hilesi yalnızca PTY YOKKEN gerekli, bkz. handleTerminalInput yorumu).
        write(data) { term.write(data || ''); },
        resize(c, r) {
          try { term.resize(Math.max(1, c || 80), Math.max(1, r || 24)); } catch { /* kapanmış olabilir */ }
        },
        close() { try { term.kill(); } catch { /* zaten ölmüş olabilir */ } },
      };
    }

    const child = spawn(targetFile, targetArgs, { cwd: targetCwd, env: process.env });
    child.stdout.on('data', (chunk) => onData(chunk.toString('utf-8')));
    child.stderr.on('data', (chunk) => onData(chunk.toString('utf-8')));
    child.on('error', (e) => onData(`\r\n[runner] Terminal hatası: ${e.message}\r\n`));
    child.on('exit', () => onExit && onExit());

    return {
      isPty: false,
      write(data) {
        if (child.stdin.writable) child.stdin.write((data || '').replace(/\r/g, '\n'));
      },
      resize() { /* PTY olmadan satır/sütun kavramı yok */ },
      close() { child.kill(); },
    };
  }

  function handleTerminalOpen(message) {
    const { terminalSessionId, cols, rows } = message;
    if (terminalSessions.has(terminalSessionId)) {
      return; // zaten açık — TERMINAL_OPEN'ın yinelenmesi (örn. reconnect yarışı)
    }

    let shell;
    try {
      shell = spawnSession(
        { cols, rows },
        (data) => send({ type: 'TERMINAL_OUTPUT', terminalSessionId, data }),
        () => {
          terminalSessions.delete(terminalSessionId);
          send({ type: 'TERMINAL_CLOSED', terminalSessionId });
          onLog(`🖥️  Terminal oturumu kapandı: ${terminalSessionId}`);
        },
      );
    } catch (e) {
      send({ type: 'TERMINAL_OUTPUT', terminalSessionId, data: `[runner] Terminal başlatılamadı: ${e.message}\r\n` });
      send({ type: 'TERMINAL_CLOSED', terminalSessionId });
      return;
    }

    terminalSessions.set(terminalSessionId, shell);
    onLog(`🖥️  Terminal oturumu açıldı: ${terminalSessionId}`);
  }

  function handleTerminalInput(message) {
    // xterm.js Enter tuşu için ham '\r' gönderir. PTY'de bu doğrudur ve olduğu gibi
    // iletilir; PTY yoksa (geri düşüş yolu) `spawnSession.write` bunu '\n'e çevirir, aksi
    // halde cmd.exe/bash pipe üzerinden gelen satırı tamamlanmış saymaz ve komut çalışmaz.
    const shell = terminalSessions.get(message.terminalSessionId);
    if (shell) shell.write(message.data);
  }

  function handleTerminalResize(message) {
    // PTY ile artık gerçek: tarayıcıdaki xterm boyutu shell'e iletiliyor (TUI'lerin
    // doğru çizmesi için şart). PTY yoksa no-op'a düşer.
    const shell = terminalSessions.get(message.terminalSessionId);
    if (shell) shell.resize(message.cols, message.rows);
  }

  function handleTerminalClose(message) {
    closeTerminalSession(message.terminalSessionId);
  }

  function closeTerminalSession(terminalSessionId) {
    const shell = terminalSessions.get(terminalSessionId);
    if (shell) {
      shell.close();
      terminalSessions.delete(terminalSessionId);
    }
  }

  function closeAllTerminalSessions() {
    for (const terminalSessionId of Array.from(terminalSessions.keys())) {
      closeTerminalSession(terminalSessionId);
    }
  }

  // ============ WebSocket Bağlantısı ============

  let ws = null;
  let heartbeatTimer = null;
  let reconnectTimer = null;
  let reconnectDelayMs = 2000;
  let stopped = false;
  const MAX_RECONNECT_DELAY_MS = 30000;

  function connect() {
    if (stopped) return;
    onStatus('connecting');
    const url = new URL(config.backendUrl);

    ws = new WebSocket(url, {
      headers: {
        'X-Organization-Id': config.organizationId,
        'X-Bridge-Token': config.bridgeToken,
      },
    });

    ws.on('open', () => {
      onLog('✅ Bridge bağlantısı kuruldu.');
      onStatus('connected');
      reconnectDelayMs = 2000; // başarılı bağlantıda backoff sıfırlanır
      flushOutbox();

      if (heartbeatTimer) clearInterval(heartbeatTimer);
      heartbeatTimer = setInterval(() => {
        send({ type: 'HEARTBEAT' });
      }, config.heartbeatIntervalMs || 30000);
    });

    ws.on('message', (data) => {
      handleMessage(data.toString()).catch((err) => {
        onLog(`Mesaj işlenirken beklenmeyen hata: ${err.message}`);
      });
    });

    ws.on('close', (code) => {
      if (stopped) return;
      onLog(`Bağlantı kapandı (kod: ${code}). ${reconnectDelayMs / 1000}s sonra tekrar denenecek.`);
      onStatus('disconnected');
      if (heartbeatTimer) clearInterval(heartbeatTimer);
      // Bağlantı koptuğunda açık terminal process'leri backend'siz kalır — orphan process
      // bırakmamak için hepsi kapatılıyor (kullanıcı yeniden bağlanınca yeni bir TERMINAL_OPEN gelir).
      closeAllTerminalSessions();
      scheduleReconnect();
    });

    ws.on('error', (err) => {
      onLog(`WebSocket hatası: ${err.message}`);
    });
  }

  function scheduleReconnect() {
    reconnectTimer = setTimeout(() => {
      connect();
      reconnectDelayMs = Math.min(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS);
    }, reconnectDelayMs);
  }

  /**
   * Bağlantı koptuğunda KAYBOLMAMASI gereken mesajlar. Görev sonucu ve görev logu tek
   * seferlik, kalıcı olaylardır: düşerlerse görev backend'de sonsuza kadar DISPATCHED kalır
   * ve terminal kaydı hiç yazılmaz. TERMINAL_* mesajları, TOOL_RESULT ve HEARTBEAT ise
   * yaşayan bir oturuma bağlıdır; yeniden bağlanınca anlamlarını yitirdikleri için
   * kuyruklanmaz.
   */
  const DURABLE_TYPES = new Set(['TASK_RESULT', 'LOG', 'TASK_PROMPT_UPDATE']);
  const MAX_OUTBOX = 500;
  const outbox = [];

  /** @returns {boolean} mesaj ANINDA gönderildi mi (false: kuyruğa alındı ya da atıldı). */
  function send(message) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(message));
      return true;
    }
    if (DURABLE_TYPES.has(message.type)) {
      if (outbox.length >= MAX_OUTBOX) outbox.shift(); // en eskiyi düşür, akışı tıkama
      outbox.push(message);
      onLog(`Bağlantı yok — ${message.type} kuyruğa alındı (${outbox.length} bekliyor).`);
    } else {
      onLog(`Bağlantı açık değil, mesaj gönderilemedi: ${message.type}`);
    }
    return false;
  }

  /** Yeniden bağlanınca biriken kalıcı mesajları SIRAYLA gönderir. */
  function flushOutbox() {
    if (!outbox.length) return;
    const queued = outbox.splice(0, outbox.length);
    onLog(`Bağlantı geri geldi — ${queued.length} bekleyen mesaj gönderiliyor.`);
    for (const m of queued) send(m);
  }

  // ============ Gelen Mesaj Yönlendirme ============

  async function handleMessage(raw) {
    let message;
    try {
      message = JSON.parse(raw);
    } catch (e) {
      onLog('Ayrıştırılamayan mesaj alındı, yok sayılıyor.');
      return;
    }

    switch (message.type) {
      case 'TOOL_CALL':
        await handleToolCall(message);
        break;
      case 'AI_ASSIST_RESULT': {
        // Bekleyen bir yardim isteginin cevabi (callId ile eslesiyor).
        const pending = pendingAssists.get(message.callId);
        if (pending) {
          pendingAssists.delete(message.callId);
          clearTimeout(pending.timer);
          pending.resolve({ success: message.success !== false, text: message.message || '' });
        }
        break;
      }
      case 'TASK_DISPATCH':
        await handleTaskDispatch(message);
        break;
      case 'TERMINAL_OPEN':
        handleTerminalOpen(message);
        break;
      case 'TERMINAL_INPUT':
        handleTerminalInput(message);
        break;
      case 'TERMINAL_RESIZE':
        handleTerminalResize(message);
        break;
      case 'TERMINAL_CLOSE':
        handleTerminalClose(message);
        break;
      default:
        onLog(`Bilinmeyen/işlenmeyen mesaj tipi: ${message.type}`);
    }
  }

  async function handleToolCall(message) {
    const { taskId, callId, toolName, arguments: args } = message;
    onLog(`🔧 Araç çağrısı: ${toolName}(${JSON.stringify(args)})`);

    const handler = TOOL_HANDLERS[toolName];
    let result;

    if (!handler) {
      result = { success: false, output: `Bilinmeyen araç: ${toolName}` };
    } else {
      try {
        result = await handler(args || {});
      } catch (e) {
        result = { success: false, output: `Araç çalıştırma hatası: ${e.message}` };
      }
    }

    onLog(result.success ? '  ✅ Başarılı' : `  ❌ Hata: ${result.output}`);

    send({
      type: 'TOOL_RESULT',
      taskId,
      callId,
      success: result.success,
      message: result.output,
    });
  }

  /**
   * `onTaskDispatch` sağlanmamışsa ya da `null`/`undefined` dönerse bilinçli olarak HİÇBİR
   * TASK_RESULT gönderilmez — görev artık web'den de tamamlanabiliyor (bkz.
   * TaskOrchestrationService.completeTask), bu yüzden burada bloke eden bir yanıt ZORUNLU değil.
   * `message` alanı TASK_DISPATCH'te backend tarafından görevin tam prompt'unu taşımak için
   * yeniden kullanılıyor (bkz. BridgeMessage.dispatch Javadoc'u) — burada `prompt` olarak okunup
   * callback'e 4. parametre olarak geçiriliyor (Electron'un native görev paneli, ayrı bir
   * kullanıcı-JWT'li REST çağrısı yapmadan tam metni görsün diye).
   */
  async function handleTaskDispatch(message) {
    const { taskId, taskType, title, message: prompt, arguments: projectInfo } = message;
    if (!onTaskDispatch) return;

    const project = projectInfo || {};
    const result = await onTaskDispatch(taskId, taskType, title, prompt, {
      projectId: project.projectId || null,
      repoUrl: project.repoUrl || null,
      defaultBranch: project.defaultBranch || null,
    });
    if (!result) return;

    send({
      type: 'TASK_RESULT',
      taskId,
      status: result.status,
      message: result.message,
      usedAgent: result.usedAgent,
    });
  }

  /** Bir görevin çalıştırma çıktısını kalıcı task logına yazdırır — backend'in zaten var olan
   * genel amaçlı LOG mesajını kullanır (AgentBridgeHandler.handleLog), yeni bir mekanizma yok. */
  /**
   * Bulut modelinden tek atislik bir yardim ister (commit mesaji / kod incelemesi).
   * Masaustunun kullanici-JWT'si olmadigi icin REST yerine bridge uzerinden akiyor; cevap
   * AI_ASSIST_RESULT ile callId eslesmesi uzerinden geri geliyor (TOOL_CALL/TOOL_RESULT ile
   * ayni desen, yalnizca yon ters).
   */
  const pendingAssists = new Map();
  const ASSIST_TIMEOUT_MS = 120000;

  function requestAiAssist(kind, content, taskId) {
    return new Promise((resolve) => {
      const callId = randomUUID();
      const timer = setTimeout(() => {
        pendingAssists.delete(callId);
        resolve({ success: false, text: 'Yanit zaman asimina ugradi.' });
      }, ASSIST_TIMEOUT_MS);
      pendingAssists.set(callId, { resolve, timer });

      const delivered = send({
        type: 'AI_ASSIST_REQUEST', callId, taskId, toolName: kind, message: content,
      });
      if (!delivered) {
        pendingAssists.delete(callId);
        clearTimeout(timer);
        resolve({ success: false, text: 'Backend baglantisi yok - AI yardimi su an kullanilamiyor.' });
      }
    });
  }

  function sendTaskLog(taskId, level, data) {
    send({ type: 'LOG', taskId, level, message: data });
  }

  /** Native görev panelinde prompt düzenlenip kaydedildiğinde — backend'de AgentBridgeHandler'ın
   * yeni TASK_PROMPT_UPDATE case'i tarafından TaskOrchestrationService.updatePrompt'a yönlendirilir
   * (kullanıcı-JWT'siz, bridge-token'lı yol — bkz. Faz E1). */
  function updateTaskPrompt(taskId, prompt) {
    send({ type: 'TASK_PROMPT_UPDATE', taskId, message: prompt });
  }

  /**
   * Bir görevin ait olduğu Kule projesinin GERÇEK GitHub reposunu `PROJECT_ROOT/
   * <projectId>/` altına hazırlar — yoksa klonlar, varsa `fetch` + `defaultBranch`'e geçip
   * `pull` eder. `repoUrl` verilmemişse (proje repo bilgisi hiç girilmemiş — ör. eski/manuel
   * görevler) hiçbir şey yapmadan doğrudan `PROJECT_ROOT`'u döner, tek-sabit-klasör eski
   * davranışı korunur. Özel repolar için ayrı bir kimlik bilgisi mekanizması YOK — Windows'ta
   * zaten kurulu Git Credential Manager (`git`in kendi ortam/kimlik bilgisi deposu) kullanılır,
   * `execFile` zaten tam `process.env` ile çalışıyor.
   */
  /**
   * @returns {Promise<{dir: string, ok: boolean, error: string|null}>}
   *
   * Sonuç açıkça raporlanır: yalnızca `dir` dönmek, klonlama başarısız olduğunda boş bir
   * klasörün yolunu geri vermek anlamına gelirdi; masaüstü "hazır" derken dosya ağacı boş
   * görünür ve kullanıcı nedenini hiçbir yerde göremezdi.
   */
  async function ensureProjectCheckout(projectId, repoUrl, defaultBranch) {
    if (!repoUrl) return { dir: PROJECT_ROOT, ok: true, error: null };
    if (!projectId) return { dir: PROJECT_ROOT, ok: true, error: null };

    const dir = path.join(PROJECT_ROOT, String(projectId));
    try {
      fs.mkdirSync(dir, { recursive: true });
    } catch (e) {
      return { dir: PROJECT_ROOT, ok: false, error: `Proje klasoru olusturulamadi: ${e.message}` };
    }

    if (!fs.existsSync(path.join(dir, '.git'))) {
      const cloneResult = await execGit(['clone', repoUrl, '.'], GIT_NETWORK_TIMEOUT_MS, dir);
      if (!cloneResult.success) {
        const error = `Depo klonlanamadi (${repoUrl}): ${String(cloneResult.output || '').trim()}`;
        onLog(error);
        return { dir, ok: false, error };
      }
    } else {
      await execGit(['fetch', 'origin'], GIT_NETWORK_TIMEOUT_MS, dir);
    }

    if (defaultBranch) {
      await execGit(['checkout', defaultBranch], null, dir);
      await execGit(['pull', 'origin', defaultBranch], GIT_NETWORK_TIMEOUT_MS, dir);
    }

    return { dir, ok: true, error: null };
  }

  /**
   * Electron'un native görev panelinin kullandığı, backend'e/WS'e HİÇ dokunmayan yerel terminal —
   * masaüstü uygulaması zaten runner'ın bulunduğu AYNI makinede çalıştığı için remote-terminal-
   * relay'e (RunnerTerminalWsHandler/RunnerTerminalSessionRegistry) hiç gerek yok. `handleTerminalOpen`
   * ile AYNI `spawnSession` çekirdeğini kullanır.
   */
  function openLocalTerminal(onData, cwd, size) {
    return spawnSession({ cwd, cols: size && size.cols, rows: size && size.rows }, onData, undefined);
  }

  /**
   * Bir ajanı (claude/agy/hermes/openclaw) DOĞRUDAN argv ile çalıştırır — kabuk üzerinden
   * metin olarak "yazmak" yerine. Böylece prompt'taki tırnak/çok satır/özel karakterler hiçbir
   * kaçış gerektirmez (eski yaklaşımda çok satırlı bir prompt komutu yarıda çalıştırıyordu).
   * PTY varken ajanın gerçek interaktif TUI'si açılır.
   */
  function openAgentSession(file, args, onData, onExit, cwd, size) {
    return spawnSession(
      { file, args, cwd, cols: size && size.cols, rows: size && size.rows },
      onData,
      onExit,
    );
  }

  /**
   * Hangi ajan CLI'lerinin bu makinede gerçekten KURULU olduğunu döner — masaüstü
   * seçicisi kurulu olmayanları devre dışı gösterebilsin diye ("seçtim ama hiçbir şey olmuyor"
   * durumunu yapısal olarak imkânsız kılar). Windows'ta `where`, diğerlerinde `which`.
   */
  function detectAgents(commands) {
    // `spawnSession` ile AYNI çözümleyiciyi kullanır — "seçicide kurulu görünüyor ama
    // başlatınca patlıyor" durumu (ör. yalnızca uzantısız bir sh sarmalayıcısı bulunması)
    // ancak böyle yapısal olarak imkânsız oluyor.
    const entries = Object.entries(commands || {});
    return Promise.resolve(Object.fromEntries(entries.map(([agent, file]) => [
      agent,
      !!file && !!lookupExecutable(file),
    ])));
  }

  return {
    start() {
      stopped = false;
      connect();
    },
    stop() {
      stopped = true;
      if (heartbeatTimer) clearInterval(heartbeatTimer);
      if (reconnectTimer) clearTimeout(reconnectTimer);
      closeAllTerminalSessions();
      if (ws) ws.close();
    },
    sendTaskLog,
    requestAiAssist,
    openLocalTerminal,
    openAgentSession,
    detectAgents,
    /**
     * Bir komut adını çalıştırılabilir `{file, args}`'a çevirir (Windows'ta mutlak yol + `.cmd`
     * sarmalama, bkz. `resolveExecutable`). Masaüstü, ajan dışı yardımcı komutları (ör. Omniroute
     * sunucu kontrolü) `child_process` ile çalıştırırken aynı çözümlemeyi kullanabilsin diye açık.
     */
    resolveExecutable: (file, args) => resolveExecutable(file, args || []),
    hasPty: () => !!ptyModule,
    /**
     * Electron'un native görev panelindeki git hızlı-aksiyonları için — AYNI `TOOL_HANDLERS`'ı
     * (bir AI ajanının TOOL_CALL ile çağırdığı, backend/WS'e hiç gitmeden) doğrudan çağırıyor.
     * `askConfirmation` (git_push/git_commit/git_checkout vb. riskli olanlarda) zaten `onConfirm`
     * callback'ine gidiyor — Electron'da bu native `dialog.showMessageBox`, tıpkı bir AI ajanının
     * tetiklediği onay gibi; ayrı bir onay mekanizması YAZILMADI.
     */
    async runTool(name, args, cwd) {
      const handler = TOOL_HANDLERS[name];
      if (!handler) return { success: false, output: `Bilinmeyen araç: ${name}` };
      return handler(args || {}, cwd);
    },
    updateTaskPrompt,
    ensureProjectCheckout,
  };
}

module.exports = { createBridge };
