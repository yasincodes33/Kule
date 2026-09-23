'use strict';

const listEl = document.getElementById('list');
const emptyState = document.getElementById('empty-state');
const statusDot = document.getElementById('status-dot');
const statusText = document.getElementById('status-text');

document.getElementById('settings-btn').addEventListener('click', () => {
  window.kuleDashboard.openSettings();
});

const STATUS_LABEL = {
  preparing: 'Hazırlanıyor…',
  waiting: 'Bekliyor',
  completed: 'Tamamlandı',
  failed: 'Başarısız',
};

function renderRow(entry) {
  const row = document.createElement('div');
  row.className = 'task-row';
  row.dataset.taskId = entry.taskId;

  const main = document.createElement('div');
  main.className = 'task-main';
  const title = document.createElement('div');
  title.className = 'task-title';
  title.textContent = `[${entry.taskType}] ${entry.title}`;
  const sub = document.createElement('div');
  sub.className = 'task-sub';
  sub.textContent = new Date(entry.receivedAt).toLocaleTimeString('tr-TR');
  main.appendChild(title);
  main.appendChild(sub);

  const badge = document.createElement('span');
  badge.className = `badge ${entry.status}`;
  badge.textContent = STATUS_LABEL[entry.status] || entry.status;

  row.appendChild(main);
  row.appendChild(badge);
  row.addEventListener('click', () => window.kuleDashboard.openTask(entry.taskId));
  return row;
}

function upsertRow(entry) {
  emptyState.style.display = 'none';
  const existing = listEl.querySelector(`[data-task-id="${entry.taskId}"]`);
  const row = renderRow(entry);
  if (existing) existing.replaceWith(row);
  else listEl.insertBefore(row, listEl.firstChild);
}

window.kuleDashboard.onStatus((status) => {
  statusDot.className = `dot ${status}`;
  statusText.textContent = status === 'connected' ? 'Bağlı' : status === 'connecting' ? 'Bağlanıyor…' : 'Bağlı değil';
});

window.kuleDashboard.onTaskEvent((entry) => upsertRow(entry));

window.kuleDashboard.getHistory().then((history) => {
  for (const entry of history) upsertRow(entry);
});
