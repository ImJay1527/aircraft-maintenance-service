package pt.isep.sidis.aircraft.cqrs.handlers;

import org.springframework.stereotype.Service;
import pt.isep.sidis.aircraft.cqrs.projections.AircraftMoodProjection;
import pt.isep.sidis.aircraft.cqrs.projections.AircraftMoodView;
import pt.isep.sidis.aircraft.cqrs.queries.GetAircraftMoodQuery;

import java.util.List;
import java.util.Optional;

@Service
public class AircraftQueryHandler {

    private final AircraftMoodProjection moodProjection;

    public AircraftQueryHandler(AircraftMoodProjection moodProjection) {
        this.moodProjection = moodProjection;
    }

    public List<AircraftMoodView> handle(GetAircraftMoodQuery query) {
        if (query.registration() == null || query.registration().isBlank()) {
            return moodProjection.findAll();
        }
        Optional<AircraftMoodView> view = moodProjection.findByRegistration(query.registration().toUpperCase());
        return view.map(List::of).orElseGet(List::of);
    }
}
