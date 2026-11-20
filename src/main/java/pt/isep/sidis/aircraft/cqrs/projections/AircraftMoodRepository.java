package pt.isep.sidis.aircraft.cqrs.projections;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AircraftMoodRepository extends JpaRepository<AircraftMoodEntity, String> {
}
