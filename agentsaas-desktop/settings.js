'use strict';

(async () => {
  const fields = ['backendUrl', 'frontendUrl', 'organizationId', 'bridgeToken', 'projectRoot'];
  const checkboxes = ['requireConfirmation', 'startAtLogin'];
  // backendUrl ve frontendUrl neredeyse hep aynı kalır (yerel geliştirme adresleri), bu
  // yüzden hazır gelir. organizationId/bridgeToken/projectRoot kişiye ve organizasyona
  // özeldir; mantıklı bir varsayılanı olmadığı için boş bırakılır.
  const DEFAULTS = {
    backendUrl: 'ws://localhost:8081/ws/agent-bridge',
    frontendUrl: 'http://localhost:5173',
  };

  const config = await window.kuleDesktop.loadConfig();
  for (const f of fields) {
    const el = document.getElementById(f);
    if (!el) continue;
    if (config && config[f] != null) el.value = config[f];
    else if (DEFAULTS[f]) el.value = DEFAULTS[f];
  }
  if (config) {
    for (const c of checkboxes) {
      const el = document.getElementById(c);
      if (el) el.checked = !!config[c];
    }
  }

  document.getElementById('save').addEventListener('click', async () => {
    const status = document.getElementById('status');
    const newConfig = {
      heartbeatIntervalMs: 30000,
      commandTimeoutMs: 30000,
      gitNetworkTimeoutMs: 600000,
    };
    for (const f of fields) {
      newConfig[f] = document.getElementById(f).value.trim();
    }
    for (const c of checkboxes) {
      newConfig[c] = document.getElementById(c).checked;
    }

    if (!newConfig.backendUrl || !newConfig.organizationId || !newConfig.bridgeToken || !newConfig.projectRoot) {
      status.style.color = '#e07a5f';
      status.textContent = 'Backend adresi, organizasyon ID, bridge token ve proje kökü zorunlu.';
      return;
    }

    await window.kuleDesktop.saveConfig(newConfig);
    status.style.color = '#7dd39a';
    status.textContent = 'Kaydedildi — runner bağlanmayı deniyor. Tepsi ikonundan durumu izleyebilirsin.';
  });
})();
