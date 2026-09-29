import { ReservationCopyResponse } from './reservation-copy-response';
import { ReservationMemberResponse } from './reservation-member-response';
import { ReservationStatus } from './reservation-status';
import { ReservationTitleResponse } from './reservation-title-response';


export interface ReservationResponse {
  id: number;
  member: ReservationMemberResponse;
  title: ReservationTitleResponse;
  assignedCopy: ReservationCopyResponse | null;
  fulfilledByLoanId: number | null;
  reservationDate: string;
  expirationDate: string | null;
  reservationStatus: ReservationStatus;
  createdAt: string;
  updatedAt: string;
}
