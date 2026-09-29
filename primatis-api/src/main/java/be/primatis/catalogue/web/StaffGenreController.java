package be.primatis.catalogue.web;

import be.primatis.catalogue.CatalogueManagementService;
import be.primatis.catalogue.dto.CreateGenreRequest;
import be.primatis.catalogue.dto.GenreResponse;
import be.primatis.catalogue.dto.UpdateGenreRequest;
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
 * Contrôleur de gestion des genres réservé au personnel.
 */
@RestController
@RequestMapping("/api/v1/staff/genres")
@Validated
@Tag(name = "Catalogue", description = "Gestion staff des Genres")
public class StaffGenreController {

        private final CatalogueManagementService catalogueManagementService;

        public StaffGenreController(CatalogueManagementService catalogueManagementService) {
                this.catalogueManagementService = catalogueManagementService;
        }

        /**
         * Retourne les genres sous forme paginée.
         *
         * @param page numéro de la page
         * @param size nombre d'éléments par page
         * @return page des genres
         */
        @Operation(summary = "Liste paginée des Genres (staff)", description = "Retourne une page de Genres. La pagination est basée sur un index "
                        + "commençant à 0, avec une taille par défaut de 20 éléments et un maximum de 100. "
                        + "Les Genres sont triés par libellé croissant, puis par identifiant croissant.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Page de Genres."),
                        @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping
        public PageResponse<GenreResponse> listGenres(
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

                Pageable pageable = PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Direction.ASC, "label", "id"));

                return PageResponse.from(
                                catalogueManagementService.listGenres(pageable));
        }

        /**
         * Crée un nouveau genre.
         *
         * @param request              données du genre
         * @param uriComponentsBuilder constructeur utilisé pour générer l'URI de la
         *                             ressource créée
         * @return réponse contenant le genre créé
         */
        @Operation(summary = "Création staff d'un Genre", description = "Crée un nouveau Genre. Le code et le libellé doivent être uniques.")
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Genre créé."),
                        @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Code ou libellé déjà utilisé par un autre Genre.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PostMapping
        public ResponseEntity<GenreResponse> createGenre(
                        @Valid @RequestBody CreateGenreRequest request,
                        UriComponentsBuilder uriComponentsBuilder) {

                GenreResponse response = catalogueManagementService.createGenre(request);

                URI location = uriComponentsBuilder
                                .replacePath("/api/v1/staff/genres/{id}")
                                .buildAndExpand(response.id())
                                .toUri();

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .location(location)
                                .body(response);
        }

        /**
         * Modifie les informations d'un genre.
         *
         * @param genreId identifiant du genre
         * @param request données à modifier
         * @return genre mis à jour
         */
        @Operation(summary = "Modification staff d'un Genre", description = "Met à jour un Genre existant. Les champs absents de la requête restent inchangés. "
                        + "Le code et le libellé restent soumis à leur contrainte d'unicité.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Genre mis à jour."),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission CATALOGUE_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Aucun Genre pour cet identifiant.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Code ou libellé invalide, ou déjà utilisé par un autre Genre.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PatchMapping("/{genreId}")
        public GenreResponse updateGenre(
                        @PathVariable Long genreId,
                        @RequestBody UpdateGenreRequest request) {

                return catalogueManagementService.updateGenre(genreId, request);
        }
}