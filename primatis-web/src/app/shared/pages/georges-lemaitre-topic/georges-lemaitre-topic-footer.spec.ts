import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { GeorgesLemaitreTopicFooter } from './georges-lemaitre-topic-footer';

describe('GeorgesLemaitreTopicFooter', () => {
  let fixture: ComponentFixture<GeorgesLemaitreTopicFooter>;

  function render(topicKey: string): HTMLElement {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    fixture = TestBed.createComponent(GeorgesLemaitreTopicFooter);
    fixture.componentRef.setInput('sources', [
      { label: 'Source A', url: 'https://example.org/a' },
      { label: 'Source B', url: 'https://example.org/b' },
    ]);
    fixture.componentRef.setInput('topicKey', topicKey);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('should list every source as an external link opened safely', () => {
    const links = Array.from(render('parcours').querySelectorAll<HTMLAnchorElement>('.lemaitre-topic-sources a'));

    expect(links.map((a) => a.getAttribute('href'))).toEqual(['https://example.org/a', 'https://example.org/b']);
    expect(links.every((a) => a.target === '_blank' && a.rel.includes('noopener'))).toBe(true);
  });

  it('should propose the five other dossiers and never the current one', () => {
    const hrefs = Array.from(render('parcours').querySelectorAll<HTMLAnchorElement>('.lemaitre-topic-next a')).map((a) =>
      a.getAttribute('href'),
    );

    expect(hrefs).toHaveLength(5);
    expect(hrefs).not.toContain('/georges-lemaitre/parcours');
    expect(hrefs).toContain('/georges-lemaitre/distinctions');
  });
});
