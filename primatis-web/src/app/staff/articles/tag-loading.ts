import { Observable, forkJoin, map, of, switchMap } from 'rxjs';

import { TagResponse } from '../../articles/models/tag-response';
import { StaffTagApiService } from '../../articles/services/staff-tag-api.service';

const TAG_PAGE_SIZE = 100;


export function loadAllTags(staffTagApiService: StaffTagApiService): Observable<TagResponse[]> {
  return staffTagApiService.listTags(0, TAG_PAGE_SIZE).pipe(
    switchMap((firstPage) => {
      if (firstPage.totalPages <= 1) {
        return of(firstPage.content);
      }
      const remainingPageRequests = Array.from({ length: firstPage.totalPages - 1 }, (_, index) =>
        staffTagApiService.listTags(index + 1, TAG_PAGE_SIZE),
      );
      return forkJoin(remainingPageRequests).pipe(
        map((remainingPages) => [...firstPage.content, ...remainingPages.flatMap((page) => page.content)]),
      );
    }),
  );
}
