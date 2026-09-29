package be.primatis.catalogue.web;

import be.primatis.catalogue.CatalogueService;
import be.primatis.catalogue.dto.GenreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Contrôleur public de consultation des genres du catalogue.
 */
@RestController
@RequestMapping("/api/v1/genres")
@Tag(name = "Catalogue", description = "Consultation publique des Genres")
public class GenreController {

    private final CatalogueService catalogueService;

    public GenreController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    /**
     * Retourne la liste complète des genres disponibles dans le catalogue.
     *
     * @return liste des genres
     */
    @Operation(summary = "Liste publique des Genres", description = "Retourne l'intégralité des Genres accessibles publiquement, "
            + "triés par libellé. La liste n'est pas paginée.", security = {})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste complète des Genres.")
    })
    @GetMapping
    public List<GenreResponse> listGenres() {
        return catalogueService.listPublicGenres();
    }
}