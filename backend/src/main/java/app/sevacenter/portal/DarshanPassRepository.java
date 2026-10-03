package app.sevacenter.portal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DarshanPassRepository extends JpaRepository<DarshanPass, Long> {

    List<DarshanPass> findAllByDevoteeIdOrderByVisitDateDesc(Long devoteeId);

    List<DarshanPass> findAllByVisitDate(LocalDate visitDate);

    Optional<DarshanPass> findByPassNumber(String passNumber);
}
