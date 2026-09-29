import { dateToIsoDate, isoDateToDate } from './iso-date';

describe('iso-date', () => {
  it('should convert an ISO date to a local calendar Date without shifting the day', () => {
    const date = isoDateToDate('2026-09-20');

    expect(date).not.toBeNull();
    expect([date!.getFullYear(), date!.getMonth(), date!.getDate()]).toEqual([2026, 8, 20]);
    expect([date!.getHours(), date!.getMinutes()]).toEqual([0, 0]);
  });

  it('should convert a Date to a local ISO calendar day, whatever the time of day', () => {
    expect(dateToIsoDate(new Date(2026, 8, 20))).toBe('2026-09-20');
    expect(dateToIsoDate(new Date(2026, 8, 20, 0, 0, 1))).toBe('2026-09-20');
    // 23:59 local reste le même jour : toISOString() (UTC) renverrait le lendemain ou la veille selon le fuseau.
    expect(dateToIsoDate(new Date(2026, 8, 20, 23, 59, 59))).toBe('2026-09-20');
  });

  it('should round-trip boundary days (year end, leap day, month start)', () => {
    for (const iso of ['2026-01-01', '2026-12-31', '2028-02-29', '2026-03-29', '2026-10-25']) {
      expect(dateToIsoDate(isoDateToDate(iso))).toBe(iso);
    }
  });

  it('should return null for empty or invalid values', () => {
    expect(isoDateToDate(null)).toBeNull();
    expect(isoDateToDate('')).toBeNull();
    expect(isoDateToDate('20/09/2026')).toBeNull();
    expect(isoDateToDate('2026-02-31')).toBeNull();
    expect(dateToIsoDate(null)).toBeNull();
    expect(dateToIsoDate(new Date(Number.NaN))).toBeNull();
  });
});
