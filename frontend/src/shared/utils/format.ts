type TimeInput = number | string | Date;

const pad = (n: number) => String(n).padStart(2, '0');

export const clock = (ts: TimeInput) => {
  const d = new Date(ts);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

export const stamp = (ts: TimeInput) => {
  const d = new Date(ts);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${String(d.getMilliseconds()).padStart(3, '0')}`;
};

export const ago = (ts: TimeInput) => {
  const m = Math.max(0, Math.round((Date.now() - new Date(ts).getTime()) / 60000));
  if (m < 1) return 'az önce';
  if (m < 60) return `${m} dk önce`;
  return `${Math.round(m / 60)} sa önce`;
};

/** Gelecekteki bir zaman damgasına kalan süre (onay son kullanma tarihi gibi). */
export const until = (ts: TimeInput) => {
  const m = Math.round((new Date(ts).getTime() - Date.now()) / 60000);
  if (m <= 0) return 'süresi doldu';
  if (m < 60) return `${m} dk içinde`;
  return `${Math.round(m / 60)} sa içinde`;
};
