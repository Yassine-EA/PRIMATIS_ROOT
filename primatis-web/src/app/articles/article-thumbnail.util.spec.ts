import { articleThumbnailAlt, articleThumbnailFallback, articleThumbnailSrc } from './article-thumbnail.util';

describe('article-thumbnail.util (HOME-ARTICLES-MEDIA)', () => {
  describe('articleThumbnailSrc', () => {
    it('should use the real imageUrl when the Article has one', () => {
      const article = { id: 1, imageUrl: '/media/articles/portrait.webp' };

      expect(articleThumbnailSrc(article, '960')).toBe('/media/articles/portrait.webp');
    });

    it('should preserve a relative /media/articles/... URL as-is, never rewritten', () => {
      const article = { id: 2, imageUrl: '/media/articles/2b7e1f.webp' };

      expect(articleThumbnailSrc(article, '640')).toBe('/media/articles/2b7e1f.webp');
    });

    it('should fall back to the decorative visual when the Article has no imageUrl', () => {
      const article = { id: 3, imageUrl: null };

      expect(articleThumbnailSrc(article, '960')).toBe(articleThumbnailFallback(article, '960'));
    });
  });

  describe('articleThumbnailFallback', () => {
    it('should be deterministic by Article id, never by list position', () => {
      const article = { id: 55 };

      const first = articleThumbnailFallback(article, '960');
      const second = articleThumbnailFallback(article, '960');

      expect(first).toBe(second);
    });

    it('should target the requested size variant', () => {
      const article = { id: 1 };

      expect(articleThumbnailFallback(article, '640')).toMatch(/-640\.webp$/);
      expect(articleThumbnailFallback(article, '960')).toMatch(/-960\.webp$/);
    });
  });

  describe('articleThumbnailAlt', () => {
    it('should build an informative alt for a real image', () => {
      const article = { title: 'Dostoïevski, une vie', imageUrl: '/media/articles/portrait.webp' };

      expect(articleThumbnailAlt(article)).toBe('Illustration de l’article « Dostoïevski, une vie »');
    });

    it('should return an empty (decorative) alt when there is no real image', () => {
      const article = { title: 'Dostoïevski, une vie', imageUrl: null };

      expect(articleThumbnailAlt(article)).toBe('');
    });
  });
});
