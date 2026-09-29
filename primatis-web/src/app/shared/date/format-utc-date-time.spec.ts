import { formatUtcDateTime } from './format-utc-date-time';

describe('formatUtcDateTime', () => {
  it('should format an ISO instant in French with the UTC suffix and no seconds', () => {
    expect(formatUtcDateTime('2026-09-19T18:18:29.001909Z')).toBe('19/09/2026 18:18 UTC');
  });

  it('should keep the UTC day at the day boundaries', () => {
    expect(formatUtcDateTime('2026-09-20T23:59:59Z')).toBe('20/09/2026 23:59 UTC');
    expect(formatUtcDateTime('2026-09-21T00:00:00Z')).toBe('21/09/2026 00:00 UTC');
    expect(formatUtcDateTime('2026-12-31T23:59:00Z')).toBe('31/12/2026 23:59 UTC');
  });

  it('should render midnight as 00:00 (24-hour clock), not 24:00', () => {
    expect(formatUtcDateTime('2026-01-01T00:05:00Z')).toBe('01/01/2026 00:05 UTC');
  });

  it('should honour an explicit offset by converting to UTC', () => {
    expect(formatUtcDateTime('2026-09-19T20:18:00+02:00')).toBe('19/09/2026 18:18 UTC');
  });

  it('should return the placeholder for an absent value and the raw text for an unreadable one', () => {
    expect(formatUtcDateTime(null)).toBe('—');
    expect(formatUtcDateTime(undefined)).toBe('—');
    expect(formatUtcDateTime('')).toBe('—');
    expect(formatUtcDateTime(null, 'Aucune')).toBe('Aucune');
    expect(formatUtcDateTime('pas une date')).toBe('pas une date');
  });
});
