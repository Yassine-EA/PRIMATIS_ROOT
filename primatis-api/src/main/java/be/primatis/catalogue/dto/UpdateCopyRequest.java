package be.primatis.catalogue.dto;

import be.primatis.catalogue.CopyCondition;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Requête de modification partielle d'un exemplaire.
 *
 * Chaque champ peut être fourni indépendamment afin de modifier uniquement
 * les informations souhaitées.
 */
public class UpdateCopyRequest {

    private String inventoryCode;
    private boolean inventoryCodePresent;

    private String location;
    private boolean locationPresent;

    private CopyCondition copyCondition;
    private boolean copyConditionPresent;

    public UpdateCopyRequest() {
    }

    public String getInventoryCode() {
        return inventoryCode;
    }

    public void setInventoryCode(String inventoryCode) {
        this.inventoryCode = inventoryCode;
        this.inventoryCodePresent = true;
    }

    @JsonIgnore
    public boolean isInventoryCodePresent() {
        return inventoryCodePresent;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
        this.locationPresent = true;
    }

    @JsonIgnore
    public boolean isLocationPresent() {
        return locationPresent;
    }

    public CopyCondition getCopyCondition() {
        return copyCondition;
    }

    public void setCopyCondition(CopyCondition copyCondition) {
        this.copyCondition = copyCondition;
        this.copyConditionPresent = true;
    }

    @JsonIgnore
    public boolean isCopyConditionPresent() {
        return copyConditionPresent;
    }
}