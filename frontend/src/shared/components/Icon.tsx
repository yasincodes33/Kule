// Kule — ikon primitifi. Yollar Claude Design çıktısından (kule-ui.jsx) portlandı.
const PATHS: Record<string, string> = {
  board: 'M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z',
  terminal: 'M4 17l6-6-6-6M12 19h8',
  shield: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10zM9 12l2 2 4-4',
  server: 'M3 4h18v6H3zM3 14h18v6H3zM7 7h.01M7 17h.01',
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  chev: 'M6 9l6 6 6-6',
  check: 'M20 6L9 17l-5-5',
  x: 'M18 6L6 18M6 6l12 12',
  search: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM21 21l-4.3-4.3',
  plus: 'M12 5v14M5 12h14',
  activity: 'M22 12h-4l-3 9L9 3l-3 9H2',
  clock: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2',
  alert: 'M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0zM12 9v4M12 17h.01',
  cpu: 'M5 5h14v14H5zM9 9h6v6H9zM9 1v4M15 1v4M9 19v4M15 19v4M1 9h4M1 15h4M19 9h4M19 15h4',
  radio: 'M12 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4M7.8 16.2a6 6 0 0 1 0-8.4M16.2 7.8a6 6 0 0 1 0 8.4M4.9 19.1a10 10 0 0 1 0-14.2M19.1 4.9a10 10 0 0 1 0 14.2',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  filter: 'M22 3H2l8 9.5V19l4 2v-8.5z',
  copy: 'M9 9h10v12H9zM5 15H3V3h12v2',
  mail: 'M2 5h20v14H2zM2 6l10 7 10-7',
  key: 'M15.5 8.5a3 3 0 1 0 0-.1M21 2l-9.6 9.6M14 8l-2 2M11.5 11.5L3 20v2h3l1-2h2v-2h2l1.5-1.5',
  logout: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9',
  pause: 'M6 4h4v16H6zM14 4h4v16h-4z',
  play: 'M6 3l14 9-14 9z',
  refresh: 'M21 12a9 9 0 1 1-3-6.7M21 4v5h-5',
  layers: 'M12 2l10 5-10 5L2 7zM2 12l10 5 10-5M2 17l10 5 10-5',
  list: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
  git: 'M6 3v12a3 3 0 0 0 3 3h6M6 21a3 3 0 1 0 0-6 3 3 0 0 0 0 6M18 9a3 3 0 1 0 0-6 3 3 0 0 0 0 6M18 21a3 3 0 1 0 0-6 3 3 0 0 0 0 6',
  bell: 'M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0',
};

export function Icon({
  n,
  s = 16,
  c = 'currentColor',
  sw = 1.75,
  style,
}: {
  n: string;
  s?: number;
  c?: string;
  sw?: number;
  style?: React.CSSProperties;
}) {
  return (
    <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth={sw} strokeLinecap="square" strokeLinejoin="miter" style={style} aria-hidden="true">
      <path d={PATHS[n] || PATHS.activity} />
    </svg>
  );
}
