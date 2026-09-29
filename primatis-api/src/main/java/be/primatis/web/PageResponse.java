package be.primatis.web;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Représentation générique d'une page de résultats exposée par l'API.
 *
 * @param content       contenu de la page
 * @param page          numéro de la page courante
 * @param size          nombre d'éléments par page
 * @param totalElements nombre total d'éléments
 * @param totalPages    nombre total de pages
 * @param <T>           type des éléments contenus dans la page
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    /**
     * Crée une réponse paginée à partir d'une page Spring Data.
     *
     * @param page page source
     * @param <T>  type des éléments
     * @return représentation paginée
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}