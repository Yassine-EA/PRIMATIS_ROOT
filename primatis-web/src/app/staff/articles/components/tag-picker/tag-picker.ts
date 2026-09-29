import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MultiSelectModule } from 'primeng/multiselect';

import { AppError } from '../../../../core/errors/api-error';
import { toAppError } from '../../../../core/errors/api-error.util';
import { ErrorState } from '../../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../../shared/ui/loading-state/loading-state';
import { TagResponse } from '../../../../articles/models/tag-response';
import { StaffTagApiService } from '../../../../articles/services/staff-tag-api.service';
import { loadAllTags } from '../../tag-loading';


@Component({
  selector: 'app-tag-picker',
  imports: [FormsModule, MultiSelectModule, LoadingState, ErrorState],
  templateUrl: './tag-picker.html',
  styleUrl: './tag-picker.scss',
})
export class TagPicker implements OnInit {
  private readonly staffTagApiService = inject(StaffTagApiService);

  readonly initialTags = input<TagResponse[]>([]);
  readonly disabled = input(false);
  readonly selectionChange = output<TagResponse[]>();

  readonly allTags = signal<TagResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);
  readonly selectedIds = signal<number[]>([]);

  /**
   * Initialisation à un seul coup depuis `initialTags` — jamais dans le
   * constructeur (inputs signal garantis résolus à partir de `ngOnInit`
   * seulement), même précédent que `GenrePicker.ngOnInit`.
   */
  ngOnInit(): void {
    this.selectedIds.set(this.initialTags().map((tag) => tag.id));
    this.loadTags();
  }

  onSelectionChange(ids: number[]): void {
    this.selectedIds.set(ids);
    this.emitSelection();
  }

  retry(): void {
    this.loadTags();
  }

  private emitSelection(): void {
    const ids = this.selectedIds();
    this.selectionChange.emit(this.allTags().filter((tag) => ids.includes(tag.id)));
  }

  private loadTags(): void {
    this.loading.set(true);
    this.error.set(null);
    loadAllTags(this.staffTagApiService).subscribe({
      next: (tags) => {
        this.allTags.set(tags);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(toAppError(err));
      },
    });
  }
}
