import { ArticleStatus } from './article-status';


export interface StaffArticleSearchFilters {
  q?: string;
  articleStatus?: ArticleStatus;
  modifiedDateFrom?: string;
  modifiedDateTo?: string;
  publishedDateFrom?: string;
  publishedDateTo?: string;
}
