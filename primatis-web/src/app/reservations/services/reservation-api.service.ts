import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { PageResponse } from '../../core/models/page-response';
import { CreateOwnReservationRequest } from '../models/create-own-reservation-request';
import { CreateReservationRequest } from '../models/create-reservation-request';
import { ReservationResponse } from '../models/reservation-response';
import { ReservationSearchFilters } from '../models/reservation-search-filters';


@Injectable({ providedIn: 'root' })
export class ReservationApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  listOwnReservations(page = 0, size = 20): Observable<PageResponse<ReservationResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<ReservationResponse>>(`${this.baseUrl}/me/reservations`, { params });
  }

  
  listReservations(
    page = 0,
    size = 20,
    filters: ReservationSearchFilters = {},
  ): Observable<PageResponse<ReservationResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    const q = filters.q?.trim();
    if (q) {
      params = params.set('q', q);
    }
    for (const key of [
      'reservationStatus',
      'reservationDateFrom',
      'reservationDateTo',
      'expirationDateFrom',
      'expirationDateTo',
    ] as const) {
      const value = filters[key];
      if (value) {
        params = params.set(key, value);
      }
    }
    return this.http.get<PageResponse<ReservationResponse>>(`${this.baseUrl}/reservations`, { params });
  }

  createOwnReservation(request: CreateOwnReservationRequest): Observable<ReservationResponse> {
    return this.http.post<ReservationResponse>(`${this.baseUrl}/me/reservations`, request);
  }

  createReservation(request: CreateReservationRequest): Observable<ReservationResponse> {
    return this.http.post<ReservationResponse>(`${this.baseUrl}/reservations`, request);
  }

  cancelOwnReservation(reservationId: number): Observable<ReservationResponse> {
    return this.http.post<ReservationResponse>(`${this.baseUrl}/me/reservations/${reservationId}/cancel`, null);
  }

  cancelReservation(reservationId: number): Observable<ReservationResponse> {
    return this.http.post<ReservationResponse>(`${this.baseUrl}/reservations/${reservationId}/cancel`, null);
  }
}
