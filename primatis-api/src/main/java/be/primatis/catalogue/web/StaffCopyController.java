package be.primatis.catalogue.web;

import be.primatis.catalogue.CopyService;
import be.primatis.catalogue.dto.CopyResponse;
import be.primatis.catalogue.dto.CreateCopyRequest;
import be.primatis.catalogue.dto.UpdateCopyAvailabilityRequest;
import be.primatis.catalogue.dto.UpdateCopyRequest;
import be.primatis.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Contrôleur de gestion des exemplaires réservé au personnel.
 */
@RestController
@RequestMapping("/api/v1/staff/titles/{titleId}/copies")
@Tag(name = "Catalogue", description = "Gestion staff des Copies (exemplaires)")
public class StaffCopyController {

        private final CopyService copyService;

        public StaffCopyController(CopyService copyService) {
                this.copyService = copyService;
        }

        /**
         * Retourne les exemplaires associés à un titre.
         *
         * @param titleId identifiant du titre
         * @return liste des exemplaires
         */
        @Operation(summary = "Liste des exemplaires d'un Title (staff)", description = "Retourne tous les exemplaires associés à un Title, "
                        + "triés par code d'inventaire.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Liste des exemplaires, éventuellement vide."),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission COPY_READ manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Aucun Title pour cet identifiant.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping
        public List<CopyResponse> listCopies(@PathVariable Long titleId) {
                return copyService.listCopiesByTitle(titleId);
        }

        /**
         * Retourne le détail d'un exemplaire appartenant à un titre.
         *
         * @param titleId identifiant du titre
         * @param copyId  identifiant de l'exemplaire
         * @return détail de l'exemplaire
         */
        @Operation(summary = "Détail d'un exemplaire (staff)", description = "Retourne un exemplaire à partir de son identifiant. "
                        + "L'exemplaire doit appartenir au Title indiqué dans le chemin.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Exemplaire trouvé."),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission COPY_READ manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Aucun exemplaire pour cet identifiant sous ce Title.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping("/{copyId}")
        public CopyResponse getCopyById(
                        @PathVariable Long titleId,
                        @PathVariable Long copyId) {

                return copyService.getCopyById(titleId, copyId);
        }

        /**
         * Crée un nouvel exemplaire pour un titre.
         *
         * @param titleId              identifiant du titre
         * @param request              données de l'exemplaire
         * @param uriComponentsBuilder constructeur utilisé pour générer l'URI de la
         *                             ressource créée
         * @return réponse contenant l'exemplaire créé
         */
        @Operation(summary = "Création staff d'un exemplaire", description = "Crée un exemplaire pour le Title indiqué. "
                        + "L'état physique et la disponibilité sont obligatoires. "
                        + "Seuls les statuts AVAILABLE et UNAVAILABLE peuvent être définis lors de la création.")
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Exemplaire créé."),
                        @ApiResponse(responseCode = "400", description = "Requête structurellement invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission COPY_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Aucun Title pour cet identifiant.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Code d'inventaire déjà utilisé, combinaison entre état physique "
                                        + "et disponibilité invalide, ou statut non autorisé à la création.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PostMapping
        public ResponseEntity<CopyResponse> createCopy(
                        @PathVariable Long titleId,
                        @Valid @RequestBody CreateCopyRequest request,
                        UriComponentsBuilder uriComponentsBuilder) {

                CopyResponse response = copyService.createCopy(titleId, request);

                URI location = uriComponentsBuilder
                                .replacePath("/api/v1/staff/titles/{titleId}/copies/{copyId}")
                                .buildAndExpand(titleId, response.id())
                                .toUri();

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .location(location)
                                .body(response);
        }

        /**
         * Modifie les informations d'un exemplaire.
         *
         * @param titleId identifiant du titre
         * @param copyId  identifiant de l'exemplaire
         * @param request données à modifier
         * @return exemplaire mis à jour
         */
        @Operation(summary = "Modification staff d'un exemplaire", description = "Met à jour un exemplaire existant. "
                        + "Les champs absents de la requête restent inchangés. "
                        + "La disponibilité est modifiée séparément par l'action dédiée.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Exemplaire mis à jour."),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission COPY_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Aucun exemplaire pour cet identifiant sous ce Title.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Code d'inventaire ou état physique invalide, "
                                        + "ou code d'inventaire déjà utilisé par un autre exemplaire.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PatchMapping("/{copyId}")
        public CopyResponse updateCopy(
                        @PathVariable Long titleId,
                        @PathVariable Long copyId,
                        @RequestBody UpdateCopyRequest request) {

                return copyService.updateCopy(titleId, copyId, request);
        }

        /**
         * Modifie manuellement la disponibilité d'un exemplaire.
         *
         * @param titleId identifiant du titre
         * @param copyId  identifiant de l'exemplaire
         * @param request nouveau statut de disponibilité
         * @return exemplaire mis à jour
         */
        @Operation(summary = "Disponibilité manuelle d'un exemplaire", description = "Modifie manuellement la disponibilité d'un exemplaire entre AVAILABLE "
                        + "et UNAVAILABLE. Les statuts ON_LOAN et RESERVED sont gérés par les processus "
                        + "de prêt et de réservation. Un exemplaire LOST ou OUT_OF_SERVICE ne peut pas "
                        + "être marqué comme AVAILABLE.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Disponibilité mise à jour."),
                        @ApiResponse(responseCode = "400", description = "Statut absent ou valeur inconnue.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Permission COPY_MANAGE manquante.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Aucun exemplaire pour cet identifiant sous ce Title.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "409", description = "Statut de disponibilité non modifiable manuellement "
                                        + "ou incompatible avec l'état physique de l'exemplaire.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PatchMapping("/{copyId}/availability")
        public CopyResponse updateCopyAvailability(
                        @PathVariable Long titleId,
                        @PathVariable Long copyId,
                        @Valid @RequestBody UpdateCopyAvailabilityRequest request) {

                return copyService.updateCopyAvailability(titleId, copyId, request);
        }
}