package be.primatis.article;

import be.primatis.article.dto.CreateTagRequest;
import be.primatis.article.dto.TagResponse;
import be.primatis.article.dto.UpdateTagRequest;
import be.primatis.exception.BusinessRuleException;
import be.primatis.exception.ResourceNotFoundException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

/**
 * Service de gestion des tags utilisés par les articles.
 */
@Service
public class TagService {

    private static final String TAG_NOT_FOUND_CODE = "TAG_NOT_FOUND";
    private static final String TAG_CODE_ALREADY_EXISTS_CODE = "TAG_CODE_ALREADY_EXISTS";
    private static final String TAG_LABEL_MUST_NOT_BE_BLANK_CODE = "TAG_LABEL_MUST_NOT_BE_BLANK";
    private static final String TAG_IN_USE_CODE = "TAG_IN_USE";

    private static final Set<String> TAG_CODE_DUPLICATE_CONSTRAINTS = Set.of("uq_tag_code");
    private static final Set<String> TAG_IN_USE_CONSTRAINTS = Set.of("fk_article_tag_tag_id");

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /**
     * Retourne les tags sous forme paginée.
     *
     * @param pageable paramètres de pagination
     * @return page des tags
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional(readOnly = true)
    public Page<TagResponse> listTags(Pageable pageable) {
        return tagRepository.findAll(pageable).map(TagResponse::from);
    }

    /**
     * Crée un nouveau tag.
     *
     * @param request données du tag
     * @return tag créé
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional
    public TagResponse createTag(CreateTagRequest request) {
        Objects.requireNonNull(request, "request");

        if (tagRepository.existsByCode(request.code())) {
            throw new BusinessRuleException(TAG_CODE_ALREADY_EXISTS_CODE, "Un Tag existe déjà avec ce code.");
        }

        Tag tag = new Tag();
        tag.setCode(request.code());
        tag.setLabel(request.label());
        tag.setDescription(request.description());

        try {
            tagRepository.saveAndFlush(tag);
        } catch (DataIntegrityViolationException ex) {
            if (isConstraintViolation(ex, TAG_CODE_DUPLICATE_CONSTRAINTS)) {
                throw new BusinessRuleException(TAG_CODE_ALREADY_EXISTS_CODE, "Un Tag existe déjà avec ce code.");
            }
            throw ex;
        }

        return TagResponse.from(tag);
    }

    /**
     * Modifie les informations d'un tag.
     *
     * @param tagId   identifiant du tag
     * @param request données à modifier
     * @return tag mis à jour
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional
    public TagResponse updateTag(Long tagId, UpdateTagRequest request) {
        Objects.requireNonNull(tagId, "tagId");
        Objects.requireNonNull(request, "request");

        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        TAG_NOT_FOUND_CODE, "Aucun Tag pour l'identifiant " + tagId + "."));

        if (request.isLabelPresent()) {
            if (request.getLabel() == null || request.getLabel().isBlank()) {
                throw new BusinessRuleException(TAG_LABEL_MUST_NOT_BE_BLANK_CODE, "label ne peut pas être vide.");
            }
            tag.setLabel(request.getLabel());
        }
        if (request.isDescriptionPresent()) {
            tag.setDescription(request.getDescription());
        }

        return TagResponse.from(tag);
    }

    /**
     * Supprime un tag lorsqu'il n'est associé à aucun article.
     *
     * @param tagId identifiant du tag
     */
    @PreAuthorize("hasAuthority('ARTICLE_MANAGE')")
    @Transactional
    public void deleteTag(Long tagId) {
        Objects.requireNonNull(tagId, "tagId");

        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        TAG_NOT_FOUND_CODE, "Aucun Tag pour l'identifiant " + tagId + "."));

        try {
            tagRepository.delete(tag);
            tagRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            if (isConstraintViolation(ex, TAG_IN_USE_CONSTRAINTS)) {
                throw new BusinessRuleException(
                        TAG_IN_USE_CODE, "Ce Tag est encore associé à au moins un Article et ne peut pas être supprimé.");
            }
            throw ex;
        }
    }

    private boolean isConstraintViolation(DataIntegrityViolationException ex, Set<String> constraintNames) {
        Throwable cause = ex.getCause();
        if (cause instanceof ConstraintViolationException hibernateException) {
            return constraintNames.contains(hibernateException.getConstraintName());
        }
        return false;
    }
}
