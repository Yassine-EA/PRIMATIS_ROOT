import { ArticleSummaryResponse } from './models/article-summary-response';

export type ArticleThumbnailSize = '640' | '960';

const FALLBACK_VARIANTS = ['cosmos', 'library', 'observatory'] as const;

/**
 * Vignette réelle d'un Article (HOME-ARTICLES-MEDIA) : logique unique
 * partagée par `ArticleListPage` et `Home`, pour éviter toute divergence
 * entre `/articles` et la Home publique. `imageUrl` (calculée backend par
 * `ArticleThumbnailExtractor`, première `<img>` de `content`) prévaut
 * toujours ; jamais reparsée côté Angular.
 */
export function articleThumbnailSrc(
  article: Pick<ArticleSummaryResponse, 'id' | 'imageUrl'>,
  size: ArticleThumbnailSize,
): string {
  return article.imageUrl ?? articleThumbnailFallback(article, size);
}

/**
 * Fallback décoratif déterministe par Article (`id`), jamais par position
 * dans une liste : deux rendus de la même liste dans un ordre différent
 * (pagination, Home vs `/articles`) donnent toujours la même vignette pour
 * un même Article.
 */
export function articleThumbnailFallback(
  article: Pick<ArticleSummaryResponse, 'id'>,
  size: ArticleThumbnailSize,
): string {
  const index = Math.abs(article.id) % FALLBACK_VARIANTS.length;
  return `/assets/public/articles/cards/article-fallback-${FALLBACK_VARIANTS[index]}-${size}.webp`;
}

/**
 * Une image réelle est informative (alt cohérent, jamais masquée aux
 * technologies d'assistance) ; le fallback décoratif reste `alt=""`
 * (gabarit : `aria-hidden="true"` conditionnel sur l'absence d'`imageUrl`).
 */
export function articleThumbnailAlt(
  article: Pick<ArticleSummaryResponse, 'title' | 'imageUrl'>,
): string {
  return article.imageUrl ? `Illustration de l’article « ${article.title} »` : '';
}
