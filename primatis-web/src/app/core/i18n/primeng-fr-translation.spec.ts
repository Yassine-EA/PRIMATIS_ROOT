import { PRIMENG_FR_TRANSLATION } from './primeng-fr-translation';

describe('PRIMENG_FR_TRANSLATION', () => {
  it('should produce jj/mm/aaaa dates with a Monday-first French calendar', () => {
    expect(PRIMENG_FR_TRANSLATION.dateFormat).toBe('dd/mm/yy');
    expect(PRIMENG_FR_TRANSLATION.firstDayOfWeek).toBe(1);
    expect(PRIMENG_FR_TRANSLATION.monthNames).toHaveLength(12);
    expect(PRIMENG_FR_TRANSLATION.monthNames?.[8]).toBe('septembre');
    expect(PRIMENG_FR_TRANSLATION.dayNames).toHaveLength(7);
    expect(PRIMENG_FR_TRANSLATION.dayNamesMin?.[1]).toBe('L');
  });

  it('should not override non-calendar PrimeNG labels', () => {
    expect(PRIMENG_FR_TRANSLATION.accept).toBeUndefined();
    expect(PRIMENG_FR_TRANSLATION.reject).toBeUndefined();
  });
});
