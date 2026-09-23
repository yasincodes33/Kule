import { describe, expect, it } from 'vitest';
import { describeNotification, logLevelColor } from './enums';

describe('describeNotification', () => {
  it('TASK_COMPLETED için görev detayına link üretir', () => {
    const result = describeNotification('TASK_COMPLETED', { taskId: 'abc-123', title: 'Yuvarlama hatası' });
    expect(result.link).toBe('/tasks/abc-123');
    expect(result.text).toContain('Yuvarlama hatası');
    expect(result.c).toBe('var(--st-ok)');
  });

  it('TASK_FAILED kırmızı renk kullanır', () => {
    const result = describeNotification('TASK_FAILED', { taskId: 'abc', title: 'x' });
    expect(result.c).toBe('var(--st-fail)');
  });

  it('MEMBERSHIP_INVITED organizasyon sayfasına link üretir', () => {
    const result = describeNotification('MEMBERSHIP_INVITED', { membershipId: 'm-1', role: 'DEVELOPER' });
    expect(result.link).toBe('/organization');
    expect(result.text).toContain('DEVELOPER');
  });

  it('APPROVAL_REQUESTED onay kuyruğuna link üretir', () => {
    const result = describeNotification('APPROVAL_REQUESTED', { approvalId: 'a-1' });
    expect(result.link).toBe('/approvals');
  });

  it('eksik payload alanı olsa da patlamaz, boş string ile devam eder', () => {
    const result = describeNotification('TASK_COMPLETED', {});
    expect(result.link).toBe('/tasks/');
    expect(result.text).toContain('Görev tamamlandı');
  });
});

describe('logLevelColor', () => {
  it('bilinen bir seviye için doğru rengi döner', () => {
    expect(logLevelColor('ERROR')).toBe('var(--st-fail)');
    expect(logLevelColor('error')).toBe('var(--st-fail)');
  });

  it('bilinmeyen bir seviye için nötr varsayılanı döner', () => {
    expect(logLevelColor('KUANTUM')).toBe('var(--ink-2)');
  });
});
