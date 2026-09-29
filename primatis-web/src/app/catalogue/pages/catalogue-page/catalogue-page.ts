import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { InputTextModule } from 'primeng/inputtext';
import { PaginatorModule } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { PaginatorState } from 'primeng/types/paginator';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { AppError } from '../../../core/errors/api-error';
import { toAppError } from '../../../core/errors/api-error.util';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { LoadingState } from '../../../shared/ui/loading-state/loading-state';
import { GenreResponse } from '../../models/genre-response';
import { Language } from '../../models/language';
import { TitleResponse } from '../../models/title-response';
import { TitleSearchParams } from '../../models/title-search-params';
import { CatalogueApiService } from '../../services/catalogue-api.service';

const DEFAULT_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

interface LanguageOption {
  readonly label: string;
  readonly value: Language | null;
}

interface GenreOption {
  readonly label: string;
  readonly value: string | null;
}


export type CatalogueViewMode = 'grid' | 'list';

interface PageSizeOption {
  readonly label: string;
  readonly value: number;
}

/** `GET /api/v1/titles` accepte déjà `size` (1..100, défaut 20, `TitleController`) — aucune évolution backend nécessaire. */
const PAGE_SIZE_OPTIONS: number[] = [20, 40, 60];

const LANGUAGE_OPTIONS: LanguageOption[] = [
  { label: 'Toutes les langues', value: null },
  { label: 'Français', value: 'FR' },
  { label: 'Anglais', value: 'EN' },
  { label: 'Néerlandais', value: 'NL' },
  { label: 'Allemand', value: 'DE' },
  { label: 'Espagnol', value: 'ES' },
  { label: 'Italien', value: 'IT' },
  { label: 'Latin', value: 'LA' },
];

const ALL_GENRES_OPTION: GenreOption = { label: 'Tous les genres', value: null };

const PAGE_SIZE_SELECT_OPTIONS: PageSizeOption[] = PAGE_SIZE_OPTIONS.map((size) => ({
  label: `${size} par page`,
  value: size,
}));

/** `null` si absent ou si la valeur ne correspond à aucun `Language` connu (jamais transmis tel quel au backend). */
function parseLanguageParam(raw: string | null): Language | null {
  if (!raw) {
    return null;
  }
  const match = LANGUAGE_OPTIONS.find((option) => option.value === raw);
  return match ? (match.value as Language) : null;
}

function parseGenreCodeParam(raw: string | null): string | null {
  const trimmed = raw?.trim();
  return trimmed && trimmed.length > 0 ? trimmed : null;
}

/** `'list'` seulement si littéralement `?view=list` — toute autre valeur (absente, invalide) retombe sur `'grid'` (défaut). */
function parseViewParam(raw: string | null): CatalogueViewMode {
  return raw === 'list' ? 'list' : 'grid';
}

/** `DEFAULT_PAGE_SIZE` si absent ou si la valeur ne fait pas partie des 3 tailles autorisées par le contrôle UI (jamais une taille arbitraire transmise au backend). */
function parseSizeParam(raw: string | null): number {
  if (!raw) {
    return DEFAULT_PAGE_SIZE;
  }
  const parsed = Number(raw);
  return PAGE_SIZE_OPTIONS.includes(parsed) ? parsed : DEFAULT_PAGE_SIZE;
}


@Component({
  selector: 'app-catalogue-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    InputTextModule,
    SelectModule,
    PaginatorModule,
    LoadingState,
    EmptyState,
    ErrorState,
  ],
  templateUrl: './catalogue-page.html',
  styleUrl: './catalogue-page.scss',
})
export class CataloguePage {
  private readonly catalogueApiService = inject(CatalogueApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly titleService = inject(Title);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly languageOptions = LANGUAGE_OPTIONS;
  readonly genreOptions = signal<GenreOption[]>([ALL_GENRES_OPTION]);
  readonly pageSizeOptions = PAGE_SIZE_SELECT_OPTIONS;

  private readonly initialQuery = this.route.snapshot.queryParamMap.get('q')?.trim() ?? '';
  private readonly initialLanguage = parseLanguageParam(
    this.route.snapshot.queryParamMap.get('language'),
  );
  private readonly initialGenreCode = parseGenreCodeParam(
    this.route.snapshot.queryParamMap.get('genreCode'),
  );
  private readonly initialView = parseViewParam(this.route.snapshot.queryParamMap.get('view'));
  private readonly initialSize = parseSizeParam(this.route.snapshot.queryParamMap.get('size'));

  
  readonly viewMode = signal<CatalogueViewMode>(this.initialView);

  
  readonly pageSizeControl = this.formBuilder.control<number>(this.initialSize);

  readonly filtersForm = this.formBuilder.group({
    q: this.formBuilder.control(this.initialQuery),
    language: this.formBuilder.control<Language | null>(this.initialLanguage),
    genreCode: this.formBuilder.control<string | null>(this.initialGenreCode),
  });

  // `FormControl.value` n'est pas un Signal : le lire directement dans le
  // template (ex. affichage conditionnel du bouton "Effacer") provoquerait
  // un NG0100 (ExpressionChangedAfterItHasBeenCheckedError) dès que la
  // valeur change sans passer par un nouveau cycle de détection déclenché
  // par un Signal. `toSignal` republie `valueChanges` comme un Signal réel.
  readonly searchTerm = toSignal(this.filtersForm.controls.q.valueChanges, {
    initialValue: this.filtersForm.controls.q.value,
  });

  readonly rows = signal<TitleResponse[]>([]);
  readonly totalRecords = signal(0);
  // Initialisé à true : le premier chargement est déclenché explicitement
  
  readonly loading = signal(true);
  readonly error = signal<AppError | null>(null);

  private lastPage = 0;

  constructor() {
    this.titleService.setTitle('Catalogue — PRIMATIS');

    this.catalogueApiService.listGenres().subscribe((genres: GenreResponse[]) => {
      this.genreOptions.set([
        ALL_GENRES_OPTION,
        ...genres.map((genre) => ({ label: genre.label, value: genre.code })),
      ]);
    });

    // Recherche texte débouncée : évite un appel HTTP à chaque frappe.
    // Un changement de langue/genre reste un choix discret (pas de saisie
    // continue) — pas de debounce nécessaire pour ces contrôles.
    this.filtersForm.controls.q.valueChanges
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        this.load(0, this.pageSizeControl.value);
        this.syncUrlFromState();
      });

    this.filtersForm.controls.language.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.load(0, this.pageSizeControl.value);
      this.syncUrlFromState();
    });

    this.filtersForm.controls.genreCode.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.load(0, this.pageSizeControl.value);
      this.syncUrlFromState();
    });

    // Choix discret (comme Langue/Genre) : toujours une des 3 valeurs de
    // `pageSizeOptions`, jamais un debounce. Remet systématiquement la
    
    this.pageSizeControl.valueChanges.pipe(takeUntilDestroyed()).subscribe((size) => {
      this.load(0, size);
      this.syncUrlFromState();
    });

    // Applique une valeur externe (navigation directe, lien entrant) pour
    // chacun des cinq paramètres : ne réagit jamais à notre propre écriture
    // (`syncUrlFromState` ci-dessus), qui produit toujours une URL déjà
    // égale à l'état courant au moment où elle est émise ici.
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const urlQuery = (params.get('q') ?? '').trim();
      if (urlQuery !== this.filtersForm.controls.q.value.trim()) {
        this.filtersForm.controls.q.setValue(urlQuery);
      }

      const urlLanguage = parseLanguageParam(params.get('language'));
      if (urlLanguage !== this.filtersForm.controls.language.value) {
        this.filtersForm.controls.language.setValue(urlLanguage);
      }

      const urlGenreCode = parseGenreCodeParam(params.get('genreCode'));
      if (urlGenreCode !== this.filtersForm.controls.genreCode.value) {
        this.filtersForm.controls.genreCode.setValue(urlGenreCode);
      }

      // `viewMode` n'est pas un FormControl : l'affecter directement ne
      // déclenche aucune émission en cascade (pas de risque de boucle).
      const urlView = parseViewParam(params.get('view'));
      if (urlView !== this.viewMode()) {
        this.viewMode.set(urlView);
      }

      const urlSize = parseSizeParam(params.get('size'));
      if (urlSize !== this.pageSizeControl.value) {
        this.pageSizeControl.setValue(urlSize);
      }
    });

    
    // jamais `DEFAULT_PAGE_SIZE` en dur, qui ignorerait une taille valide
    // demandée par une URL directe.
    this.load(0, this.pageSizeControl.value);
  }

  /** Déclenche une recherche immédiate (soumission du formulaire), sans attendre le debounce. */
  onSubmit(): void {
    this.load(0, this.pageSizeControl.value);
    this.syncUrlFromState();
  }

  clearSearch(): void {
    this.filtersForm.controls.q.setValue('');
  }

  /** Aucun rechargement réseau : seul l'état d'affichage change (cf. Javadoc de la classe). */
  setViewMode(mode: CatalogueViewMode): void {
    this.viewMode.set(mode);
    this.syncUrlFromState();
  }

  /** `viewMode`/`pageSizeControl` hors `filtersForm` : jamais réinitialisés ici (cf. Javadoc de la classe). */
  resetFilters(): void {
    this.filtersForm.reset({ q: '', language: null, genreCode: null });
  }

  hasActiveFilters(): boolean {
    const raw = this.filtersForm.getRawValue();
    return raw.q.trim().length > 0 || raw.language !== null || raw.genreCode !== null;
  }

  onPageChange(event: PaginatorState): void {
    const rows = event.rows ?? this.pageSizeControl.value;
    const first = event.first ?? 0;
    this.load(Math.floor(first / rows), rows);
  }

  retry(): void {
    this.load(this.lastPage, this.pageSizeControl.value);
  }

  /** Position (0-based) du premier résultat de la page courante — reflète toujours la dernière page réellement chargée. */
  currentFirst(): number {
    return this.lastPage * this.pageSizeControl.value;
  }

  currentRows(): number {
    return this.pageSizeControl.value;
  }

  
  authorsLabel(row: TitleResponse): string {
    const names = row.authorNames;
    if (names.length <= 2) {
      return names.join(', ');
    }
    const others = names.length - 2;
    return `${names.slice(0, 2).join(', ')} et ${others} autre${others > 1 ? 's' : ''}`;
  }

  /** `availableCopies` est calculé par le backend : Disponible ssi > 0. */
  availabilityLabel(row: TitleResponse): string {
    return row.availableCopies > 0 ? 'Disponible' : 'Indisponible';
  }

  /** Nom accessible du lien : titre, auteurs et disponibilité (l'`aria-label` remplace le contenu). */
  rowLabel(row: TitleResponse): string {
    const authors = this.authorsLabel(row);
    return `Voir le détail de ${row.title}${authors ? ', ' + authors : ''} — ${this.availabilityLabel(row)}`;
  }

  languageLabel(language: Language): string {
    return this.languageOptions.find((option) => option.value === language)?.label ?? language;
  }

  /** Envoie l'état complet (3 filtres + vue + taille) en un seul `navigate()` — jamais un par champ, cf. Javadoc de la classe. */
  private syncUrlFromState(): void {
    const raw = this.filtersForm.getRawValue();
    const trimmedQ = raw.q.trim();
    const view = this.viewMode();
    const size = this.pageSizeControl.value;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        q: trimmedQ.length > 0 ? trimmedQ : null,
        language: raw.language,
        genreCode: raw.genreCode,
        // Défauts jamais écrits dans l'URL (`null` ⇒ paramètre retiré par le Router).
        view: view === 'list' ? 'list' : null,
        size: size !== DEFAULT_PAGE_SIZE ? size : null,
      },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private load(page: number, size: number): void {
    this.lastPage = page;
    this.loading.set(true);
    this.error.set(null);

    const raw = this.filtersForm.getRawValue();
    const params: TitleSearchParams = { page, size };
    const trimmedQ = raw.q.trim();
    if (trimmedQ.length > 0) {
      params.q = trimmedQ;
    }
    if (raw.language !== null) {
      params.language = raw.language;
    }
    if (raw.genreCode !== null) {
      params.genreCode = raw.genreCode;
    }

    this.catalogueApiService.searchTitles(params).subscribe({
      next: (response) => {
        this.rows.set(response.content);
        this.totalRecords.set(response.totalElements);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(toAppError(err));
      },
    });
  }
}
