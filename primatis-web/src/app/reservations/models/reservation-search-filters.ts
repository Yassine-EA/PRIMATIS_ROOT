import { ReservationStatus } from './reservation-status';


export interface ReservationSearchFilters {
  q?: string;
  reservationStatus?: ReservationStatus;
  reservationDateFrom?: string;
  reservationDateTo?: string;
  expirationDateFrom?: string;
  expirationDateTo?: string;
}
