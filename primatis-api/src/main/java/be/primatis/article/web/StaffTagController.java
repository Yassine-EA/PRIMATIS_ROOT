package be.primatis.article.web;

import be.primatis.article.TagService;
import be.primatis.article.dto.CreateTagRequest;
import be.primatis.article.dto.TagResponse;
import be.primatis.article.dto.UpdateTagRequest;
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
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Contrôleur de gestion des tags réservé au personnel.
 */
@RestController
@RequestMapping("/api/v1/staff/tags")
@Validated
@Tag(name = "Articles", description = "Gestion staff des Tags")
public class StaffTagController {

        private final TagService tagService;

        public StaffTagController(TagService tagService) {
                this.tagService = tagService;
        }

        /**
         * Retourne les tags sous forme paginée.
         *
         * @param page numéro de la page
         * @param size nombre d'éléments par page
         * @return page des tags
         */
        @Operation(summary = "Liste paginée des Tags (staff)", description = "Retourne une page de Tags. La pagination est basée sur un index commençant à 0, "
                        + "avec une taille par défaut de 20 éléments et un maximum de 100. "
                        + "Les Tags sont triés par libellé croissant, puis par identifiant croissant.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Page de Tags."),
                        @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping
        public PageResponse<TagResponse> listTags(
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

                Pageable pageable = PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Direction.ASC, "label", "id"));

                return PageResponse.from(tagService.listTags(pageable));
        }

        /**
         * Crée un nouveau tag.
         *
         * @param request              données du tag
         * @param uriComponentsBuilder constructeur utilisé pour générer l'URI de la
         *                             ressource créée
         * @return réponse contenant le tag créé
         */
        @Operation(summary = "Création staff d'un Tag", description = "Crée un nouveau Tag. Son code doit être unique et ne peut plus être modifié "
                        + "après la création.")
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Tag créé."),
                        @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Code déjà utilisé par un autre Tag.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PostMapping
        public ResponseEntity<TagResponse> createTag(
                        @Valid @RequestBody CreateTagRequest request,
                        UriComponentsBuilder uriComponentsBuilder) {

                TagResponse response = tagService.createTag(request);

                URI location = uriComponentsBuilder
                                .replacePath("/api/v1/staff/tags/{id}")
                                .buildAndExpand(response.id())
                                .toUri();

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .location(location)
                                .body(response);
        }

        /**
         * Modifie les informations d'un tag.
         *
         * @param tagId   identifiant du tag
         * @param request données à modifier
         * @return tag mis à jour
         */
        @Operation(summary = "Modification staff d'un Tag", description = "Met à jour un Tag existant. Les champs absents de la requête restent inchangés. "
                        + "Le code du Tag n'est pas modifiable.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Tag mis à jour."),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Aucun Tag pour cet identifiant.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Libellé invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PatchMapping("/{tagId}")
        public TagResponse updateTag(
                        @PathVariable Long tagId,
                        @RequestBody UpdateTagRequest request) {

                return tagService.updateTag(tagId, request);
        }

        /**
         * Supprime un tag lorsqu'il n'est associé à aucun article.
         *
         * @param tagId identifiant du tag
         * @return réponse sans contenu
         */
        @Operation(summary = "Suppression staff d'un Tag", description = "Supprime définitivement un Tag non utilisé. "
                        + "Un Tag encore associé à au moins un Article ne peut pas être supprimé.")
        @ApiResponses({
                        @ApiResponse(responseCode = "204", description = "Tag supprimé."),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission ARTICLE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Aucun Tag pour cet identifiant.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Tag encore associé à au moins un Article.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @DeleteMapping("/{tagId}")
        public ResponseEntity<Void> deleteTag(@PathVariable Long tagId) {
                tagService.deleteTag(tagId);
                return ResponseEntity.noContent().build();
        }
}