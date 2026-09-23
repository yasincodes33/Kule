'use strict';

const fs = require('fs');
const path = require('path');
const readline = require('readline');
const { createBridge } = require('./bridge');

// ============ Konfigürasyon ============

const config = JSON.parse(fs.readFileSync(path.join(__dirname, 'config.json'), 'utf-8'));

// ============ Terminal Onay Yardımcısı ============
//
// CLI'ya özgü — bridge.js bunu bilmiyor, yalnızca `onConfirm`/`onTaskDispatch` callback'leri
// üzerinden soru sorulmasını istiyor. agentsaas-desktop (Electron) aynı callback'leri native
// dialog'larla dolduruyor, bu readline mantığına hiç ihtiyaç duymuyor.
const rl = readline.createInterface({ input: process.stdin, output: process.stdout });

// Ayni anda birden fazla rl.question() acilirsa istemler birbirine karisir ve kullanicinin
// cevabi yanlis soruya gider - onay istemi ile gorev sonucu istemi cakisabiliyordu.
// Tum terminal sorulari tek bir kuyruktan sirayla geciyor.
let promptQueue = Promise.resolve();

function ask(promptText) {
  const answered = promptQueue.then(
    () => new Promise((resolve) => rl.question(promptText, resolve))
  );
  promptQueue = answered.catch(() => {});
  return answered;
}

function askConfirmation(promptText) {
  return ask(`\n⚠️  ONAY GEREKİYOR: ${promptText}\nDevam edilsin mi? (e/h): `)
    .then((answer) => answer.trim().toLowerCase() === 'e');
}

// backend'deki common.domain.UsedAgent enum'ıyla birebir aynı olmalı — yeni bir araç eklenirse
// ikisi birlikte güncellenmeli (bkz. UsedAgent.java Javadoc'undaki aynı uyarı).
const KNOWN_AGENTS = new Set(['CLAUDE_CODE', 'ANTIGRAVITY', 'HERMES', 'OPENCLAW', 'OMNIROUTE', 'MANUAL']);

async function handleTaskDispatch(taskId, taskType, title) {
  console.log(`\n📋 Yeni görev alındı [${taskType}]: ${title}`);
  console.log('Görevi hangi araçla çözersen çöz (Claude Code, Antigravity, Omniroute, Hermes, ' +
    'OpenClaw veya doğrudan kendin) — runner bunu SENİN YERİNE ÇALIŞTIRMIYOR, yalnızca bitirdiğinde ' +
    'sonucu ve (opsiyonel) hangi aracı kullandığını buradan bildiriyorsun.');

  const resultText = await ask('Sonuç (COMPLETED için metin, başarısızlık için "FAIL: <sebep>"): ');
  const isFailure = resultText.trim().toUpperCase().startsWith('FAIL:');

  let usedAgent;
  if (!isFailure) {
    const agentInput = (await ask(
      `Hangi araçla tamamladın? (${Array.from(KNOWN_AGENTS).join('/')}, boş geçebilirsin): `
    )).trim().toUpperCase();
    if (agentInput && KNOWN_AGENTS.has(agentInput)) {
      usedAgent = agentInput;
    } else if (agentInput) {
      console.log(`  ⚠️  Tanınmayan araç adı "${agentInput}", bu alan boş bırakılıyor.`);
    }
  }

  return {
    status: isFailure ? 'FAILED' : 'COMPLETED',
    message: isFailure ? resultText.replace(/^FAIL:\s*/i, '') : resultText,
    usedAgent,
  };
}

// ============ Başlangıç ============

console.log(`AgentSaaS Local Bridge başlatılıyor — proje kökü: ${path.resolve(config.projectRoot)}`);

const bridge = createBridge(config, {
  onLog: (line) => console.log(line),
  onStatus: () => {}, // CLI'da ayrı bir durum göstergesine gerek yok — onLog zaten yeterli
  onConfirm: askConfirmation,
  onTaskDispatch: handleTaskDispatch,
});

bridge.start();

process.on('SIGINT', () => {
  console.log('\nKapatılıyor...');
  bridge.stop();
  rl.close();
  process.exit(0);
});
