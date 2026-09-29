import { Component, effect, inject, input, output, signal } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';

import { toAppError } from '../../../../core/errors/api-error.util';
import { FieldError } from '../../../../core/models/field-error';
import { SettingResponse } from '../../../../settings/models/setting-response';
import { UpdateSettingValueRequest } from '../../../../settings/models/update-setting-value-request';
import { SettingApiService } from '../../../../settings/services/setting-api.service';

const DECIMAL_GRAMMAR = /^([+-]?)(\d+(?:\.\d*)?|\.\d+)([eE][+-]?\d+)?$/;


function positiveIntegerValidator(control: AbstractControl): ValidationErrors | null {
  const raw = (control.value ?? '').trim();
  if (raw === '') {
    return null;
  }
  if (!/^[+-]?\d+$/.test(raw)) {
    return { notInteger: true };
  }
  return Number(raw) > 0 ? null : { notPositive: true };
}


function isPositiveDecimalLexically(raw: string): boolean {
  const match = DECIMAL_GRAMMAR.exec(raw);
  if (match === null) {
    return false;
  }
  const [, sign, significand] = match;
  if (sign === '-') {
    return false;
  }
  return /[1-9]/.test(significand);
}


function positiveDecimalValidator(control: AbstractControl): ValidationErrors | null {
  const raw = (control.value ?? '').trim();
  if (raw === '') {
    return null;
  }
  if (!DECIMAL_GRAMMAR.test(raw)) {
    return { notDecimal: true };
  }
  return isPositiveDecimalLexically(raw) ? null : { notPositive: true };
}


@Component({
  selector: 'app-setting-value-edit-dialog',
  imports: [ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, MessageModule],
  templateUrl: './setting-value-edit-dialog.html',
  styleUrl: './setting-value-edit-dialog.scss',
})
export class SettingValueEditDialog {
  private readonly settingApiService = inject(SettingApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly messageService = inject(MessageService);

  readonly visible = input.required<boolean>();
  readonly setting = input<SettingResponse | null>(null);

  readonly closed = output<void>();
  readonly saved = output<SettingResponse>();

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  private lastFieldErrors: readonly FieldError[] = [];

  readonly form = this.formBuilder.group({
    settingValue: this.formBuilder.control('', [Validators.required]),
  });

  constructor() {
    effect(() => {
      const current = this.setting();
      if (!this.visible() || current === null) {
        return;
      }
      this.form.reset({ settingValue: current.settingValue });
      this.form.controls.settingValue.clearValidators();
      this.form.controls.settingValue.addValidators(
        current.valueType === 'DECIMAL'
          ? [Validators.required, positiveDecimalValidator]
          : [Validators.required, positiveIntegerValidator],
      );
      this.form.controls.settingValue.updateValueAndValidity();
      this.errorMessage.set(null);
      this.lastFieldErrors = [];
    });
  }

  fieldError(field: string): string | undefined {
    return this.lastFieldErrors.find((fieldError) => fieldError.field === field)?.message;
  }

  cancel(): void {
    this.closed.emit();
  }

  submit(): void {
    const current = this.setting();
    if (this.submitting() || current === null) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.lastFieldErrors = [];

    const request: UpdateSettingValueRequest = { settingValue: this.form.getRawValue().settingValue.trim() };
    this.settingApiService.updateSettingValue(current.settingKey, request).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Paramètre modifié',
          detail: `${response.settingKey} a été mis à jour.`,
        });
        this.saved.emit(response);
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  private handleError(err: unknown): void {
    this.submitting.set(false);
    const appError = toAppError(err);
    this.errorMessage.set(appError.message);
    this.lastFieldErrors = appError.fieldErrors;
    this.messageService.add({ severity: 'error', summary: 'Erreur', detail: appError.message });
  }
}
