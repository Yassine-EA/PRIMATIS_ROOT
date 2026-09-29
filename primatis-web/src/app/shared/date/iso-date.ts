/**
 * Conversion entre une date calendaire ISO `AAAA-MM-JJ` (contrat REST, sans
 * heure) et l'objet `Date` manipulé par `p-datepicker`.
 *
 * Règle anti-décalage : une date métier sans heure reste un jour de
 * calendrier. On construit donc la `Date` avec les composantes locales
 * (`new Date(a, m - 1, j)`, minuit local) et on relit avec les accesseurs
 * locaux (`getFullYear/getMonth/getDate`). Jamais `toISOString()` : il
 * convertit en UTC et peut renvoyer la veille (ex. minuit à Bruxelles).
 */
const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

/** `AAAA-MM-JJ` → `Date` locale (minuit) ; `null` si absent ou invalide (ex. 2026-02-31). */
export function isoDateToDate(iso: string | null | undefined): Date | null {
  const match = iso ? ISO_DATE.exec(iso) : null;
  if (!match) {
    return null;
  }
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const date = new Date(year, month - 1, day);
  const roundTrips = date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
  return roundTrips ? date : null;
}

/** `Date` → jour de calendrier local au format `AAAA-MM-JJ` ; `null` si absente ou invalide. */
export function dateToIsoDate(date: Date | null | undefined): string | null {
  if (!date || Number.isNaN(date.getTime())) {
    return null;
  }
  const year = String(date.getFullYear()).padStart(4, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
