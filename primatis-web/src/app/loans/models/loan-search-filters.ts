import { LoanStatus } from './loan-status';


export interface LoanSearchFilters {
  q?: string;
  loanStatus?: LoanStatus;
  loanDateFrom?: string;
  loanDateTo?: string;
  dueDateFrom?: string;
  dueDateTo?: string;
}
