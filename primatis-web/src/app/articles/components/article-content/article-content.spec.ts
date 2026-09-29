import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ArticleContent } from './article-content';

describe('ArticleContent', () => {
  let fixture: ComponentFixture<ArticleContent>;

  function render(html: string): HTMLElement {
    fixture = TestBed.createComponent(ArticleContent);
    fixture.componentRef.setInput('html', html);
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('.article-detail-content') as HTMLElement;
  }

  it('should render the provided HTML inside .article-detail-content', () => {
    const content = render('<h2>Titre</h2><p>Un <strong>paragraphe</strong>.</p>');

    expect(content.querySelector('h2')?.textContent).toBe('Titre');
    expect(content.querySelector('strong')?.textContent).toBe('paragraphe');
  });

  it('should keep Angular DOM sanitization active (no bypass)', () => {
    const content = render('<p>ok</p><script>window.__pwned = true;</script><img src="x" onerror="window.__pwned = true">');

    expect(content.querySelector('script')).toBeNull();
    expect(content.innerHTML).not.toContain('onerror');
    expect((window as unknown as { __pwned?: boolean }).__pwned).toBeUndefined();
  });
});
