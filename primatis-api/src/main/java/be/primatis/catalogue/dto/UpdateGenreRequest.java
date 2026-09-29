package be.primatis.catalogue.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Requête de modification partielle d'un genre.
 *
 * Chaque champ peut être fourni indépendamment afin de modifier uniquement
 * les informations souhaitées.
 */
public class UpdateGenreRequest {

    private String code;
    private boolean codePresent;

    private String label;
    private boolean labelPresent;

    private String description;
    private boolean descriptionPresent;

    public UpdateGenreRequest() {
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
        this.codePresent = true;
    }

    @JsonIgnore
    public boolean isCodePresent() {
        return codePresent;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
        this.labelPresent = true;
    }

    @JsonIgnore
    public boolean isLabelPresent() {
        return labelPresent;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    @JsonIgnore
    public boolean isDescriptionPresent() {
        return descriptionPresent;
    }
}
