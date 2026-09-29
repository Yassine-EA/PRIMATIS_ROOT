import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api-base-url.token';
import { PageResponse } from '../../core/models/page-response';
import { ArticleMediaUploadResponse } from '../models/article-media-upload-response';
import { ArticleResponse } from '../models/article-response';
import { CreateArticleRequest } from '../models/create-article-request';
import { StaffArticleSearchFilters } from '../models/staff-article-search-filters';
import { StaffArticleSummaryResponse } from '../models/staff-article-summary-response';
import { UpdateArticleRequest } from '../models/update-article-request';
import { UpdateArticleTagsRequest } from '../models/update-article-tags-request';


@Injectable({ providedIn: 'root' })
export class StaffArticleApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  
  listStaffArticles(
    page = 0,
    size = 20,
    filters: StaffArticleSearchFilters = {},
  ): Observable<PageResponse<StaffArticleSummaryResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    const q = filters.q?.trim();
    if (q) {
      params = params.set('q', q);
    }
    for (const key of [
      'articleStatus',
      'modifiedDateFrom',
      'modifiedDateTo',
      'publishedDateFrom',
      'publishedDateTo',
    ] as const) {
      const value = filters[key];
      if (value) {
        params = params.set(key, value);
      }
    }
    return this.http.get<PageResponse<StaffArticleSummaryResponse>>(
      `${this.baseUrl}/staff/articles`,
      { params },
    );
  }

  getStaffArticleById(articleId: number): Observable<ArticleResponse> {
    return this.http.get<ArticleResponse>(`${this.baseUrl}/staff/articles/${articleId}`);
  }

  createArticle(request: CreateArticleRequest): Observable<ArticleResponse> {
    return this.http.post<ArticleResponse>(`${this.baseUrl}/staff/articles`, request);
  }

  updateArticle(articleId: number, request: UpdateArticleRequest): Observable<ArticleResponse> {
    return this.http.patch<ArticleResponse>(`${this.baseUrl}/staff/articles/${articleId}`, request);
  }

  publishArticle(articleId: number): Observable<ArticleResponse> {
    return this.http.post<ArticleResponse>(
      `${this.baseUrl}/staff/articles/${articleId}/publish`,
      null,
    );
  }

  archiveArticle(articleId: number): Observable<ArticleResponse> {
    return this.http.post<ArticleResponse>(
      `${this.baseUrl}/staff/articles/${articleId}/archive`,
      null,
    );
  }

  deleteArticle(articleId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/staff/articles/${articleId}`);
  }

  updateArticleTags(
    articleId: number,
    request: UpdateArticleTagsRequest,
  ): Observable<ArticleResponse> {
    return this.http.patch<ArticleResponse>(
      `${this.baseUrl}/staff/articles/${articleId}/tags`,
      request,
    );
  }

  
  uploadArticleMedia(file: File): Observable<ArticleMediaUploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ArticleMediaUploadResponse>(
      `${this.baseUrl}/staff/articles/media`,
      formData,
    );
  }
}
