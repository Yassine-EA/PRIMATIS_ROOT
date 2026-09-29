import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { MEMBER_COVER_FALLBACK, MemberCoverDirective } from './member-cover.directive';

@Component({
  imports: [MemberCoverDirective],
  template: `<img [primatisMemberCover]="cover()" alt="" />`,
})
class HostComponent {
  readonly cover = signal<string | null | undefined>(null);
}

describe('MemberCoverDirective (DEV-16.7 FIX-02)', () => {
  function render(cover: string | null | undefined) {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.cover.set(cover);
    fixture.detectChanges();
    const img = (fixture.nativeElement as HTMLElement).querySelector('img') as HTMLImageElement;
    return { fixture, img };
  }

  it('should use the real cover URL as-is when coverImageUrl is present', () => {
    const { img } = render('/covers/catalogue/9781401937201.jpg');

    expect(img.getAttribute('src')).toBe('/covers/catalogue/9781401937201.jpg');
  });

  it.each([null, undefined, '', '   '])(
    'should use the fallback when coverImageUrl is %j',
    (value) => {
      const { img } = render(value);

      expect(img.getAttribute('src')).toBe(MEMBER_COVER_FALLBACK);
      expect(img.getAttribute('src')).toBe('/assets/fallbacks/cover-compact.svg');
    },
  );

  it('should fall back once when the real cover fails to load', () => {
    const { fixture, img } = render('/covers/catalogue/missing.jpg');

    img.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(img.getAttribute('src')).toBe(MEMBER_COVER_FALLBACK);
  });

  it('should not loop when the fallback itself fails to load', () => {
    const { fixture, img } = render('/covers/catalogue/missing.jpg');

    img.dispatchEvent(new Event('error'));
    fixture.detectChanges();
    img.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(img.getAttribute('src')).toBe(MEMBER_COVER_FALLBACK);
  });

  it('should try a new real URL after a previous one failed', () => {
    const { fixture, img } = render('/covers/catalogue/missing.jpg');
    img.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    fixture.componentInstance.cover.set('/covers/catalogue/other.jpg');
    fixture.detectChanges();

    expect(img.getAttribute('src')).toBe('/covers/catalogue/other.jpg');
  });
});
