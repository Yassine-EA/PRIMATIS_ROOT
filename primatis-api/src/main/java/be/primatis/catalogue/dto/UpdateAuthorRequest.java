package be.primatis.catalogue.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;

/**
 * Requête de modification partielle d'un auteur.
 *
 * Chaque champ peut être fourni indépendamment afin de modifier uniquement
 * les informations souhaitées.
 */
public class UpdateAuthorRequest {

    private String fullName;
    private boolean fullNamePresent;

    private LocalDate birthDate;
    private boolean birthDatePresent;

    private LocalDate deathDate;
    private boolean deathDatePresent;

    private String nationality;
    private boolean nationalityPresent;

    private String biography;
    private boolean biographyPresent;

    public UpdateAuthorRequest() {
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
        this.fullNamePresent = true;
    }

    @JsonIgnore
    public boolean isFullNamePresent() {
        return fullNamePresent;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
        this.birthDatePresent = true;
    }

    @JsonIgnore
    public boolean isBirthDatePresent() {
        return birthDatePresent;
    }

    public LocalDate getDeathDate() {
        return deathDate;
    }

    public void setDeathDate(LocalDate deathDate) {
        this.deathDate = deathDate;
        this.deathDatePresent = true;
    }

    @JsonIgnore
    public boolean isDeathDatePresent() {
        return deathDatePresent;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
        this.nationalityPresent = true;
    }

    @JsonIgnore
    public boolean isNationalityPresent() {
        return nationalityPresent;
    }

    public String getBiography() {
        return biography;
    }

    public void setBiography(String biography) {
        this.biography = biography;
        this.biographyPresent = true;
    }

    @JsonIgnore
    public boolean isBiographyPresent() {
        return biographyPresent;
    }
}