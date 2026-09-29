import { Observable, forkJoin, map, of, switchMap } from 'rxjs';

import { GenreResponse } from '../../catalogue/models/genre-response';
import { StaffCatalogueApiService } from '../../catalogue/services/staff-catalogue-api.service';

const GENRE_PAGE_SIZE = 100;


export function loadAllGenres(staffCatalogueApiService: StaffCatalogueApiService): Observable<GenreResponse[]> {
  return staffCatalogueApiService.searchGenres({ page: 0, size: GENRE_PAGE_SIZE }).pipe(
    switchMap((firstPage) => {
      if (firstPage.totalPages <= 1) {
        return of(firstPage.content);
      }
      const remainingPageRequests = Array.from({ length: firstPage.totalPages - 1 }, (_, index) =>
        staffCatalogueApiService.searchGenres({ page: index + 1, size: GENRE_PAGE_SIZE }),
      );
      return forkJoin(remainingPageRequests).pipe(
        map((remainingPages) => [...firstPage.content, ...remainingPages.flatMap((page) => page.content)]),
      );
    }),
  );
}
