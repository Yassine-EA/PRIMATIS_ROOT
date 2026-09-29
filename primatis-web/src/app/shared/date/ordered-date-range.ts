import { AbstractControl, ValidationErrors } from '@angular/forms';

import { dateToIsoDate } from './iso-date';


export function orderedDateRange(fromKey: string, toKey: string, errorKey: string) {
  return (group: AbstractControl): ValidationErrors | null => {
    const from = dateToIsoDate(group.get(fromKey)?.value as Date | null);
    const to = dateToIsoDate(group.get(toKey)?.value as Date | null);
    return from && to && from > to ? { [errorKey]: true } : null;
  };
}
