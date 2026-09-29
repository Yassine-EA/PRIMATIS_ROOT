import { Language } from './language';
import { TitleStatus } from './title-status';


export interface TitleResponse {
  id: number;
  isbn: string | null;
  title: string;
  subtitle: string | null;
  publicationYear: number | null;
  language: Language;
  publisher: string | null;
  coverImageUrl: string | null;
  titleStatus: TitleStatus;
  authorNames: string[];
  availableCopies: number;
}
