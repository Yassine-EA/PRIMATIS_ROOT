/** `''` (champ vidé) devient `null` (efface), sinon la valeur trimée. */
export function normalizeOptional(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

/**
 * `''`/`null`/`undefined` deviennent `null`, sinon un nombre — jamais `NaN`
 * propagé. `value` peut être un `number` : `formControlName` sur un
 * `input[type=number]` déclenche le `NumberValueAccessor` d'Angular, qui
 * écrit `number | null` dans le `FormControl` au runtime quel que soit le
 * type `string` déclaré côté `FormBuilder` (mismatch non détecté par le
 * compilateur, publicationYear/pageCount de `staff-title-create-page` et
 * `staff-title-detail-page`).
 */
export function parseOptionalInt(value: string | number | null | undefined): number | null {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null;
  }
  const trimmed = value.trim();
  if (trimmed.length === 0) {
    return null;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}
