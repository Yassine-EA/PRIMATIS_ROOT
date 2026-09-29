package be.primatis.user.web;

import be.primatis.user.Residence;

import java.time.LocalDate;
/**
 * Représentation d'une résidence exposée par l'API.
 */
public record ResidenceResponse(Long id, AddressResponse address, LocalDate startDate, LocalDate endDate) {

    /**
     * Crée la représentation correspondante.
     *
     * @param residence objet à représenter
     * @return représentation créée
     */
    public static ResidenceResponse from(Residence residence) {
        return new ResidenceResponse(
                residence.getId(),
                AddressResponse.from(residence.getAddress()),
                residence.getStartDate(),
                residence.getEndDate());
    }
}
