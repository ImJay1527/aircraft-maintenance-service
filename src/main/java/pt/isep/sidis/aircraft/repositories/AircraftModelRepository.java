package pt.isep.sidis.aircraft.repositories;

import org.springframework.data.repository.CrudRepository;
import pt.isep.sidis.aircraft.domain.AircraftModel;
import java.util.Optional;

public interface AircraftModelRepository extends CrudRepository<AircraftModel, Long> {
    Optional<AircraftModel> findByName(String name);
}
