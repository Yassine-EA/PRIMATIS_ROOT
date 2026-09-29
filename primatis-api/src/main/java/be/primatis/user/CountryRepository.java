package be.primatis.user;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès aux données des pays.
 */
public interface CountryRepository extends JpaRepository<Country, Long> {
}
