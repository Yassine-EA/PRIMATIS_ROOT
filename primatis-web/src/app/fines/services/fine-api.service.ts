import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { PageResponse } from '../../core/models/page-response';
import { FineResponse } from '../models/fine-response';
import { FineSearchFilters } from '../models/fine-search-filters';


@Injectable({ providedIn: 'root' })
export class FineApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  listOwnFines(page = 0, size = 20): Observable<PageResponse<FineResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<FineResponse>>(`${this.baseUrl}/me/fines`, { params });
  }

  
  listFines(page = 0, size = 20, filters: FineSearchFilters = {}): Observable<PageResponse<FineResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    const q = filters.q?.trim();
    if (q) {
      params = params.set('q', q);
    }
    for (const key of ['fineStatus', 'issuedDateFrom', 'issuedDateTo', 'closedDateFrom', 'closedDateTo'] as const) {
      const value = filters[key];
      if (value) {
        params = params.set(key, value);
      }
    }
    return this.http.get<PageResponse<FineResponse>>(`${this.baseUrl}/fines`, { params });
  }

  confirmPayment(fineId: number): Observable<FineResponse> {
    return this.http.post<FineResponse>(`${this.baseUrl}/fines/${fineId}/payment-confirmation`, null);
  }

  cancelFine(fineId: number): Observable<FineResponse> {
    return this.http.post<FineResponse>(`${this.baseUrl}/fines/${fineId}/cancel`, null);
  }
}
