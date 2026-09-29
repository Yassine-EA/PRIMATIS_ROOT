import { Language } from '../../catalogue/models/language';

export interface LanguageOption {
  label: string;
  value: Language;
}


export const LANGUAGE_OPTIONS: LanguageOption[] = [
  { label: 'Français', value: 'FR' },
  { label: 'Anglais', value: 'EN' },
  { label: 'Néerlandais', value: 'NL' },
  { label: 'Allemand', value: 'DE' },
  { label: 'Espagnol', value: 'ES' },
  { label: 'Italien', value: 'IT' },
  { label: 'Latin', value: 'LA' },
];
