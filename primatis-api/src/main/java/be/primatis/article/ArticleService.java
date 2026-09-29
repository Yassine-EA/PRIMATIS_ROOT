package be.primatis.article;

import be.primatis.article.dto.ArticleResponse;
import be.primatis.article.dto.ArticleSummaryResponse;
import be.primatis.article.dto.CreateArticleRequest;
import be.primatis.article.dto.StaffArticleSummaryResponse;
import be.primatis.article.dto.UpdateArticleRequest;
import be.primatis.article.dto.UpdateArticleTagsRequest;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.InvalidDateRangeException;
import be.primatis.exception.ResourceNotFoundException;
import be.primatis.notification.NotificationService;
import be.primatis.notification.NotificationType;
import be.primatis.user.AppUser;
import be.primatis.user.AppUserRepository;
import be.primatis.user.MemberStatus;
import org.hibernate.exception.ConstraintViolationException;
import org.jsoup.Jsoup;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Service de consultation et de gestion des articles.
 */
@Service
public class ArticleService {

    // Tri des articles du plus récemment modifié au plus ancien.
    private static final Sort STAFF_LIST_SORT = Sort.by(Sort.Direction.DESC, "updatedAt", "id");
    private static final String ARTICLE_NOT_FOUND_CODE = "ARTICLE_NOT_FOUND";
    private static final String USER_NOT_FOUND_CODE = "USER_NOT_FOUND";
    private static final String ARTICLE_CONTENT_EMPTY_CODE = "ARTICLE_CONTENT_EMPTY";
    private static final String ARTICLE_TITLE_PRODUCES_EMPTY_SLUG_CODE = "ARTICLE_TITLE_PRODUCES_EMPTY_SLUG";
    private static final String ARTICLE_SLUG_ALREADY_EXISTS_CODE = "ARTICLE_SLUG_ALREADY_EXISTS";
    private static final String ARTICLE_NOT_EDITABLE_CODE = "ARTICLE_NOT_EDITABLE";
    private static final String ARTICLE_TITLE_MUST_NOT_BE_BLANK_CODE = "ARTICLE_TITLE_MUST_NOT_BE_BLANK";
    private static final String ARTICLE_CONTENT_MUST_NOT_BE_NULL_CODE = "ARTICLE_CONTENT_MUST_NOT_BE_NULL";
    private static final String ARTICLE_NOT_PUBLISHABLE_CODE = "ARTICLE_NOT_PUBLISHABLE";
    private static final String ARTICLE_NOT_ARCHIVABLE_CODE = "ARTICLE_NOT_ARCHIVABLE";
    private static final String ARTICLE_NOT_DELETABLE_CODE = "ARTICLE_NOT_DELETABLE";
    private static final String TAG_NOT_FOUND_CODE = "TAG_NOT_FOUND";

    private static final String ARTICLE_PUBLISHED_NOTIFICATION_TITLE = "Nouvel article publié";

    private static final Set<String> ARTICLE_SLUG_DUPLICATE_CONSTRAINTS = Set.of("uq_article_slug");

    private final ArticleRepository articleRepository;
    private final ArticleTagRepository articleTagRepository;
    private final TagRepository tagRepository;
    private final AppUserRepository appUserRepository;
    private final ArticleSanitizer articleSanitizer;
    private final ArticleSlugGenerator articleSlugGenerator;
    private final ArticleThumbnailExtractor articleThumbnailExtractor;
    private final NotificationService notificationService;
    private final Clock clock;

    public ArticleService(
            ArticleRepository articleRepository,
            ArticleTagRepository articleTagRepository,
            TagRepository tagRepository,
            AppUserRepository appUserRepository,
            ArticleSanitizer articleSanitizer,
            ArticleSlugGenerator articleSlugGenerator,
            ArticleThumbnailExtractor articleThumbnailExtractor,
            NotificationService notificationService,
            Clock clock) {
        this.articleRepository = articleRepository;
        this.articleTagRepository = articleTagRepository;
        this.tagRepository = tagRepository;
        this.appUserRepository = appUserRepository;
        this.articleSanitizer = articleSanitizer;
        this.articleSlugGenerator = articleSlugGenerator;
        this.articleThumbnailExtractor = articleThumbnailExtractor;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /**
     * Retourne les articles publiés sous forme paginée.
     *
     * @param pageable paramètres de pagination
     * @return page des articles publiés
     */
    @Transactional(readOnly = true)
    public Page<ArticleSummaryResponse> listPublishedArticles(Pageable pageable) {
        return articleRepository.findByArticleStatus(ArticleStatus.PUBLISHED, pageable)
                .map(article -> ArticleSummaryResponse.from(article, articleThumbnailExtractor));
    }

    /**
     * Retourne le détail d'un article publié à partir de son slug.
     *
     * @param slug slug de l'article
     * @return détail de l'article publié
     */
    @Transactional(readOnly = true)
    public ArticleResponse getPublishedArticleBySlug(String slug) {
        Objects.requireNonNull(slug, "slug");
        Article article = articleRepository.findBySlugAndArticleStatus(slug, ArticleStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ARTICLE_NOT_FOUND_CODE, "Aucun article publié pour le slug " + slug + "."));
        List<Tag> tags = articleRepository.findTagsByArticleId(article.getId());
        return ArticleResponse.from(article, tags);
    }

    /**
     * Retourne les articles accessibles au personnel, tous statuts confondus.
     *
     * @param pageable paramètres de pagination
     * @return page des articles
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional(readOnly = true)
    public Page<StaffArticleSummaryResponse> listStaffArticles(Pageable pageable) {
        return articleRepository.findAllByOrderByUpdatedAtDescIdDesc(pageable)
                .map(StaffArticleSummaryResponse::from);
    }

    /**
     * Recherche les articles accessibles au personnel selon les critères fournis.
     *
     * @param pageable          paramètres de pagination
     * @param q                 texte recherché
     * @param articleStatus     statut recherché
     * @param modifiedDateFrom  date minimale de modification
     * @param modifiedDateTo    date maximale de modification
     * @param publishedDateFrom date minimale de publication
     * @param publishedDateTo   date maximale de publication
     * @return page des articles correspondants
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional(readOnly = true)
    public Page<StaffArticleSummaryResponse> listStaffArticles(
            Pageable pageable,
            String q,
            ArticleStatus articleStatus,
            LocalDate modifiedDateFrom,
            LocalDate modifiedDateTo,
            LocalDate publishedDateFrom,
            LocalDate publishedDateTo) {
        requireOrderedRange("modifiedDateFrom", modifiedDateFrom, modifiedDateTo);
        requireOrderedRange("publishedDateFrom", publishedDateFrom, publishedDateTo);
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), STAFF_LIST_SORT);
        return articleRepository
                .findAll(ArticleSpecifications.matching(
                        q, articleStatus, modifiedDateFrom, modifiedDateTo, publishedDateFrom, publishedDateTo), sorted)
                .map(StaffArticleSummaryResponse::from);
    }

    private static void requireOrderedRange(String fromParameter, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException(fromParameter,
                    "La date de début doit être antérieure ou égale à la date de fin.");
        }
    }

    /**
     * Retourne le détail d'un article accessible au personnel.
     *
     * @param articleId identifiant de l'article
     * @return détail de l'article
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional(readOnly = true)
    public ArticleResponse getStaffArticleById(Long articleId) {
        Objects.requireNonNull(articleId, "articleId");
        Article article = articleRepository.findByIdWithUsers(articleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ARTICLE_NOT_FOUND_CODE, "Aucun article pour l'identifiant " + articleId + "."));
        List<Tag> tags = articleRepository.findTagsByArticleId(article.getId());
        return ArticleResponse.from(article, tags);
    }

    /**
     * Crée un nouvel article à l'état brouillon.
     *
     * @param request      données de l'article
     * @param authorUserId identifiant de l'auteur
     * @return article créé
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional
    public ArticleResponse createDraftArticle(CreateArticleRequest request, Long authorUserId) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(authorUserId, "authorUserId");

        AppUser author = appUserRepository.findById(authorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        USER_NOT_FOUND_CODE, "Utilisateur introuvable pour l'identifiant " + authorUserId + "."));

        String sanitizedContent = sanitizeAndRequireNonEmptyContent(request.content());
        String slug = generateSlugOrFail(request.title());

        Instant now = clock.instant();
        Article article = new Article();
        article.setAuthorUser(author);
        article.setLastModifiedByUser(null);
        article.setTitle(request.title());
        article.setContent(sanitizedContent);
        article.setSummary(request.summary());
        article.setSlug(slug);
        article.setArticleStatus(ArticleStatus.DRAFT);
        article.setPublishedAt(null);
        article.setCreatedAt(now);
        article.setUpdatedAt(now);

        try {
            articleRepository.saveAndFlush(article);
        } catch (DataIntegrityViolationException ex) {
            if (isArticleSlugDuplicateConstraint(ex)) {
                throw new BusinessRuleException(
                        ARTICLE_SLUG_ALREADY_EXISTS_CODE, "Un Article existe déjà avec ce slug.");
            }
            throw ex;
        }

        return ArticleResponse.from(article, List.of());
    }

    /**
     * Modifie les informations éditoriales d'un article.
     *
     * @param articleId    identifiant de l'article
     * @param request      données à modifier
     * @param editorUserId identifiant de l'utilisateur effectuant la modification
     * @return article mis à jour
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional
    public ArticleResponse updateArticle(Long articleId, UpdateArticleRequest request, Long editorUserId) {
        Objects.requireNonNull(articleId, "articleId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(editorUserId, "editorUserId");

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ARTICLE_NOT_FOUND_CODE, "Aucun article pour l'identifiant " + articleId + "."));

        if (article.getArticleStatus() == ArticleStatus.ARCHIVED) {
            throw new BusinessRuleException(
                    ARTICLE_NOT_EDITABLE_CODE, "Un Article ARCHIVED ne peut plus être modifié.");
        }

        boolean mutated = false;

        if (request.isTitlePresent()) {
            if (request.getTitle() == null || request.getTitle().isBlank()) {
                throw new BusinessRuleException(ARTICLE_TITLE_MUST_NOT_BE_BLANK_CODE, "title ne peut pas être vide.");
            }
            article.setTitle(request.getTitle());
            mutated = true;
        }
        if (request.isContentPresent()) {
            if (request.getContent() == null) {
                throw new BusinessRuleException(
                        ARTICLE_CONTENT_MUST_NOT_BE_NULL_CODE, "content ne peut pas être effacé.");
            }
            article.setContent(sanitizeAndRequireNonEmptyContent(request.getContent()));
            mutated = true;
        }
        if (request.isSummaryPresent()) {
            article.setSummary(request.getSummary());
            mutated = true;
        }

        if (mutated) {
            AppUser editor = appUserRepository.findById(editorUserId)
                    .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_CODE,
                            "Utilisateur introuvable pour l'identifiant " + editorUserId + "."));
            article.setLastModifiedByUser(editor);
            article.setUpdatedAt(clock.instant());
        }

        List<Tag> tags = articleRepository.findTagsByArticleId(article.getId());
        return ArticleResponse.from(article, tags);
    }

    /**
     * Publie un article actuellement à l'état brouillon.
     *
     * @param articleId       identifiant de l'article
     * @param publisherUserId identifiant de l'utilisateur effectuant la publication
     * @return article publié
     */
    @PreAuthorize("hasAuthority('ARTICLE_PUBLISH')")
    @Transactional
    public ArticleResponse publishArticle(Long articleId, Long publisherUserId) {
        Objects.requireNonNull(articleId, "articleId");
        Objects.requireNonNull(publisherUserId, "publisherUserId");

        Article article = articleRepository.findByIdForUpdate(articleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ARTICLE_NOT_FOUND_CODE, "Aucun article pour l'identifiant " + articleId + "."));

        if (article.getArticleStatus() != ArticleStatus.DRAFT) {
            throw new BusinessRuleException(
                    ARTICLE_NOT_PUBLISHABLE_CODE, "Seul un Article DRAFT peut être publié.");
        }

        AppUser publisher = appUserRepository.findById(publisherUserId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_CODE,
                        "Utilisateur introuvable pour l'identifiant " + publisherUserId + "."));

        Instant now = clock.instant();
        article.setArticleStatus(ArticleStatus.PUBLISHED);
        article.setPublishedAt(now);
        article.setLastModifiedByUser(publisher);
        article.setUpdatedAt(now);

        List<AppUser> activeMembers = appUserRepository.findByMemberStatus(MemberStatus.ACTIVE);
        for (AppUser recipient : activeMembers) {
            notificationService.createForArticle(article, recipient, ARTICLE_PUBLISHED_NOTIFICATION_TITLE,
                    "Un nouvel article a été publié : " + article.getTitle() + ".");
        }

        List<Tag> tags = articleRepository.findTagsByArticleId(article.getId());
        return ArticleResponse.from(article, tags);
    }

    /**
     * Archive un article publié.
     *
     * @param articleId    identifiant de l'article
     * @param editorUserId identifiant de l'utilisateur effectuant l'archivage
     * @return article archivé
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional
    public ArticleResponse archiveArticle(Long articleId, Long editorUserId) {
        Objects.requireNonNull(articleId, "articleId");
        Objects.requireNonNull(editorUserId, "editorUserId");

        Article article = articleRepository.findByIdForUpdate(articleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ARTICLE_NOT_FOUND_CODE, "Aucun article pour l'identifiant " + articleId + "."));

        if (article.getArticleStatus() != ArticleStatus.PUBLISHED) {
            throw new BusinessRuleException(
                    ARTICLE_NOT_ARCHIVABLE_CODE, "Seul un Article PUBLISHED peut être archivé.");
        }

        AppUser editor = appUserRepository.findById(editorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_CODE,
                        "Utilisateur introuvable pour l'identifiant " + editorUserId + "."));

        article.setArticleStatus(ArticleStatus.ARCHIVED);
        article.setLastModifiedByUser(editor);
        article.setUpdatedAt(clock.instant());

        List<Tag> tags = articleRepository.findTagsByArticleId(article.getId());
        return ArticleResponse.from(article, tags);
    }

    /**
     * Supprime définitivement un article à l'état brouillon.
     *
     * @param articleId identifiant de l'article
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional
    public void deleteDraftArticle(Long articleId) {
        Objects.requireNonNull(articleId, "articleId");

        Article article = articleRepository.findByIdForUpdate(articleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ARTICLE_NOT_FOUND_CODE, "Aucun article pour l'identifiant " + articleId + "."));

        if (article.getArticleStatus() != ArticleStatus.DRAFT) {
            throw new BusinessRuleException(
                    ARTICLE_NOT_DELETABLE_CODE, "Seul un Article DRAFT peut être supprimé physiquement.");
        }

        articleTagRepository.deleteByIdArticleId(articleId);
        articleRepository.delete(article);
    }

    /**
     * Modifie les tags associés à un article.
     *
     * @param articleId   identifiant de l'article
     * @param request     sélection finale des tags
     * @param actorUserId identifiant de l'utilisateur effectuant la modification
     * @return article avec ses tags mis à jour
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional
    public ArticleResponse associateTags(Long articleId, UpdateArticleTagsRequest request, Long actorUserId) {
        Objects.requireNonNull(articleId, "articleId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(actorUserId, "actorUserId");

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ARTICLE_NOT_FOUND_CODE, "Aucun article pour l'identifiant " + articleId + "."));

        if (article.getArticleStatus() == ArticleStatus.ARCHIVED) {
            throw new BusinessRuleException(
                    ARTICLE_NOT_EDITABLE_CODE, "Un Article ARCHIVED ne peut plus être modifié.");
        }

        AppUser actor = appUserRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_CODE,
                        "Utilisateur introuvable pour l'identifiant " + actorUserId + "."));

        List<Tag> targetTags = resolveTags(request.tagIds());
        replaceArticleTags(article, targetTags);

        article.setLastModifiedByUser(actor);
        article.setUpdatedAt(clock.instant());

        List<Tag> tags = articleRepository.findTagsByArticleId(article.getId());
        return ArticleResponse.from(article, tags);
    }

    private List<Tag> resolveTags(List<Long> tagIds) {
        Set<Long> uniqueTagIds = new LinkedHashSet<>(tagIds);
        List<Tag> tags = new ArrayList<>();
        for (Long tagId : uniqueTagIds) {
            tags.add(tagRepository.findById(tagId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            TAG_NOT_FOUND_CODE, "Aucun Tag pour l'identifiant " + tagId + ".")));
        }
        return tags;
    }

    private void replaceArticleTags(Article article, List<Tag> targetTags) {
        List<ArticleTag> currentAssociations = articleTagRepository.findByIdArticleId(article.getId());
        Set<Long> currentTagIds = new HashSet<>();
        for (ArticleTag association : currentAssociations) {
            currentTagIds.add(association.getTag().getId());
        }
        Set<Long> targetTagIds = new HashSet<>();
        for (Tag tag : targetTags) {
            targetTagIds.add(tag.getId());
        }

        for (ArticleTag association : currentAssociations) {
            if (!targetTagIds.contains(association.getTag().getId())) {
                articleTagRepository.delete(association);
            }
        }
        for (Tag tag : targetTags) {
            if (!currentTagIds.contains(tag.getId())) {
                ArticleTag newAssociation = new ArticleTag();
                newAssociation.setId(new ArticleTagId(article.getId(), tag.getId()));
                newAssociation.setArticle(article);
                newAssociation.setTag(tag);
                articleTagRepository.save(newAssociation);
            }
        }
    }

    // Utilitaires privés
    
    private String sanitizeAndRequireNonEmptyContent(String rawContent) {
        String sanitized = articleSanitizer.sanitize(rawContent);
        if (Jsoup.parse(sanitized).text().isBlank()) {
            throw new BusinessRuleException(
                    ARTICLE_CONTENT_EMPTY_CODE, "Le contenu de l'Article est vide après sanitization.");
        }
        return sanitized;
    }

    private String generateSlugOrFail(String title) {
        try {
            return articleSlugGenerator.generateUniqueSlug(
                    title, candidate -> articleRepository.findBySlug(candidate).isPresent());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(ARTICLE_TITLE_PRODUCES_EMPTY_SLUG_CODE,
                    "Le titre fourni ne permet pas de générer un slug valide.");
        }
    }

    private boolean isArticleSlugDuplicateConstraint(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof ConstraintViolationException hibernateException) {
            return ARTICLE_SLUG_DUPLICATE_CONSTRAINTS.contains(hibernateException.getConstraintName());
        }
        return false;
    }
}
