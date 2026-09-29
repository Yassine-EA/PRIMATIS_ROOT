import { ArticleStatus } from './article-status';
import { ArticleUserResponse } from './article-user-response';
import { TagResponse } from './tag-response';


export interface ArticleResponse {
  id: number;
  title: string;
  content: string;
  summary: string | null;
  slug: string;
  articleStatus: ArticleStatus;
  author: ArticleUserResponse;
  lastModifiedBy: ArticleUserResponse | null;
  tags: TagResponse[];
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}
