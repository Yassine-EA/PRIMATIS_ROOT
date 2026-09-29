import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AuthService } from '../../auth/services/auth.service';
import { API_BASE_URL } from '../api/api-base-url.token';
import { ApiErrorResponse } from '../models/api-error-response';
import { isPrimatisApiUrl } from './is-primatis-api-url';


const SESSION_INVALIDATING_CODES = new Set(['AUTHENTICATION_REQUIRED', 'INVALID_TOKEN']);


export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const apiBaseUrl = inject(API_BASE_URL);

  const targetsPrimatisApi = isPrimatisApiUrl(req.url, apiBaseUrl);

  if (targetsPrimatisApi && authService.token() !== null && authService.isSessionExpired()) {
    authService.logout();
  }

  const token = authService.token();
  const authorizedReq =
    targetsPrimatisApi && token !== null
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(authorizedReq).pipe(
    catchError((error: unknown) => {
      if (targetsPrimatisApi && error instanceof HttpErrorResponse && error.status === 401) {
        invalidateSessionIfBackendSaysSo(authService, error);
      }
      return throwError(() => error);
    }),
  );
};

function invalidateSessionIfBackendSaysSo(authService: AuthService, error: HttpErrorResponse): void {
  const body = error.error as ApiErrorResponse | null;
  const code = body?.code;
  if (code && SESSION_INVALIDATING_CODES.has(code)) {
    authService.logout();
  }
}
