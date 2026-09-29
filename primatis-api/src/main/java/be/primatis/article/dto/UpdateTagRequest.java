package be.primatis.article.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Requête de modification partielle d'un tag.
 *
 * Le code du tag n'est pas modifiable.
 */
public class UpdateTagRequest {

    private String label;
    private boolean labelPresent;

    private String description;
    private boolean descriptionPresent;

    public UpdateTagRequest() {
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
