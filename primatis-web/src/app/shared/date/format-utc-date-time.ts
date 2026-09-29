const PARTS = new Intl.DateTimeFormat('fr-BE', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: 'UTC',
});

/**
 * Affiche un `Instant` ISO reçu de l'API (ex. `2026-09-19T18:18:29.001909Z`)
 * au format français `19/09/2026 18:18 UTC`.
 *
 * Fuseau : UTC, comme toutes les pages Member (`timeZone: 'UTC'`,
 * fr-BE) et comme l'horloge applicative et les filtres de jour du backend
 * (jours UTC). L'heure est suffixée `UTC` pour lever toute ambiguïté avec
 * l'heure locale de Bruxelles (UTC+1/+2). Valeur absente → `emptyLabel` ;
 * valeur illisible → renvoyée telle quelle (jamais `Invalid Date`).
 */
export function formatUtcDateTime(value: string | null | undefined, emptyLabel = '—'): string {
  if (!value) {
    return emptyLabel;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const parts = Object.fromEntries(PARTS.formatToParts(date).map((part) => [part.type, part.value]));
  return `${parts['day']}/${parts['month']}/${parts['year']} ${parts['hour']}:${parts['minute']} UTC`;
}
