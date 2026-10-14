package pt.isep.sidis.aircraft.api.dto;

import jakarta.validation.constraints.NotNull;

public class VersionedActionDTO {

    @NotNull(message = "The (Optimistic Locking) version is mandatory.")
    private Long version;

    public VersionedActionDTO() {
    }

    public VersionedActionDTO(Long version) {
        this.version = version;
    }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
