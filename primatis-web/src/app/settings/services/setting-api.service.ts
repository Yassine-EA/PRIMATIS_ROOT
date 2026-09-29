import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { SettingResponse } from '../models/setting-response';
import { UpdateSettingValueRequest } from '../models/update-setting-value-request';


@Injectable({ providedIn: 'root' })
export class SettingApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  getSettings(): Observable<SettingResponse[]> {
    return this.http.get<SettingResponse[]>(`${this.baseUrl}/settings`);
  }

  updateSettingValue(settingKey: string, request: UpdateSettingValueRequest): Observable<SettingResponse> {
    return this.http.patch<SettingResponse>(
      `${this.baseUrl}/settings/${encodeURIComponent(settingKey)}`,
      request,
    );
  }
}
