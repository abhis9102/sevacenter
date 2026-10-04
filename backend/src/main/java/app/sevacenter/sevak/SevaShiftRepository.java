package app.sevacenter.sevak;

import org.springframework.data.jpa.repository.JpaRepository;

/** RLS scopes every query to the current tenant. */
public interface SevaShiftRepository extends JpaRepository<SevaShift, Long> { }
