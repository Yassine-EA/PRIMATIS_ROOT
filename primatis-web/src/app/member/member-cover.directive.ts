import { computed, Directive, input, signal } from '@angular/core';


export const MEMBER_COVER_FALLBACK = '/assets/fallbacks/cover-compact.svg';


@Directive({
  selector: 'img[primatisMemberCover]',
  host: {
    '[attr.src]': 'src()',
    '(error)': 'onError()',
  },
})
export class MemberCoverDirective {
  readonly coverImageUrl = input<string | null | undefined>(undefined, {
    alias: 'primatisMemberCover',
  });

  /** Dernière URL réelle en échec : un changement de valeur réessaie la nouvelle URL. */
  private readonly failedUrl = signal<string | null>(null);

  protected readonly src = computed(() => {
    const url = this.coverImageUrl()?.trim();
    return url && url !== this.failedUrl() ? url : MEMBER_COVER_FALLBACK;
  });

  protected onError(): void {
    const current = this.src();
    if (current !== MEMBER_COVER_FALLBACK) {
      this.failedUrl.set(current);
    }
  }
}
