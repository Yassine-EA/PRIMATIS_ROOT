import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';

import { isApiErrorResponse } from '../../../core/errors/api-error.util';
import { AuthService } from '../../services/auth.service';

const ERROR_MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'Adresse e-mail ou mot de passe incorrect.',
  ACCOUNT_TEMPORARILY_LOCKED:
    'Compte temporairement verrouillé suite à plusieurs échecs de connexion. Réessayez plus tard.',
};

const GENERIC_ERROR_MESSAGE = 'Une erreur est survenue. Veuillez réessayer.';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, PasswordModule, MessageModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly form = this.formBuilder.group({
    email: this.formBuilder.control('', [Validators.required, Validators.email]),
    password: this.formBuilder.control('', [Validators.required]),
  });

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  submit(): void {
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const { email, password } = this.form.getRawValue();

    this.authService.login(email, password).subscribe({
      next: () => {
        this.submitting.set(false);
        const returnUrl = sanitizeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
        void this.router.navigateByUrl(returnUrl);
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.errorMessage.set(resolveErrorMessage(error));
      },
    });
  }
}


function resolveErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse && isApiErrorResponse(error.error)) {
    const code = error.error.code;
    if (code in ERROR_MESSAGES) {
      return ERROR_MESSAGES[code];
    }
  }
  return GENERIC_ERROR_MESSAGE;
}


function sanitizeReturnUrl(rawReturnUrl: string | null): string {
  if (rawReturnUrl && rawReturnUrl.startsWith('/') && !rawReturnUrl.startsWith('//')) {
    return rawReturnUrl;
  }
  return '/';
}
