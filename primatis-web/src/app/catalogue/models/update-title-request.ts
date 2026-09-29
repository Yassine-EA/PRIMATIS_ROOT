import { Language } from './language';


export interface UpdateTitleRequest {
  isbn?: string | null;
  title?: string;
  subtitle?: string | null;
  summary?: string | null;
  publicationYear?: number | null;
  language?: Language;
  pageCount?: number | null;
  publisher?: string | null;
  coverImageUrl?: string | null;
  authorIds?: number[];
  genreIds?: number[];
}
