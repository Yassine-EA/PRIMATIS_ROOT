package be.primatis.user.web;

import be.primatis.user.City;
/**
 * Représentation d'une ville exposée par l'API.
 */
public record CityResponse(Long id, String name, String postalCode, CountryResponse country) {

    /**
     * Crée la représentation correspondante.
     *
     * @param city objet à représenter
     * @return représentation créée
     */
    public static CityResponse from(City city) {
        return new CityResponse(city.getId(), city.getName(), city.getPostalCode(),
                CountryResponse.from(city.getCountry()));
    }
}
