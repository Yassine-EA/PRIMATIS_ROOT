package be.primatis.user.web;

import be.primatis.user.Country;
/**
 * Représentation d'un pays exposée par l'API.
 */
public record CountryResponse(Long id, String name, String code) {

    /**
     * Crée la représentation correspondante.
     *
     * @param country objet à représenter
     * @return représentation créée
     */
    public static CountryResponse from(Country country) {
        return new CountryResponse(country.getId(), country.getName(), country.getCode());
    }
}
