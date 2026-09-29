export interface ArticleContentParts {
  bodyHtml: string;
  imageUrl: string | null;
  imageAlt: string;
}

/**
 * Le contrat persistant historique d'Article ne possède aucune colonne image :
 * l'illustration vit dans Article.content sous forme d'une unique balise <img>.
 * L'UI staff l'extrait simplement pour la présenter dans un champ dédié.
 */
export function splitArticleContent(content: string): ArticleContentParts {
  const document = new DOMParser().parseFromString(content, 'text/html');
  const images = Array.from(document.body.querySelectorAll('img'));
  const firstImage = images[0] ?? null;

  const imageUrl = firstImage?.getAttribute('src')?.trim() || null;
  const imageAlt = firstImage?.getAttribute('alt') ?? '';

  for (const image of images) {
    image.remove();
  }

  return {
    bodyHtml: document.body.innerHTML,
    imageUrl,
    imageAlt,
  };
}

/**
 * Recompose le payload Article.content attendu par le backend : au plus une image,
 * toujours placée en premier afin que le rendu public l'affiche sous le résumé,
 * avant le corps rédactionnel. Les éventuelles images résiduelles du body sont
 * retirées par défense en profondeur ; le backend revalide aussi la cardinalité.
 */
export function composeArticleContent(
  bodyHtml: string,
  imageUrl: string | null,
  imageAlt: string,
): string {
  const document = new DOMParser().parseFromString(bodyHtml, 'text/html');
  for (const image of Array.from(document.body.querySelectorAll('img'))) {
    image.remove();
  }

  if (imageUrl) {
    const image = document.createElement('img');
    image.setAttribute('src', imageUrl);
    image.setAttribute('alt', imageAlt.trim());
    document.body.insertBefore(image, document.body.firstChild);
  }

  return document.body.innerHTML;
}
