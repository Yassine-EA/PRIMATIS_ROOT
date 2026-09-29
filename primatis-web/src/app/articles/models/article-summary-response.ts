import { ArticleUserResponse } from './article-user-response';


export interface ArticleSummaryResponse {
  id: number;
  title: string;
  summary: string | null;
  slug: string;
  author: ArticleUserResponse;
  publishedAt: string;
  imageUrl: string | null;
}
