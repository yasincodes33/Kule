/** Kule — marka işareti: bir kontrol kulesi + tepesinde yanan bir işaret feneri. */
function TowerMark({ s }: { s: number }) {
  return (
    <svg width={s} height={s} viewBox="0 0 24 24" aria-hidden="true" style={{ flex: 'none', overflow: 'visible' }}>
      <polygon points="7,22 17,22 14.2,8.5 9.8,8.5" fill="var(--ink-2)" />
      <rect x="9.3" y="8.5" width="5.4" height="1.6" fill="var(--color-bg)" opacity="0.5" />
      <rect x="8.3" y="14" width="7.4" height="1.4" fill="var(--color-bg)" opacity="0.5" />
      <circle cx="12" cy="5.4" r="2.6" fill="var(--color-accent)" style={{ filter: 'drop-shadow(0 0 3px var(--color-accent))' }} />
    </svg>
  );
}

export function Logo({ size = 'md' }: { size?: 'md' | 'sm' }) {
  const s = size === 'sm' ? 16 : 20;
  return (
    <>
      <TowerMark s={s} />
      <span className={'k-logo-w' + (size === 'sm' ? ' sm' : '')}>KULE</span>
    </>
  );
}
