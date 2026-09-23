import { describe, expect, it, vi } from 'vitest';
import { ago, clock, stamp, until } from './format';

describe('clock', () => {
  it('saat:dakika:saniye biçiminde, sıfırla dolgulu döner', () => {
    const ts = new Date(2026, 0, 1, 9, 5, 3).getTime();
    expect(clock(ts)).toBe('09:05:03');
  });
});

describe('stamp', () => {
  it('milisaniyeyi de içerir', () => {
    const ts = new Date(2026, 0, 1, 9, 5, 3, 42).getTime();
    expect(stamp(ts)).toBe('09:05:03.042');
  });
});

describe('ago', () => {
  it('1 dakikadan az ise "az önce" döner', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 0, 1, 12, 0, 0));
    expect(ago(new Date(2026, 0, 1, 11, 59, 50))).toBe('az önce');
    vi.useRealTimers();
  });

  it('60 dakikadan az ise dakika cinsinden döner', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 0, 1, 12, 0, 0));
    expect(ago(new Date(2026, 0, 1, 11, 45, 0))).toBe('15 dk önce');
    vi.useRealTimers();
  });

  it('60 dakikadan uzunsa saat cinsinden döner', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 0, 1, 12, 0, 0));
    expect(ago(new Date(2026, 0, 1, 9, 0, 0))).toBe('3 sa önce');
    vi.useRealTimers();
  });

  it('gelecekteki bir zaman negatif olmadan 0 dakikaya kırpılır', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 0, 1, 12, 0, 0));
    expect(ago(new Date(2026, 0, 1, 13, 0, 0))).toBe('az önce');
    vi.useRealTimers();
  });
});

describe('until', () => {
  it('gelecekteki bir zaman için kalan dakikayı döner', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 0, 1, 12, 0, 0));
    expect(until(new Date(2026, 0, 1, 12, 20, 0))).toBe('20 dk içinde');
    vi.useRealTimers();
  });

  it('geçmişteki bir zaman için "süresi doldu" döner', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 0, 1, 12, 0, 0));
    expect(until(new Date(2026, 0, 1, 11, 0, 0))).toBe('süresi doldu');
    vi.useRealTimers();
  });
});
