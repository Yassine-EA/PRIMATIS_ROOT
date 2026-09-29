import { Component, inject, input, output, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';

import { toAppError } from '../../../../core/errors/api-error.util';
import { StaffArticleApiService } from '../../../../articles/services/staff-article-api.service';

export interface ArticleImageValue {
  imageUrl: string | null;
  imageAlt: string;
}

const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_IMAGE_FILE_SIZE_BYTES = 5 * 1024 * 1024;

@Component({
  selector: 'app-article-image-field',
  imports: [ButtonModule, InputTextModule, MessageModule],
  templateUrl: './article-image-field.html',
  styleUrl: './article-image-field.scss',
})
export class ArticleImageField {
  private readonly staffArticleApiService = inject(StaffArticleApiService);

  readonly imageUrl = input<string | null>(null);
  readonly imageAlt = input<string | null>(null);
  readonly disabled = input(false);
  readonly altInvalid = input(false);

  readonly imageChange = output<ArticleImageValue>();

  readonly uploading = signal(false);
  readonly uploadError = signal<string | null>(null);

  chooseImage(fileInput: HTMLInputElement): void {
    if (this.disabled() || this.uploading()) return;
    this.uploadError.set(null);
    fileInput.click();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (!file) return;

    const validationError = this.validateFile(file);
    if (validationError) {
      this.uploadError.set(validationError);
      return;
    }

    this.uploadError.set(null);
    this.uploading.set(true);
    this.staffArticleApiService.uploadArticleMedia(file).subscribe({
      next: ({ url }) => {
        this.uploading.set(false);
        this.imageChange.emit({ imageUrl: url, imageAlt: '' });
      },
      error: (err: unknown) => {
        this.uploading.set(false);
        this.uploadError.set(toAppError(err).message);
      },
    });
  }

  updateAlt(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.imageChange.emit({ imageUrl: this.imageUrl(), imageAlt: value });
  }

  removeImage(): void {
    if (this.disabled() || this.uploading()) return;
    this.uploadError.set(null);
    this.imageChange.emit({ imageUrl: null, imageAlt: '' });
  }

  private validateFile(file: File): string | null {
    if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
      return 'Format non autorisé (JPEG, PNG ou WebP uniquement).';
    }
    if (file.size > MAX_IMAGE_FILE_SIZE_BYTES) {
      return 'Le fichier dépasse la taille maximale autorisée (5 Mio).';
    }
    return null;
  }
}
