import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';

import { routes } from './app.routes';
import { provideApiConfiguration } from './core/api/api.providers';
import { authInterceptor } from './core/http/auth.interceptor';
import { PRIMENG_FR_TRANSLATION } from './core/i18n/primeng-fr-translation';
import { PrimatisPreset } from './core/theme/primatis-preset';


export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideApiConfiguration(),
    providePrimeNG({
      theme: {
        preset: PrimatisPreset,
        options: {
          darkModeSelector: false,
        },
      },
      ripple: true,
      translation: PRIMENG_FR_TRANSLATION,
    }),
    MessageService,
    ConfirmationService,
  ],
};
