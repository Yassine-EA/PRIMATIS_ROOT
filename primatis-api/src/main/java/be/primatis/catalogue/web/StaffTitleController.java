package be.primatis.catalogue.web;

import be.primatis.catalogue.CatalogueService;
import be.primatis.catalogue.Language;
import be.primatis.catalogue.TitleStatus;
import be.primatis.catalogue.dto.CreateTitleRequest;
import be.primatis.catalogue.dto.TitleDetailResponse;
import be.primatis.catalogue.dto.TitleResponse;
import be.primatis.catalogue.dto.UpdateTitleRequest;
import be.primatis.catalogue.dto.UpdateTitleStatusRequest;
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
 * Contrôleur de gestion des titres réservé au personnel.
 */
@RestController
@RequestMapping("/api/v1/staff/titles")
@Validated
@Tag(name = "Catalogue", description = "Gestion staff du catalogue (Titles)")
public class StaffTitleController {

        private final CatalogueService catalogueService;

        public StaffTitleController(CatalogueService catalogueService) {
                this.catalogueService = catalogueService;
        }

        /**
         * Recherche les titres du catalogue selon les critères fournis.
         *
         * @param q           texte recherché
         * @param authorId    identifiant de l'auteur
         * @param genreCode   code du genre
         * @param language    langue du titre
         * @param titleStatus statut du titre
         * @param page        numéro de la page
         * @param size        nombre d'éléments par page
         * @return page des titres correspondants
         */
        @Operation(summary = "Recherche paginée du catalogue (staff)", description = "Retourne une page de Titles, quel que soit leur statut. "
                        + "Les filtres q, authorId, genreCode, language et titleStatus peuvent être combinés. "
                        + "La pagination est basée sur un index commençant à 0, avec une taille par défaut "
                        + "de 20 éléments et un maximum de 100.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Page de Titles."),
                        @ApiResponse(responseCode = "400", description = "Paramètres de recherche ou de pagination invalides.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping
        public PageResponse<TitleResponse> searchTitles(
                        @RequestParam(required = false) String q,
                        @RequestParam(required = false) Long authorId,
                        @RequestParam(required = false) String genreCode,
                        @RequestParam(required = false) Language language,
                        @RequestParam(required = false) TitleStatus titleStatus,
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

                Pageable pageable = PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Direction.ASC, "title", "id"));

                return PageResponse.from(
                                catalogueService.searchStaffTitles(
                                                q,
                                                authorId,
                                                genreCode,
                                                language,
                                                titleStatus,
                                                pageable));
        }

        /**
         * Retourne le détail d'un titre.
         *
         * @param titleId identifiant du titre
         * @return détail du titre
         */
        @Operation(summary = "Détail staff d'un Title", description = "Retourne le détail d'un Title, qu'il soit ACTIVE ou WITHDRAWN. "
                        + "Les exemplaires physiques ne sont pas inclus dans cette réponse.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Title trouvé."),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Aucun Title pour cet identifiant.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping("/{titleId}")
        public TitleDetailResponse getTitleById(@PathVariable Long titleId) {
                return catalogueService.getStaffTitleById(titleId);
        }

        /**
         * Crée un nouveau titre.
         *
         * @param request              données du titre
         * @param uriComponentsBuilder constructeur utilisé pour générer l'URI de la
         *                             ressource créée
         * @return réponse contenant le titre créé
         */
        @Operation(summary = "Création staff d'un Title", description = "Crée un nouveau Title à l'état ACTIVE. "
                        + "Au moins un Author existant doit être associé au Title. "
                        + "Les Authors et Genres référencés doivent déjà exister.")
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Title créé."),
                        @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Author ou Genre référencé inexistant.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "ISBN déjà utilisé par un autre Title.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PostMapping
        public ResponseEntity<TitleDetailResponse> createTitle(
                        @Valid @RequestBody CreateTitleRequest request,
                        UriComponentsBuilder uriComponentsBuilder) {

                TitleDetailResponse response = catalogueService.createTitle(request);

                URI location = uriComponentsBuilder
                                .replacePath("/api/v1/staff/titles/{id}")
                                .buildAndExpand(response.id())
                                .toUri();

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .location(location)
                                .body(response);
        }

        /**
         * Modifie les informations d'un titre.
         *
         * @param titleId identifiant du titre
         * @param request données à modifier
         * @return titre mis à jour
         */
        @Operation(summary = "Modification staff d'un Title", description = "Met à jour un Title existant. "
                        + "Les champs absents de la requête restent inchangés. "
                        + "Le statut du Title est géré séparément par l'action dédiée.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Title mis à jour."),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Title, Author ou Genre référencé inexistant.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Données invalides, liste d'Authors vide "
                                        + "ou ISBN déjà utilisé par un autre Title.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PatchMapping("/{titleId}")
        public TitleDetailResponse updateTitle(
                        @PathVariable Long titleId,
                        @RequestBody UpdateTitleRequest request) {

                return catalogueService.updateTitle(titleId, request);
        }

        /**
         * Modifie le statut d'un titre.
         *
         * @param titleId identifiant du titre
         * @param request nouveau statut du titre
         * @return titre avec son statut mis à jour
         */
        @Operation(summary = "Transition TitleStatus", description = "Modifie le statut d'un Title entre ACTIVE et WITHDRAWN. "
                        + "Le passage à WITHDRAWN ne supprime aucune donnée associée et peut être annulé.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "TitleStatus appliqué."),
                        @ApiResponse(responseCode = "400", description = "Statut absent ou valeur inconnue.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Aucun Title pour cet identifiant.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PatchMapping("/{titleId}/status")
        public TitleDetailResponse updateTitleStatus(
                        @PathVariable Long titleId,
                        @Valid @RequestBody UpdateTitleStatusRequest request) {

                return catalogueService.updateTitleStatus(titleId, request);
        }
}