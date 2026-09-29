import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { PageResponse } from '../../core/models/page-response';
import { ArticleResponse } from '../models/article-response';
import { ArticleSummaryResponse } from '../models/article-summary-response';


@Injectable({ providedIn: 'root' })
export class ArticleApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  listPublishedArticles(page = 0, size = 20): Observable<PageResponse<ArticleSummaryResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<ArticleSummaryResponse>>(`${this.baseUrl}/articles`, { params });
  }

  getPublishedArticleBySlug(slug: string): Observable<ArticleResponse> {
    return this.http.get<ArticleResponse>(`${this.baseUrl}/articles/${encodeURIComponent(slug)}`);
  }
}
