package be.primatis.user.web;

import be.primatis.user.Address;
/**
 * Représentation d'une adresse exposée par l'API.
 */
public record AddressResponse(
        Long id,
        String street,
        String streetNumber,
        String boxNumber,
        String additionalInfo,
        CityResponse city) {

    /**
     * Crée la représentation correspondante.
     *
     * @param address objet à représenter
     * @return représentation créée
     */
    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getStreet(),
                address.getStreetNumber(),
                address.getBoxNumber(),
                address.getAdditionalInfo(),
                CityResponse.from(address.getCity()));
    }
}
