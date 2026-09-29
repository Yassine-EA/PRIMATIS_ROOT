package be.primatis.article.web;

import be.primatis.article.ArticleService;
import be.primatis.article.dto.ArticleResponse;
import be.primatis.article.dto.ArticleSummaryResponse;
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
 * Contrôleur public de consultation des articles publiés.
 */
@RestController
@RequestMapping("/api/v1/articles")
@Validated
@Tag(name = "Articles", description = "Consultation publique des Articles publiés")
public class ArticleController {

        private final ArticleService articleService;

        public ArticleController(ArticleService articleService) {
                this.articleService = articleService;
        }

        /**
         * Retourne les articles publiés sous forme paginée.
         *
         * @param page numéro de la page
         * @param size nombre d'éléments par page
         * @return page des articles publiés
         */
        @Operation(summary = "Liste paginée des Articles publiés", description = "Retourne une page d'Articles PUBLISHED accessible publiquement. "
                        + "La pagination est basée sur un index commençant à 0, avec une taille par défaut "
                        + "de 20 éléments et un maximum de 100. Les Articles sont triés par date de publication "
                        + "décroissante, puis par identifiant décroissant.", security = {})
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Page d'Articles PUBLISHED."),
                        @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping
        public PageResponse<ArticleSummaryResponse> listArticles(
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

                Pageable pageable = PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Direction.DESC, "publishedAt", "id"));

                return PageResponse.from(articleService.listPublishedArticles(pageable));
        }

        /**
         * Retourne le détail d'un article publié à partir de son slug.
         *
         * @param slug slug de l'article
         * @return détail de l'article publié
         */
        @Operation(summary = "Détail public d'un Article par slug", description = "Retourne le détail d'un Article PUBLISHED accessible publiquement, "
                        + "avec ses Tags associés. Un slug inexistant ou correspondant à un Article "
                        + "non publié retourne une réponse 404.", security = {})
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Article PUBLISHED trouvé."),
                        @ApiResponse(responseCode = "404", description = "Aucun Article PUBLISHED pour ce slug.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping("/{slug}")
        public ArticleResponse getArticleBySlug(@PathVariable String slug) {
                return articleService.getPublishedArticleBySlug(slug);
        }
}