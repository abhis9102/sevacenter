package app.sevacenter.portal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SevakSignupRepository extends JpaRepository<SevakSignup, Long> {

    List<SevakSignup> findAllByDevoteeIdOrderByCreatedAtDesc(Long devoteeId);

    List<SevakSignup> findAllBySevaArea(String sevaArea);
}
