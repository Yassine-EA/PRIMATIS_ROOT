import { normalizeOptional, parseOptionalInt } from './form-value-normalization';

describe('normalizeOptional', () => {
  it('should trim a non-empty value', () => {
    expect(normalizeOptional('  Germinal  ')).toBe('Germinal');
  });

  it('should return null for an empty or blank value', () => {
    expect(normalizeOptional('')).toBeNull();
    expect(normalizeOptional('   ')).toBeNull();
  });
});

describe('parseOptionalInt', () => {
  it('should return null for null, undefined and empty/blank strings', () => {
    expect(parseOptionalInt(null)).toBeNull();
    expect(parseOptionalInt(undefined)).toBeNull();
    expect(parseOptionalInt('')).toBeNull();
    expect(parseOptionalInt('   ')).toBeNull();
  });

  it('should parse a numeric string', () => {
    expect(parseOptionalInt('123')).toBe(123);
    expect(parseOptionalInt('0')).toBe(0);
  });

  it('should return null for a non-numeric string', () => {
    expect(parseOptionalInt('abc')).toBeNull();
  });

  it('should pass through a number value unchanged (input[type=number] NumberValueAccessor)', () => {
    expect(parseOptionalInt(123)).toBe(123);
    expect(parseOptionalInt(0)).toBe(0);
  });

  it('should return null for a non-finite number', () => {
    expect(parseOptionalInt(NaN)).toBeNull();
    expect(parseOptionalInt(Infinity)).toBeNull();
  });
});
