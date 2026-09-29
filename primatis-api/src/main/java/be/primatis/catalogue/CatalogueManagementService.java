package be.primatis.catalogue;

import be.primatis.catalogue.dto.AuthorResponse;
import be.primatis.catalogue.dto.CreateAuthorRequest;
import be.primatis.catalogue.dto.CreateGenreRequest;
import be.primatis.catalogue.dto.GenreResponse;
import be.primatis.catalogue.dto.UpdateAuthorRequest;
import be.primatis.catalogue.dto.UpdateGenreRequest;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ConflictException;
import be.primatis.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Service de gestion des auteurs et des genres du catalogue.
 */
@Service
public class CatalogueManagementService {

    private static final String AUTHOR_NOT_FOUND_CODE = "AUTHOR_NOT_FOUND";
    private static final String GENRE_NOT_FOUND_CODE = "GENRE_NOT_FOUND";

    private final AuthorRepository authorRepository;
    private final GenreRepository genreRepository;

    public CatalogueManagementService(
            AuthorRepository authorRepository,
            GenreRepository genreRepository) {
        this.authorRepository = authorRepository;
        this.genreRepository = genreRepository;
    }

    // Auteurs

    /**
     * Recherche les auteurs selon leur nom.
     *
     * Une recherche vide retourne l'ensemble des auteurs sous forme paginée.
     *
     * @param query    texte recherché dans le nom de l'auteur
     * @param pageable paramètres de pagination
     * @return page des auteurs correspondants
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional(readOnly = true)
    public Page<AuthorResponse> searchAuthors(String query, Pageable pageable) {
        Page<Author> page = (query == null || query.isBlank())
                ? authorRepository.findAll(pageable)
                : authorRepository.findByFullNameContainingIgnoreCase(query, pageable);

        return page.map(AuthorResponse::from);
    }

    /**
     * Crée un nouvel auteur.
     *
     * @param request données de l'auteur
     * @return auteur créé
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional
    public AuthorResponse createAuthor(CreateAuthorRequest request) {
        requireCoherentDates(request.birthDate(), request.deathDate());

        Author author = new Author();
        author.setFullName(request.fullName());
        author.setBirthDate(request.birthDate());
        author.setDeathDate(request.deathDate());
        author.setNationality(request.nationality());
        author.setBiography(request.biography());

        authorRepository.save(author);

        return AuthorResponse.from(author);
    }

    /**
     * Modifie les informations d'un auteur.
     *
     * @param authorId identifiant de l'auteur
     * @param request  données à modifier
     * @return auteur mis à jour
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional
    public AuthorResponse updateAuthor(
            Long authorId,
            UpdateAuthorRequest request) {

        Author author = authorRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AUTHOR_NOT_FOUND_CODE,
                        "Aucun auteur pour l'identifiant " + authorId + "."));

        if (request.isFullNamePresent()) {
            if (request.getFullName() == null || request.getFullName().isBlank()) {
                throw new BusinessRuleException(
                        "AUTHOR_FULL_NAME_MUST_NOT_BE_BLANK",
                        "fullName ne peut pas être vide.");
            }

            author.setFullName(request.getFullName());
        }

        if (request.isBirthDatePresent()) {
            author.setBirthDate(request.getBirthDate());
        }

        if (request.isDeathDatePresent()) {
            author.setDeathDate(request.getDeathDate());
        }

        if (request.isNationalityPresent()) {
            author.setNationality(request.getNationality());
        }

        if (request.isBiographyPresent()) {
            author.setBiography(request.getBiography());
        }

        requireCoherentDates(
                author.getBirthDate(),
                author.getDeathDate());

        return AuthorResponse.from(author);
    }

    private static void requireCoherentDates(
            LocalDate birthDate,
            LocalDate deathDate) {

        if (birthDate != null
                && deathDate != null
                && birthDate.isAfter(deathDate)) {

            throw new BusinessRuleException(
                    "AUTHOR_BIRTH_DATE_AFTER_DEATH_DATE",
                    "birthDate ne peut pas être postérieure à deathDate.");
        }
    }

    // Genres

    /**
     * Retourne les genres sous forme paginée.
     *
     * @param pageable paramètres de pagination
     * @return page des genres
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional(readOnly = true)
    public Page<GenreResponse> listGenres(Pageable pageable) {
        return genreRepository.findAll(pageable)
                .map(GenreResponse::from);
    }

    /**
     * Crée un nouveau genre.
     *
     * @param request données du genre
     * @return genre créé
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional
    public GenreResponse createGenre(CreateGenreRequest request) {
        if (genreRepository.existsByCode(request.code())) {
            throw new ConflictException(
                    "GENRE_CODE_ALREADY_EXISTS",
                    "Un genre existe déjà avec ce code.");
        }

        if (genreRepository.existsByLabel(request.label())) {
            throw new ConflictException(
                    "GENRE_LABEL_ALREADY_EXISTS",
                    "Un genre existe déjà avec ce label.");
        }

        Genre genre = new Genre();
        genre.setCode(request.code());
        genre.setLabel(request.label());
        genre.setDescription(request.description());

        genreRepository.save(genre);

        return GenreResponse.from(genre);
    }

    /**
     * Modifie les informations d'un genre.
     *
     * @param genreId identifiant du genre
     * @param request données à modifier
     * @return genre mis à jour
     */
    @PreAuthorize("hasAuthority('CATALOGUE_MANAGE')")
    @Transactional
    public GenreResponse updateGenre(
            Long genreId,
            UpdateGenreRequest request) {

        Genre genre = genreRepository.findById(genreId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        GENRE_NOT_FOUND_CODE,
                        "Aucun genre pour l'identifiant " + genreId + "."));

        if (request.isCodePresent()) {
            if (request.getCode() == null || request.getCode().isBlank()) {
                throw new BusinessRuleException(
                        "GENRE_CODE_MUST_NOT_BE_BLANK",
                        "code ne peut pas être vide.");
            }

            if (!request.getCode().equals(genre.getCode())
                    && genreRepository.existsByCode(request.getCode())) {

                throw new ConflictException(
                        "GENRE_CODE_ALREADY_EXISTS",
                        "Un genre existe déjà avec ce code.");
            }

            genre.setCode(request.getCode());
        }

        if (request.isLabelPresent()) {
            if (request.getLabel() == null || request.getLabel().isBlank()) {
                throw new BusinessRuleException(
                        "GENRE_LABEL_MUST_NOT_BE_BLANK",
                        "label ne peut pas être vide.");
            }

            if (!request.getLabel().equals(genre.getLabel())
                    && genreRepository.existsByLabel(request.getLabel())) {

                throw new ConflictException(
                        "GENRE_LABEL_ALREADY_EXISTS",
                        "Un genre existe déjà avec ce label.");
            }

            genre.setLabel(request.getLabel());
        }

        if (request.isDescriptionPresent()) {
            genre.setDescription(request.getDescription());
        }

        return GenreResponse.from(genre);
    }
}