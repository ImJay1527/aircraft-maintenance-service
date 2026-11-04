package pt.isep.sidis.aircraft.cqrs.queries;

public record GetAircraftMoodQuery(
    String registration
) {
    public static GetAircraftMoodQuery all() {
        return new GetAircraftMoodQuery(null);
    }

    public static GetAircraftMoodQuery forRegistration(String registration) {
        return new GetAircraftMoodQuery(registration);
    }
}
