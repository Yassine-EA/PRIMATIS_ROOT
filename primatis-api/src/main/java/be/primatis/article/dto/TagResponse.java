package be.primatis.article.dto;

import be.primatis.article.Tag;

import java.util.Objects;

/**
 * Représentation d'un tag exposée par l'API.
 */
public record TagResponse(Long id, String code, String label, String description) {

    /**
     * Crée la représentation d'un tag.
     *
     * @param tag tag à représenter
     * @return représentation du tag
     */
    public static TagResponse from(Tag tag) {
        Objects.requireNonNull(tag, "tag");
        return new TagResponse(tag.getId(), tag.getCode(), tag.getLabel(), tag.getDescription());
    }
}
