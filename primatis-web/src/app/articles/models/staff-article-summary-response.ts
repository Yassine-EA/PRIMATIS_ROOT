import { ArticleStatus } from './article-status';
import { ArticleUserResponse } from './article-user-response';


export interface StaffArticleSummaryResponse {
  id: number;
  title: string;
  summary: string | null;
  slug: string;
  articleStatus: ArticleStatus;
  author: ArticleUserResponse;
  publishedAt: string | null;
  updatedAt: string;
}
