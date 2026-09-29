import { FineStatus } from './fine-status';


export interface FineSearchFilters {
  q?: string;
  fineStatus?: FineStatus;
  issuedDateFrom?: string;
  issuedDateTo?: string;
  closedDateFrom?: string;
  closedDateTo?: string;
}
