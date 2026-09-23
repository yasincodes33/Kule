'use strict';

/**
 * Ajan CLI'lerinin GERÇEK çalıştırma biçimleri.
 *
 * Önceki sürümde yalnızca Claude Code'un gerçek bir CLI olduğu varsayılıyordu; bu YANLIŞTI —
 * dördü de gerçek, kurulu araçlar. Aşağıdaki biçimlerin hepsi araçların kendi `--help`
 * çıktılarından doğrulandı:
 *
 *   claude   : `claude [options] [prompt]` — "starts an interactive session by default,
 *              use -p/--print for non-interactive output"
 *   agy      : `-i/--prompt-interactive` (ilk prompt'la interaktif devam), `-p/--print` (tek sefer)
 *   hermes   : `hermes chat` (interaktif), `hermes -z PROMPT` (tek sefer)
 *   openclaw : `openclaw agent --local -m <text>` (ayrı bir interaktif prompt biçimi yok)
 *   omniroute: `omniroute repl -s <prompt>` (çok turlu interaktif REPL — prompt argümanı almıyor,
 *              görev metni sistem prompt'u olarak veriliyor), `omniroute chat --stream <prompt>`
 *              (tek sefer). Diğerlerinden farkı: önce kendi YÖNLENDİRİCİ SUNUCUSU çalışıyor
 *              olmalı (bkz. main.js `ensureOmnirouteServer`).
 *
 * Komutlar `{ file, args }` olarak dönüyor ve argv ile DOĞRUDAN spawn ediliyor (kabuğa metin
 * yazılmıyor) — prompt'taki tırnak, boşluk ve satır sonları hiçbir kaçış gerektirmiyor.
 */

/** Her ajanın uygunluk tespiti (`where`/`which`) için çalıştırılabilir adı. */
const AGENT_BINARIES = {
  CLAUDE_CODE: 'claude',
  ANTIGRAVITY: 'agy',
  HERMES: 'hermes',
  OPENCLAW: 'openclaw',
  OMNIROUTE: 'omniroute',
  MANUAL: null,
};

const AGENT_LABELS = {
  CLAUDE_CODE: 'Claude Code',
  ANTIGRAVITY: 'Antigravity',
  HERMES: 'Hermes',
  OPENCLAW: 'OpenClaw',
  OMNIROUTE: 'Omniroute',
  MANUAL: 'Manuel (sadece shell)',
};

/**
 * @returns {{file: string, args: string[]}|null} — `null` ise o araç için otomatik başlatma
 * komutu yok (yalnızca MANUAL); çağıran taraf düz shell açar.
 */
function commandFor(agent, prompt, options) {
  const interactive = !(options && options.interactive === false);
  const text = prompt || '';

  switch (agent) {
    case 'CLAUDE_CODE':
      return interactive
        ? { file: 'claude', args: text ? [text] : [] }
        : { file: 'claude', args: ['-p', text] };
    case 'ANTIGRAVITY':
      return interactive
        ? { file: 'agy', args: text ? ['-i', text] : [] }
        : { file: 'agy', args: ['-p', text] };
    case 'HERMES':
      // `hermes chat` bir prompt argümanı almıyor — interaktif oturum açılır, prompt elle
      // yapıştırılır; tek-seferlik mod için `-z` var.
      return interactive
        ? { file: 'hermes', args: ['chat'] }
        : { file: 'hermes', args: ['-z', text] };
    case 'OPENCLAW':
      return { file: 'openclaw', args: ['agent', '--local', '-m', text] };
    case 'OMNIROUTE':
      // `repl` prompt argümanı almıyor; görev metnini taşıyabilecek tek alan `-s` (sistem
      // prompt'u) — oturum görevin bağlamıyla başlar, kullanıcı üstüne konuşarak devam eder.
      return interactive
        ? { file: 'omniroute', args: text ? ['repl', '-s', text] : ['repl'] }
        : { file: 'omniroute', args: ['chat', '--stream', text] };
    default:
      return null;
  }
}

/** Ajanı başlatmadan önce sağlanması gereken ön koşullar (şimdilik yalnızca Omniroute'un sunucusu). */
const NEEDS_OMNIROUTE_SERVER = 'OMNIROUTE';

module.exports = { AGENT_BINARIES, AGENT_LABELS, commandFor, NEEDS_OMNIROUTE_SERVER };
