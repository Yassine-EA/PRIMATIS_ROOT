import { Component, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';

import { AuthService } from '../../../../auth/services/auth.service';
import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { SettingResponse } from '../../../../settings/models/setting-response';
import { SettingApiService } from '../../../../settings/services/setting-api.service';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { SettingValueEditDialog } from '../../components/setting-value-edit-dialog/setting-value-edit-dialog';


@Component({
  selector: 'app-admin-settings-page',
  imports: [TableModule, ButtonModule, LoadingState, EmptyState, ErrorState, SettingValueEditDialog],
  templateUrl: './admin-settings-page.html',
  styleUrl: './admin-settings-page.scss',
})
export class AdminSettingsPage {
  private readonly settingApiService = inject(SettingApiService);
  private readonly authService = inject(AuthService);
  private readonly titleService = inject(Title);

  readonly rows = signal<SettingResponse[]>([]);
  // Initialisé à true : le premier chargement est déclenché explicitement
  // ci-dessous (constructeur) — même principe que StaffFinesPage/AdminUsersPage.
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);
  readonly editingSetting = signal<SettingResponse | null>(null);

  constructor() {
    this.titleService.setTitle("Paramètres de l'application — PRIMATIS");
    this.load();
  }

  get canManageSettings(): boolean {
    return this.authService.hasPermission('SETTING_MANAGE');
  }

  retry(): void {
    this.load();
  }

  edit(setting: SettingResponse): void {
    if (!this.canManageSettings) {
      return;
    }
    this.editingSetting.set(setting);
  }

  closeDialog(): void {
    this.editingSetting.set(null);
  }

  onSaved(updated: SettingResponse): void {
    this.rows.set(this.rows().map((row) => (row.settingKey === updated.settingKey ? updated : row)));
    this.editingSetting.set(null);
  }

  
  auditLabel(setting: SettingResponse): string {
    if (setting.updatedByUser === null) {
      return 'Jamais modifié';
    }
    return `${setting.updatedAt} — ${setting.updatedByUser.firstName} ${setting.updatedByUser.lastName}`;
  }

  
  keySegments(settingKey: string): string[] {
    return settingKey.split('_');
  }

  valueTypeLabel(setting: SettingResponse): string {
    switch (setting.valueType) {
      case 'INTEGER':
        return 'Entier';
      case 'DECIMAL':
        return 'Décimal';
    }
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);

    this.settingApiService.getSettings().subscribe({
      next: (response) => {
        this.rows.set(response);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(toAppError(err));
      },
    });
  }
}
