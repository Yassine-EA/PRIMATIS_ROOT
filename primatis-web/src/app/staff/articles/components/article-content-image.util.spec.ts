import { composeArticleContent, splitArticleContent } from './article-content-image.util';

describe('article-content-image util', () => {
  it('should extract the single image from persisted content and keep the body text separate', () => {
    const parts = splitArticleContent(
      '<img src="/media/articles/a.webp" alt="Illustration"><h2>Titre</h2><p>Texte</p>',
    );

    expect(parts.imageUrl).toBe('/media/articles/a.webp');
    expect(parts.imageAlt).toBe('Illustration');
    expect(parts.bodyHtml).not.toContain('<img');
    expect(parts.bodyHtml).toContain('<h2>Titre</h2>');
  });

  it('should compose at most one image before the body', () => {
    const content = composeArticleContent(
      '<p>Texte</p><img src="/media/articles/old.webp" alt="Old">',
      '/media/articles/new.webp',
      'Nouvelle illustration',
    );

    expect(content.startsWith('<img')).toBe(true);
    expect(content.match(/<img/g)?.length).toBe(1);
    expect(content).toContain('/media/articles/new.webp');
    expect(content).toContain('<p>Texte</p>');
  });
});
