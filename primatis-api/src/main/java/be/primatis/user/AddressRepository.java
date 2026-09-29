package be.primatis.user;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès aux données des adresses.
 */
public interface AddressRepository extends JpaRepository<Address, Long> {
}
