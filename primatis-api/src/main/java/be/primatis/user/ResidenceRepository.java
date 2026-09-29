package be.primatis.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Accès aux données des résidences.
 */
public interface ResidenceRepository extends JpaRepository<Residence, Long> {
        Optional<Residence> findByUserIdAndEndDateIsNull(Long userId);

        List<Residence> findByUserIdOrderByStartDateDesc(Long userId);

        @Query("""
                        SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
                        FROM Residence r
                        WHERE r.user.id = :userId
                          AND r.startDate <= COALESCE(:candidateEndDate, r.startDate)
                          AND (r.endDate IS NULL OR r.endDate >= :candidateStartDate)
                        """)
        boolean existsOverlappingPeriod(
                        @Param("userId") Long userId,
                        @Param("candidateStartDate") LocalDate candidateStartDate,
                        @Param("candidateEndDate") LocalDate candidateEndDate);
}
