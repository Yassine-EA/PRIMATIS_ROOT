package be.primatis.user;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès aux données des villes.
 */
public interface CityRepository extends JpaRepository<City, Long> {
}
