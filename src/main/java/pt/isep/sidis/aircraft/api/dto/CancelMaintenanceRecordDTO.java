package pt.isep.sidis.aircraft.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CancelMaintenanceRecordDTO {

    @NotBlank(message = "A cancellation reason is mandatory.")
    private String reason;

    @NotNull(message = "The (Optimistic Locking) version is mandatory.")
    private Long version;

    public CancelMaintenanceRecordDTO() {
    }

    public CancelMaintenanceRecordDTO(String reason, Long version) {
        this.reason = reason;
        this.version = version;
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
