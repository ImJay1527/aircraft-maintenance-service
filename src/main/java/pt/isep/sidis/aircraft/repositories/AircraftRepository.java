package pt.isep.sidis.aircraft.repositories;

import org.springframework.data.repository.CrudRepository;
import pt.isep.sidis.aircraft.domain.Aircraft;
import java.util.Optional;

public interface AircraftRepository extends CrudRepository<Aircraft, String> {
    Optional<Aircraft> findByRegistrationNumber(String registrationNumber);
}
