package be.primatis.catalogue;

import be.primatis.catalogue.dto.CreateTitleRequest;
import be.primatis.catalogue.dto.GenreResponse;
import be.primatis.catalogue.dto.TitleDetailResponse;
import be.primatis.catalogue.dto.TitleResponse;
import be.primatis.catalogue.dto.UpdateTitleRequest;
import be.primatis.catalogue.dto.UpdateTitleStatusRequest;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ConflictException;
import be.primatis.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service de consultation et de gestion des titres du catalogue.
 */
@Service
public class CatalogueService {

    private static final String TITLE_NOT_FOUND_CODE = "TITLE_NOT_FOUND";
    private static final String AUTHOR_NOT_FOUND_CODE = "AUTHOR_NOT_FOUND";
    private static final String GENRE_NOT_FOUND_CODE = "GENRE_NOT_FOUND";

    private final TitleRepository titleRepository;
    private final AuthorRepository authorRepository;
    private final GenreRepository genreRepository;
    private final TitleAuthorRepository titleAuthorRepository;
    private final TitleGenreRepository titleGenreRepository;
    private final CopyRepository copyRepository;
    private final Clock clock;

    public CatalogueService(
            TitleRepository titleRepository,
            AuthorRepository authorRepository,
            GenreRepository genreRepository,
            TitleAuthorRepository titleAuthorRepository,
            TitleGenreRepository titleGenreRepository,
            CopyRepository copyRepository,
            Clock clock) {
        this.titleRepository = titleRepository;
        this.authorRepository = authorRepository;
        this.genreRepository = genreRepository;
        this.titleAuthorRepository = titleAuthorRepository;
        this.titleGenreRepository = titleGenreRepository;
        this.copyRepository = copyRepository;
        this.clock = clock;
    }

    // Consultation publique

    /**
     * Recherche les titres actifs du catalogue selon les critères fournis.
     *
     * @param query     texte recherché
     * @param authorId  identifiant de l'auteur
     * @param genreCode code du genre
     * @param language  langue du titre
     * @param pageable  paramètres de pagination
     * @return page des titres correspondants
     */
    @Transactional(readOnly = true)
    public Page<TitleResponse> searchPublicTitles(
            String query,
            Long authorId,
            String genreCode,
            Language language,
            Pageable pageable) {

        var specification = TitleSpecifications.matching(
                query,
                authorId,
                genreCode,
                language,
                TitleStatus.ACTIVE);

        return toTitleResponses(
                titleRepository.findAll(specification, pageable));
    }

    /**
     * Retourne la liste complète des genres du catalogue public.
     *
     * @return liste des genres triés par libellé
     */
    @Transactional(readOnly = true)
    public List<GenreResponse> listPublicGenres() {
        return genreRepository
                .findAll(Sort.by(Sort.Direction.ASC, "label", "id"))
                .stream()
                .map(GenreResponse::from)
                .toList();
    }

    /**
     * Retourne le détail d'un titre actif du catalogue public.
     *
     * @param titleId identifiant du titre
     * @return détail du titre
     */
    @Transactional(readOnly = true)
    public TitleDetailResponse getPublicTitleById(Long titleId) {
        Title title = titleRepository.findById(titleId)
                .filter(candidate -> candidate.getTitleStatus() == TitleStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        TITLE_NOT_FOUND_CODE,
                        "Aucun titre disponible pour l'identifiant " + titleId + "."));

        List<Author> authors = authorRepository.findByTitleIdOrderByFullNameAscIdAsc(titleId);

        List<Genre> genres = genreRepository.findByTitleIdOrderByLabelAscIdAsc(titleId);

        return TitleDetailResponse.from(title, authors, genres);
    }

    // Consultation staff

    /**
     * Recherche les titres accessibles au personnel selon les critères fournis.
     *
     * @param query       texte recherché
     * @param authorId    identifiant de l'auteur
     * @param genreCode   code du genre
     * @param language    langue du titre
     * @param titleStatus statut du titre
     * @param pageable    paramètres de pagination
     * @return page des titres correspondants
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional(readOnly = true)
    public Page<TitleResponse> searchStaffTitles(
            String query,
            Long authorId,
            String genreCode,
            Language language,
            TitleStatus titleStatus,
            Pageable pageable) {

        var specification = TitleSpecifications.matching(
                query,
                authorId,
                genreCode,
                language,
                titleStatus);

        return toTitleResponses(
                titleRepository.findAll(specification, pageable));
    }

    /**
     * Retourne le détail d'un titre accessible au personnel.
     *
     * @param titleId identifiant du titre
     * @return détail du titre
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional(readOnly = true)
    public TitleDetailResponse getStaffTitleById(Long titleId) {
        Title title = getRequiredTitle(titleId);

        List<Author> authors = authorRepository.findByTitleIdOrderByFullNameAscIdAsc(titleId);

        List<Genre> genres = genreRepository.findByTitleIdOrderByLabelAscIdAsc(titleId);

        return TitleDetailResponse.from(title, authors, genres);
    }

    // Gestion staff

    /**
     * Crée un nouveau titre actif avec ses auteurs et ses genres.
     *
     * @param request données du titre
     * @return titre créé
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional
    public TitleDetailResponse createTitle(CreateTitleRequest request) {
        if (request.authorIds() == null || request.authorIds().isEmpty()) {
            throw new BusinessRuleException(
                    "AUTHOR_IDS_MUST_NOT_BE_EMPTY",
                    "authorIds ne peut pas être vide : un Title doit avoir au moins un Author.");
        }

        String normalizedIsbn = normalizeIsbn(request.isbn());

        if (normalizedIsbn != null
                && titleRepository.existsByIsbn(normalizedIsbn)) {
            throw new ConflictException(
                    "ISBN_ALREADY_EXISTS",
                    "Un titre existe déjà avec cet ISBN.");
        }

        List<Author> authors = resolveAuthors(request.authorIds());
        List<Genre> genres = resolveGenres(request.genreIds());

        Instant now = clock.instant();

        Title title = new Title();
        title.setIsbn(normalizedIsbn);
        title.setTitle(request.title());
        title.setSubtitle(request.subtitle());
        title.setSummary(request.summary());
        title.setPublicationYear(request.publicationYear());
        title.setLanguage(request.language());
        title.setPageCount(request.pageCount());
        title.setPublisher(request.publisher());
        title.setCoverImageUrl(request.coverImageUrl());
        title.setTitleStatus(TitleStatus.ACTIVE);
        title.setCreatedAt(now);
        title.setUpdatedAt(now);

        titleRepository.save(title);

        replaceAuthors(title, authors);
        replaceGenres(title, genres);

        return TitleDetailResponse.from(title, authors, genres);
    }

    /**
     * Modifie les informations d'un titre ainsi que ses associations.
     *
     * @param titleId identifiant du titre
     * @param request données à modifier
     * @return titre mis à jour
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional
    public TitleDetailResponse updateTitle(
            Long titleId,
            UpdateTitleRequest request) {

        Title title = getRequiredTitle(titleId);
        boolean mutated = false;

        if (request.isIsbnPresent()) {
            String normalizedIsbn = normalizeIsbn(request.getIsbn());

            if (normalizedIsbn != null
                    && !normalizedIsbn.equals(title.getIsbn())
                    && titleRepository.existsByIsbn(normalizedIsbn)) {

                throw new ConflictException(
                        "ISBN_ALREADY_EXISTS",
                        "Un titre existe déjà avec cet ISBN.");
            }

            title.setIsbn(normalizedIsbn);
            mutated = true;
        }

        if (request.isTitlePresent()) {
            if (request.getTitle() == null || request.getTitle().isBlank()) {
                throw new BusinessRuleException(
                        "TITLE_MUST_NOT_BE_BLANK",
                        "title ne peut pas être vide.");
            }

            title.setTitle(request.getTitle());
            mutated = true;
        }

        if (request.isSubtitlePresent()) {
            title.setSubtitle(request.getSubtitle());
            mutated = true;
        }

        if (request.isSummaryPresent()) {
            title.setSummary(request.getSummary());
            mutated = true;
        }

        if (request.isPublicationYearPresent()) {
            title.setPublicationYear(request.getPublicationYear());
            mutated = true;
        }

        if (request.isLanguagePresent()) {
            if (request.getLanguage() == null) {
                throw new BusinessRuleException(
                        "LANGUAGE_MUST_NOT_BE_NULL",
                        "language ne peut pas être effacé.");
            }

            title.setLanguage(request.getLanguage());
            mutated = true;
        }

        if (request.isPageCountPresent()) {
            if (request.getPageCount() != null
                    && request.getPageCount() <= 0) {

                throw new BusinessRuleException(
                        "PAGE_COUNT_MUST_BE_POSITIVE",
                        "pageCount doit être > 0.");
            }

            title.setPageCount(request.getPageCount());
            mutated = true;
        }

        if (request.isPublisherPresent()) {
            title.setPublisher(request.getPublisher());
            mutated = true;
        }

        if (request.isCoverImageUrlPresent()) {
            title.setCoverImageUrl(request.getCoverImageUrl());
            mutated = true;
        }

        List<Author> authors = null;

        if (request.getAuthorIds() != null) {
            if (request.getAuthorIds().isEmpty()) {
                throw new BusinessRuleException(
                        "AUTHOR_IDS_MUST_NOT_BE_EMPTY",
                        "authorIds ne peut pas être vide : un Title garde au moins un Author.");
            }

            authors = resolveAuthors(request.getAuthorIds());
            replaceAuthors(title, authors);
            mutated = true;
        }

        List<Genre> genres = null;

        if (request.getGenreIds() != null) {
            genres = resolveGenres(request.getGenreIds());
            replaceGenres(title, genres);
            mutated = true;
        }

        if (mutated) {
            title.setUpdatedAt(clock.instant());
        }

        List<Author> finalAuthors = authors != null
                ? authors
                : authorRepository.findByTitleIdOrderByFullNameAscIdAsc(titleId);

        List<Genre> finalGenres = genres != null
                ? genres
                : genreRepository.findByTitleIdOrderByLabelAscIdAsc(titleId);

        return TitleDetailResponse.from(
                title,
                finalAuthors,
                finalGenres);
    }

    /**
     * Modifie le statut d'un titre.
     *
     * @param titleId identifiant du titre
     * @param request nouveau statut
     * @return titre avec son statut mis à jour
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional
    public TitleDetailResponse updateTitleStatus(
            Long titleId,
            UpdateTitleStatusRequest request) {

        Title title = getRequiredTitle(titleId);

        if (title.getTitleStatus() != request.status()) {
            title.setTitleStatus(request.status());
            title.setUpdatedAt(clock.instant());
        }

        List<Author> authors = authorRepository.findByTitleIdOrderByFullNameAscIdAsc(titleId);

        List<Genre> genres = genreRepository.findByTitleIdOrderByLabelAscIdAsc(titleId);

        return TitleDetailResponse.from(title, authors, genres);
    }

    // Utilitaires privés

    private Page<TitleResponse> toTitleResponses(Page<Title> titles) {
        List<Long> ids = titles.getContent()
                .stream()
                .map(Title::getId)
                .toList();

        Map<Long, List<String>> authorNamesByTitle = new HashMap<>();
        Map<Long, Long> availableCopiesByTitle = new HashMap<>();

        if (!ids.isEmpty()) {
            for (var row : titleAuthorRepository.findAuthorNamesByTitleIds(ids)) {
                authorNamesByTitle
                        .computeIfAbsent(
                                row.getTitleId(),
                                key -> new ArrayList<>())
                        .add(row.getFullName());
            }

            for (var row : copyRepository.countCopiesByTitleIdsAndAvailabilityStatus(
                    ids,
                    AvailabilityStatus.AVAILABLE)) {

                availableCopiesByTitle.put(
                        row.getTitleId(),
                        row.getAvailableCopies());
            }
        }

        return titles.map(title -> TitleResponse.from(
                title,
                authorNamesByTitle.getOrDefault(
                        title.getId(),
                        List.of()),
                availableCopiesByTitle.getOrDefault(
                        title.getId(),
                        0L)));
    }

    private Title getRequiredTitle(Long titleId) {
        return titleRepository.findById(titleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        TITLE_NOT_FOUND_CODE,
                        "Aucun titre pour l'identifiant " + titleId + "."));
    }

    private List<Author> resolveAuthors(List<Long> authorIds) {
        List<Author> authors = new ArrayList<>();

        for (Long authorId : authorIds) {
            authors.add(
                    authorRepository.findById(authorId)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    AUTHOR_NOT_FOUND_CODE,
                                    "Aucun auteur pour l'identifiant " + authorId + ".")));
        }

        return authors;
    }

    private List<Genre> resolveGenres(List<Long> genreIds) {
        if (genreIds == null) {
            return List.of();
        }

        List<Genre> genres = new ArrayList<>();

        for (Long genreId : genreIds) {
            genres.add(
                    genreRepository.findById(genreId)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    GENRE_NOT_FOUND_CODE,
                                    "Aucun genre pour l'identifiant " + genreId + ".")));
        }

        return genres;
    }

    private static String normalizeIsbn(String isbn) {
        if (isbn == null) {
            return null;
        }

        String trimmed = isbn.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void replaceAuthors(
            Title title,
            List<Author> targetAuthors) {

        List<TitleAuthor> currentAssociations = titleAuthorRepository.findByIdTitleId(title.getId());

        Set<Long> currentAuthorIds = new HashSet<>();

        for (TitleAuthor association : currentAssociations) {
            currentAuthorIds.add(
                    association.getAuthor().getId());
        }

        Set<Long> targetAuthorIds = new HashSet<>();

        for (Author author : targetAuthors) {
            targetAuthorIds.add(author.getId());
        }

        for (TitleAuthor association : currentAssociations) {
            if (!targetAuthorIds.contains(
                    association.getAuthor().getId())) {

                titleAuthorRepository.delete(association);
            }
        }

        for (Author author : targetAuthors) {
            if (!currentAuthorIds.contains(author.getId())) {
                TitleAuthor newAssociation = new TitleAuthor();

                newAssociation.setId(
                        new TitleAuthorId(
                                title.getId(),
                                author.getId()));

                newAssociation.setTitle(title);
                newAssociation.setAuthor(author);

                titleAuthorRepository.save(newAssociation);
            }
        }
    }

    private void replaceGenres(
            Title title,
            List<Genre> targetGenres) {

        List<TitleGenre> currentAssociations = titleGenreRepository.findByIdTitleId(title.getId());

        Set<Long> currentGenreIds = new HashSet<>();

        for (TitleGenre association : currentAssociations) {
            currentGenreIds.add(
                    association.getGenre().getId());
        }

        Set<Long> targetGenreIds = new HashSet<>();

        for (Genre genre : targetGenres) {
            targetGenreIds.add(genre.getId());
        }

        for (TitleGenre association : currentAssociations) {
            if (!targetGenreIds.contains(
                    association.getGenre().getId())) {

                titleGenreRepository.delete(association);
            }
        }

        for (Genre genre : targetGenres) {
            if (!currentGenreIds.contains(genre.getId())) {
                TitleGenre newAssociation = new TitleGenre();

                newAssociation.setId(
                        new TitleGenreId(
                                genre.getId(),
                                title.getId()));

                newAssociation.setTitle(title);
                newAssociation.setGenre(genre);

                titleGenreRepository.save(newAssociation);
            }
        }
    }
}