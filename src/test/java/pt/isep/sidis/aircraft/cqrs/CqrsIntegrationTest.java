package pt.isep.sidis.aircraft.cqrs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pt.isep.sidis.aircraft.cqrs.commands.*;
import pt.isep.sidis.aircraft.cqrs.handlers.AircraftCommandHandler;
import pt.isep.sidis.aircraft.cqrs.handlers.AircraftQueryHandler;
import pt.isep.sidis.aircraft.cqrs.handlers.MaintenanceCommandHandler;
import pt.isep.sidis.aircraft.cqrs.projections.AircraftMoodView;
import pt.isep.sidis.aircraft.cqrs.queries.GetAircraftMoodQuery;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.domain.AircraftStatus;
import pt.isep.sidis.aircraft.domain.MaintenanceRecord;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class CqrsIntegrationTest {

    @Autowired
    private AircraftCommandHandler aircraftCommandHandler;

    @Autowired
    private MaintenanceCommandHandler maintenanceCommandHandler;

    @Autowired
    private AircraftQueryHandler aircraftQueryHandler;

    @Test
    void shouldExecuteRegisterAircraftCommandAndProjectMood() {
        String reg = "CS-CQ1";
        RegisterAircraftCommand cmd = new RegisterAircraftCommand(reg, "SN-9999", "A320neo");

        Aircraft created = aircraftCommandHandler.handle(cmd);
        assertThat(created).isNotNull();
        assertThat(created.getRegistrationNumber()).isEqualTo(reg);

        List<AircraftMoodView> views = aircraftQueryHandler.handle(GetAircraftMoodQuery.forRegistration(reg));
        assertThat(views).hasSize(1);
        assertThat(views.get(0).mood()).isEqualTo("HAPPY");
        assertThat(views.get(0).energyLevel()).isGreaterThan(80);
    }

    @Test
    void shouldExecuteScheduleMaintenanceCommandAndTransitionStatus() {
        String reg = "CS-CQ2";
        aircraftCommandHandler.handle(new RegisterAircraftCommand(reg, "SN-8888", "A320neo"));

        ScheduleMaintenanceCommand scheduleCmd = new ScheduleMaintenanceCommand(
                reg, 1L, "CQRS Test Maintenance", LocalDate.now(), 5.0, "ENGINE", 2500.0
        );

        MaintenanceRecord record = maintenanceCommandHandler.handle(scheduleCmd);
        assertThat(record).isNotNull();
        assertThat(record.getAircraft().getStatus()).isEqualTo(AircraftStatus.UNDER_MAINTENANCE);

        List<AircraftMoodView> views = aircraftQueryHandler.handle(GetAircraftMoodQuery.forRegistration(reg));
        assertThat(views).hasSize(1);
        assertThat(views.get(0).mood()).isEqualTo("SLEEPY");
    }

    @Test
    void shouldExecuteCompleteMaintenanceCommandAndRejuvenateMood() {
        String reg = "CS-CQ3";
        aircraftCommandHandler.handle(new RegisterAircraftCommand(reg, "SN-7777", "A320neo"));

        MaintenanceRecord record = maintenanceCommandHandler.handle(new ScheduleMaintenanceCommand(
                reg, 1L, "CQRS Test Overhaul", LocalDate.now(), 4.0, "AVIONICS", 1800.0
        ));

        record = maintenanceCommandHandler.handle(new StartMaintenanceCommand(
                record.getId(), record.getVersion()
        ));

        MaintenanceRecord completed = maintenanceCommandHandler.handle(new CompleteMaintenanceCommand(
                record.getId(), record.getVersion(), "Overhaul finished flawlessly", 4.0, 1800.0
        ));

        assertThat(completed.getAircraft().getStatus()).isEqualTo(AircraftStatus.AVAILABLE);

        List<AircraftMoodView> views = aircraftQueryHandler.handle(GetAircraftMoodQuery.forRegistration(reg));
        assertThat(views).hasSize(1);
        assertThat(views.get(0).mood()).isEqualTo("REJUVENATED");
        assertThat(views.get(0).energyLevel()).isEqualTo(100);
        assertThat(views.get(0).sparkleFactor()).isEqualTo(5.0);
    }
}
