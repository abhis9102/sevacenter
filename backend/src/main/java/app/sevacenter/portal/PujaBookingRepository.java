package app.sevacenter.portal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PujaBookingRepository extends JpaRepository<PujaBooking, Long> {

    List<PujaBooking> findAllByDevoteeIdOrderByPujaDateDesc(Long devoteeId);

    List<PujaBooking> findAllByPujaDate(LocalDate pujaDate);

    Optional<PujaBooking> findByBookingNumber(String bookingNumber);
}
