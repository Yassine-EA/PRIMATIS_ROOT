package be.primatis.catalogue.web;

import be.primatis.catalogue.CatalogueService;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.dto.TitleDetailResponse;
import be.primatis.catalogue.dto.TitleResponse;
import be.primatis.exception.ApiErrorResponse;
import be.primatis.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur public de consultation du catalogue.
 */
@RestController
@RequestMapping("/api/v1/titles")
@Validated
@Tag(name = "Catalogue", description = "Consultation publique du catalogue (Titles)")
public class TitleController {

        private final CatalogueService catalogueService;

        public TitleController(CatalogueService catalogueService) {
                this.catalogueService = catalogueService;
        }

        /**
         * Recherche les titres disponibles dans le catalogue public.
         *
         * @param q         texte recherché
         * @param authorId  identifiant de l'auteur
         * @param genreCode code du genre
         * @param language  langue du titre
         * @param page      numéro de la page
         * @param size      nombre d'éléments par page
         * @return page des titres correspondants
         */
        @Operation(summary = "Recherche paginée du catalogue public", description = "Retourne une page de Titles ACTIVE accessible publiquement. "
                        + "Les filtres q, authorId, genreCode et language peuvent être combinés. "
                        + "La pagination est basée sur un index commençant à 0, avec une taille par défaut "
                        + "de 20 éléments et un maximum de 100. "
                        + "Les résultats sont triés par titre croissant, puis par identifiant croissant.", security = {})
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Page de Titles ACTIVE."),
                        @ApiResponse(responseCode = "400", description = "Paramètres de recherche ou de pagination invalides.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping
        public PageResponse<TitleResponse> searchTitles(
                        @RequestParam(required = false) String q,
                        @RequestParam(required = false) Long authorId,
                        @RequestParam(required = false) String genreCode,
                        @RequestParam(required = false) Language language,
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

                Pageable pageable = PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Direction.ASC, "title", "id"));

                return PageResponse.from(
                                catalogueService.searchPublicTitles(
                                                q,
                                                authorId,
                                                genreCode,
                                                language,
                                                pageable));
        }

        /**
         * Retourne le détail d'un titre disponible dans le catalogue public.
         *
         * @param titleId identifiant du titre
         * @return détail du titre
         */
        @Operation(summary = "Détail public d'un Title", description = "Retourne le détail d'un Title ACTIVE accessible publiquement, "
                        + "avec ses Authors et Genres associés. "
                        + "Un Title inexistant ou non disponible dans le catalogue public retourne une réponse 404. "
                        + "Les exemplaires physiques ne sont pas inclus dans cette réponse.", security = {})
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Title ACTIVE trouvé."),
                        @ApiResponse(responseCode = "404", description = "Aucun Title ACTIVE pour cet identifiant.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping("/{titleId}")
        public TitleDetailResponse getTitleById(@PathVariable Long titleId) {
                return catalogueService.getPublicTitleById(titleId);
        }
}