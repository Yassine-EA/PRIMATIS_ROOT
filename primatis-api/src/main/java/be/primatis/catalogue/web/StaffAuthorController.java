package be.primatis.catalogue.web;

import be.primatis.catalogue.CatalogueManagementService;
import be.primatis.catalogue.dto.AuthorResponse;
import be.primatis.catalogue.dto.CreateAuthorRequest;
import be.primatis.catalogue.dto.UpdateAuthorRequest;
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
 * Contrôleur de gestion des auteurs réservé au personnel.
 */
@RestController
@RequestMapping("/api/v1/staff/authors")
@Validated
@Tag(name = "Catalogue", description = "Gestion staff des Authors")
public class StaffAuthorController {

        private final CatalogueManagementService catalogueManagementService;

        public StaffAuthorController(CatalogueManagementService catalogueManagementService) {
                this.catalogueManagementService = catalogueManagementService;
        }

        /**
         * Recherche les auteurs selon leur nom et retourne les résultats sous forme
         * paginée.
         *
         * @param q    texte recherché dans le nom de l'auteur
         * @param page numéro de la page
         * @param size nombre d'éléments par page
         * @return page des auteurs correspondants
         */
        @Operation(summary = "Recherche paginée des Authors (staff)", description = "Retourne une page d'Authors. Le paramètre q permet une recherche partielle "
                        + "et insensible à la casse sur le nom complet. La pagination est basée sur un index "
                        + "commençant à 0, avec une taille par défaut de 20 éléments et un maximum de 100.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Page d'Authors."),
                        @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping
        public PageResponse<AuthorResponse> searchAuthors(
                        @RequestParam(required = false) String q,
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

                Pageable pageable = PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Direction.ASC, "fullName", "id"));

                return PageResponse.from(
                                catalogueManagementService.searchAuthors(q, pageable));
        }

        /**
         * Crée un nouvel auteur.
         *
         * @param request              données de l'auteur
         * @param uriComponentsBuilder constructeur utilisé pour générer l'URI de la
         *                             ressource créée
         * @return réponse contenant l'auteur créé
         */
        @Operation(summary = "Création staff d'un Author", description = "Crée un nouvel Author. Plusieurs Authors peuvent porter le même nom.")
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Author créé."),
                        @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Date de naissance postérieure à la date de décès.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PostMapping
        public ResponseEntity<AuthorResponse> createAuthor(
                        @Valid @RequestBody CreateAuthorRequest request,
                        UriComponentsBuilder uriComponentsBuilder) {

                AuthorResponse response = catalogueManagementService.createAuthor(request);

                URI location = uriComponentsBuilder
                                .replacePath("/api/v1/staff/authors/{id}")
                                .buildAndExpand(response.id())
                                .toUri();

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .location(location)
                                .body(response);
        }

        /**
         * Modifie les informations d'un auteur.
         *
         * @param authorId identifiant de l'auteur
         * @param request  données à modifier
         * @return auteur mis à jour
         */
        @Operation(summary = "Modification staff d'un Author", description = "Met à jour un Author existant. Les champs absents de la requête restent inchangés.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Author mis à jour."),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Aucun Author pour cet identifiant.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Nom invalide ou date de naissance postérieure à la date de décès.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PatchMapping("/{authorId}")
        public AuthorResponse updateAuthor(
                        @PathVariable Long authorId,
                        @RequestBody UpdateAuthorRequest request) {

                return catalogueManagementService.updateAuthor(authorId, request);
        }
}