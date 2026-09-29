import { AccountStatus } from '../../user/models/account-status';
import { ArticleStatus } from '../../articles/models/article-status';
import { AvailabilityStatus } from '../../catalogue/models/availability-status';
import { CopyCondition } from '../../catalogue/models/copy-condition';
import { FineStatus } from '../../fines/models/fine-status';
import { LoanStatus } from '../../loans/models/loan-status';
import { MemberStatus } from '../../user/models/member-status';
import { ReservationStatus } from '../../reservations/models/reservation-status';
import { TitleStatus } from '../../catalogue/models/title-status';


export type StatusTagSeverity = 'success' | 'secondary' | 'info' | 'warn' | 'danger' | 'contrast';

export function loanStatusSeverity(status: LoanStatus): StatusTagSeverity {
  switch (status) {
    case 'ACTIVE':
      return 'success';
    case 'OVERDUE':
      return 'danger';
    case 'RETURNED':
      return 'secondary';
  }
}

export function reservationStatusSeverity(status: ReservationStatus): StatusTagSeverity {
  switch (status) {
    case 'WAITING':
      return 'info';
    case 'READY':
      return 'success';
    case 'FULFILLED':
      return 'secondary';
    case 'CANCELLED':
      return 'danger';
    case 'EXPIRED':
      return 'warn';
  }
}

export function fineStatusSeverity(status: FineStatus): StatusTagSeverity {
  switch (status) {
    case 'UNPAID':
      return 'danger';
    case 'PAID':
      return 'success';
    case 'CANCELLED':
      return 'secondary';
  }
}

export function memberStatusSeverity(status: MemberStatus): StatusTagSeverity {
  switch (status) {
    case 'ACTIVE':
      return 'success';
    case 'BLOCKED':
      return 'danger';
    case 'EXPIRED':
      return 'warn';
  }
}

export function accountStatusSeverity(status: AccountStatus): StatusTagSeverity {
  return status === 'ACTIVE' ? 'success' : 'danger';
}

export function articleStatusSeverity(status: ArticleStatus): StatusTagSeverity {
  if (status === 'PUBLISHED') {
    return 'success';
  }
  if (status === 'DRAFT') {
    return 'warn';
  }
  return 'secondary';
}

export function titleStatusSeverity(status: TitleStatus): StatusTagSeverity {
  return status === 'ACTIVE' ? 'success' : 'warn';
}


export function copyAvailabilityStatusSeverity(status: AvailabilityStatus): StatusTagSeverity {
  switch (status) {
    case 'AVAILABLE':
      return 'success';
    case 'ON_LOAN':
    case 'RESERVED':
      return 'warn';
    case 'UNAVAILABLE':
      return 'danger';
  }
}


export function copyConditionSeverity(condition: CopyCondition): StatusTagSeverity {
  switch (condition) {
    case 'GOOD':
      return 'success';
    case 'DAMAGED':
      return 'warn';
    case 'LOST':
    case 'OUT_OF_SERVICE':
      return 'danger';
  }
}
