package be.primatis.article.web;

import be.primatis.article.ArticleMediaService;
import be.primatis.article.ArticleService;
import be.primatis.article.ArticleStatus;
import be.primatis.article.dto.ArticleMediaUploadResponse;
import be.primatis.article.dto.ArticleResponse;
import be.primatis.article.dto.CreateArticleRequest;
import be.primatis.article.dto.StaffArticleSummaryResponse;
import be.primatis.article.dto.UpdateArticleRequest;
import be.primatis.article.dto.UpdateArticleTagsRequest;
import be.primatis.exception.ApiErrorResponse;
import be.primatis.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * Contrôleur de gestion des articles réservé au personnel.
 */
@RestController
@RequestMapping("/api/v1/staff/articles")
@Validated
@Tag(name = "Articles", description = "Gestion staff des Articles")
public class StaffArticleController {

        private final ArticleService articleService;
        private final ArticleMediaService articleMediaService;

        public StaffArticleController(
                        ArticleService articleService,
                        ArticleMediaService articleMediaService) {
                this.articleService = articleService;
                this.articleMediaService = articleMediaService;
        }

        /**
         * Stocke une image destinée au contenu d'un article.
         *
         * @param file image à enregistrer
         * @return URL publique de l'image enregistrée
         */
        @Operation(summary = "Upload d'une image destinée au contenu riche d'un Article", description = "Stocke une image JPEG, PNG ou WebP de 5 Mio maximum et retourne son "
                        + "URL publique relative. L'image peut ensuite être insérée dans le contenu HTML "
                        + "d'un Article. Aucun chemin du système de fichiers n'est exposé.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Image stockée, URL publique retournée."),
                        @ApiResponse(responseCode = "400", description = "Fichier absent, type non autorisé ou taille excessive.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PostMapping(path = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ArticleMediaUploadResponse uploadArticleMedia(
                        @RequestParam("file") MultipartFile file) {
                return new ArticleMediaUploadResponse(articleMediaService.storeImage(file));
        }

        /**
         * Retourne les articles accessibles au personnel selon les critères fournis.
         *
         * @param page              numéro de la page
         * @param size              nombre d'éléments par page
         * @param q                 texte recherché
         * @param articleStatus     statut recherché
         * @param modifiedDateFrom  date minimale de modification
         * @param modifiedDateTo    date maximale de modification
         * @param publishedDateFrom date minimale de publication
         * @param publishedDateTo   date maximale de publication
         * @return page des articles correspondants
         */
        @Operation(summary = "Liste staff paginée des Articles", description = "Retourne une page d'Articles, tous statuts confondus "
                        + "(DRAFT, PUBLISHED et ARCHIVED). Pagination 0-based, taille par défaut 20 "
                        + "et maximum 100. Les filtres optionnels sont combinés en ET logique. "
                        + "Les intervalles de dates sont inclusifs et utilisent le format AAAA-MM-JJ.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Page d'Articles, tous statuts."),
                        @ApiResponse(responseCode = "400", description = "Paramètres de pagination ou de filtre invalides, "
                                        + "ou intervalle de dates incohérent.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping
        public PageResponse<StaffArticleSummaryResponse> listStaffArticles(
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                        @RequestParam(required = false) String q,
                        @RequestParam(required = false) ArticleStatus articleStatus,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate modifiedDateFrom,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate modifiedDateTo,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publishedDateFrom,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publishedDateTo) {

                Pageable pageable = PageRequest.of(page, size);

                return PageResponse.from(articleService.listStaffArticles(
                                pageable,
                                q,
                                articleStatus,
                                modifiedDateFrom,
                                modifiedDateTo,
                                publishedDateFrom,
                                publishedDateTo));
        }

        /**
         * Retourne le détail d'un article accessible au personnel.
         *
         * @param articleId identifiant de l'article
         * @return détail de l'article
         */
        @Operation(summary = "Détail staff d'un Article", description = "Retourne le détail d'un Article quel que soit son statut, "
                        + "avec ses Tags associés.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Article trouvé."),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Aucun Article pour cet identifiant.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping("/{articleId}")
        public ArticleResponse getStaffArticleById(@PathVariable Long articleId) {
                return articleService.getStaffArticleById(articleId);
        }

        /**
         * Crée un nouvel article à l'état brouillon.
         *
         * @param request        données de l'article
         * @param authentication utilisateur authentifié
         * @return réponse contenant l'article créé
         */
        @Operation(summary = "Création staff d'un Article", description = "Crée un Article à l'état DRAFT. L'auteur est déterminé à partir "
                        + "de l'utilisateur authentifié. Le slug est généré par le backend et le contenu "
                        + "HTML est nettoyé avant sa persistance.")
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Article DRAFT créé."),
                        @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Contenu vide après nettoyage, titre ne produisant aucun slug "
                                        + "valide ou slug déjà utilisé.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PostMapping
        public ResponseEntity<ArticleResponse> createDraftArticle(
                        @Valid @RequestBody CreateArticleRequest request,
                        Authentication authentication) {

                ArticleResponse response = articleService.createDraftArticle(request, currentUserId(authentication));

                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        /**
         * Modifie les informations éditoriales d'un article.
         *
         * @param articleId      identifiant de l'article
         * @param request        données à modifier
         * @param authentication utilisateur authentifié
         * @return article mis à jour
         */
        @Operation(summary = "Modification staff d'un Article DRAFT ou PUBLISHED", description = "Met à jour un Article DRAFT ou PUBLISHED existant. "
                        + "Les champs absents de la requête restent inchangés. "
                        + "Un Article ARCHIVED ne peut plus être modifié.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Article mis à jour."),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Aucun Article pour cet identifiant.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Article ARCHIVED ou données éditoriales invalides.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PatchMapping("/{articleId}")
        public ArticleResponse updateArticle(
                        @PathVariable Long articleId,
                        @RequestBody UpdateArticleRequest request,
                        Authentication authentication) {

                return articleService.updateArticle(
                                articleId,
                                request,
                                currentUserId(authentication));
        }

        /**
         * Publie un article actuellement à l'état brouillon.
         *
         * @param articleId      identifiant de l'article
         * @param authentication utilisateur authentifié
         * @return article publié
         */
        @Operation(summary = "Publication d'un Article", description = "Publie un Article actuellement à l'état DRAFT. "
                        + "La date de publication est renseignée et les notifications de publication "
                        + "sont créées pour les membres concernés.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Article publié."),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission ARTICLE_PUBLISH manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Aucun Article pour cet identifiant.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Article déjà PUBLISHED ou ARCHIVED.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PostMapping("/{articleId}/publish")
        public ArticleResponse publishArticle(
                        @PathVariable Long articleId,
                        Authentication authentication) {

                return articleService.publishArticle(
                                articleId,
                                currentUserId(authentication));
        }

        /**
         * Archive un article publié.
         *
         * @param articleId      identifiant de l'article
         * @param authentication utilisateur authentifié
         * @return article archivé
         */
        @Operation(summary = "Archivage d'un Article", description = "Archive un Article actuellement publié. "
                        + "Un Article archivé ne peut pas revenir à un état précédent.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Article archivé."),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Aucun Article pour cet identifiant.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Article non PUBLISHED.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PostMapping("/{articleId}/archive")
        public ArticleResponse archiveArticle(
                        @PathVariable Long articleId,
                        Authentication authentication) {

                return articleService.archiveArticle(
                                articleId,
                                currentUserId(authentication));
        }

        /**
         * Supprime définitivement un article à l'état brouillon.
         *
         * @param articleId identifiant de l'article
         * @return réponse sans contenu
         */
        @Operation(summary = "Suppression physique d'un Article DRAFT", description = "Supprime définitivement un Article à l'état DRAFT. "
                        + "Les Articles PUBLISHED ou ARCHIVED ne peuvent pas être supprimés physiquement.")
        @ApiResponses({
                        @ApiResponse(responseCode = "204", description = "Article DRAFT supprimé."),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Aucun Article pour cet identifiant.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Article non DRAFT.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @DeleteMapping("/{articleId}")
        public ResponseEntity<Void> deleteDraftArticle(@PathVariable Long articleId) {
                articleService.deleteDraftArticle(articleId);
                return ResponseEntity.noContent().build();
        }

        /**
         * Modifie les tags associés à un article.
         *
         * @param articleId      identifiant de l'article
         * @param request        sélection finale des tags
         * @param authentication utilisateur authentifié
         * @return article avec ses tags mis à jour
         */
        @Operation(summary = "Association/dissociation de Tags sur un Article", description = "Remplace l'ensemble des Tags associés à un Article DRAFT ou PUBLISHED. "
                        + "La liste fournie représente la sélection finale des Tags. "
                        + "Un Article ARCHIVED ne peut plus être modifié.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Sélection de Tags mise à jour."),
                        @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Article inexistant ou au moins un identifiant de Tag inconnu.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Article ARCHIVED.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PatchMapping("/{articleId}/tags")
        public ArticleResponse associateTags(
                        @PathVariable Long articleId,
                        @Valid @RequestBody UpdateArticleTagsRequest request,
                        Authentication authentication) {

                return articleService.associateTags(
                                articleId,
                                request,
                                currentUserId(authentication));
        }

        private Long currentUserId(Authentication authentication) {
                return Long.valueOf(authentication.getName());
        }
}