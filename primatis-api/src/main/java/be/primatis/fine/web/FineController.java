package be.primatis.fine.web;

import be.primatis.exception.ApiErrorResponse;
import be.primatis.fine.FineService;
import be.primatis.fine.FineStatus;
import be.primatis.fine.dto.FineResponse;
import be.primatis.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;


/**
 * Contrôleur de consultation et de gestion des amendes.
 */
@RestController
@Validated
public class FineController {

    private final FineService fineService;

    public FineController(FineService fineService) {
        this.fineService = fineService;
    }

    /**
     * Recherche les amendes selon les critères fournis.
     *
     * @param page numéro de la page
     * @param size nombre d'éléments par page
     * @param q texte recherché
     * @param fineStatus statut recherché
     * @param issuedDateFrom date minimale d'émission
     * @param issuedDateTo date maximale d'émission
     * @param closedDateFrom date minimale de clôture
     * @param closedDateTo date maximale de clôture
     * @return page des amendes correspondantes
     */
    @Operation(
            summary = "Liste paginée de toutes les amendes",
            description = "Retourne une page d'amendes, tous membres confondus (FINE_READ requis). "
                    + "Historique complet (UNPAID/PAID/CANCELLED) sans critère. "
                    + "Tri par date d'émission décroissante. Pagination 0-based, taille par défaut 20, maximum 100. "
                    + "Filtres optionnels, combinés en ET logique et appliqués avant la pagination : "
                    + "q (sous-chaîne insensible à la casse sur prénom, nom, n° adhérent de l'emprunteur ou titre), "
                    + "fineStatus (UNPAID, PAID, CANCELLED), issuedDateFrom/issuedDateTo (date d'émission) et "
                    + "closedDateFrom/closedDateTo (date de clôture = paiement ou annulation ; une amende impayée "
                    + "n'est jamais retournée par un filtre de clôture), jours UTC inclusifs, format AAAA-MM-JJ. "
                    + "Un intervalle dont le début dépasse la fin est refusé (400).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page d'amendes."),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination ou de filtre invalides, "
                    + "ou intervalle de dates incohérent (INVALID_DATE_RANGE).",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission FINE_READ manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/fines")
    public PageResponse<FineResponse> listFines(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) FineStatus fineStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedDateTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closedDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closedDateTo) {
        return PageResponse.from(fineService.listFines(
                pageable(page, size), q, fineStatus, issuedDateFrom, issuedDateTo, closedDateFrom, closedDateTo));
    }

    /**
     * Retourne les amendes de l'utilisateur authentifié.
     *
     * @param page numéro de la page
     * @param size nombre d'éléments par page
     * @param authentication utilisateur authentifié
     * @return page des amendes de l'utilisateur
     */
    @Operation(
            summary = "Amendes de l'utilisateur authentifié",
            description = "Retourne une page des amendes de l'utilisateur authentifié, historique complet inclus "
                    + "(UNPAID/PAID/CANCELLED). Aucun identifiant client : l'utilisateur est dérivé du JWT. "
                    + "Aucune permission requise. Page vide (jamais 404) si l'utilisateur n'a aucune amende.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page d'amendes de l'utilisateur authentifié."),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination invalides.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/me/fines")
    public PageResponse<FineResponse> listOwnFines(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication) {
        return PageResponse.from(fineService.listOwnFines(currentUserId(authentication), pageable(page, size)));
    }

    /**
     * Confirme le paiement externe d'une amende.
     *
     * @param fineId identifiant de l'amende
     * @return amende mise à jour
     */
    @Operation(
            summary = "Confirmation du paiement externe d'une amende",
            description = "Enregistre la confirmation administrative qu'un règlement a eu lieu hors PRIMATIS "
                    + "(FINE_MANAGE requis). PRIMATIS ne traite aucune transaction financière. "
                    + "Applique UNPAID -> PAID et renseigne paidAt. Seule une Fine UNPAID peut être confirmée.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paiement confirmé."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission FINE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Amende introuvable.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Amende déjà payée ou annulée.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/api/v1/fines/{fineId}/payment-confirmation")
    public FineResponse confirmExternalPayment(@PathVariable Long fineId) {
        return fineService.confirmExternalPayment(fineId);
    }

    /**
     * Annule une amende impayée.
     *
     * @param fineId identifiant de l'amende
     * @return amende annulée
     */
    @Operation(
            summary = "Annulation d'une amende",
            description = "Annule une Fine UNPAID (FINE_MANAGE requis). Applique UNPAID -> CANCELLED et renseigne "
                    + "cancelledAt. La Fine n'est jamais supprimée : conservée comme historique. "
                    + "Une amende déjà payée ou déjà annulée ne peut pas être annulée.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Amende annulée."),
            @ApiResponse(responseCode = "401", description = "Authentification requise ou JWT invalide.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Permission FINE_MANAGE manquante.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Amende introuvable.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Amende déjà payée ou déjà annulée.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/api/v1/fines/{fineId}/cancel")
    public FineResponse cancelFine(@PathVariable Long fineId) {
        return fineService.cancelFine(fineId);
    }

    
    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issuedAt", "id"));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
