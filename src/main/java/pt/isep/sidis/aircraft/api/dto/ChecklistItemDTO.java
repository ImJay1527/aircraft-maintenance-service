package pt.isep.sidis.aircraft.api.dto;

import pt.isep.sidis.aircraft.domain.ChecklistItem;

public class ChecklistItemDTO {

    private String description;
    private boolean completed;

    public ChecklistItemDTO() {
    }

    public ChecklistItemDTO(String description, boolean completed) {
        this.description = description;
        this.completed = completed;
    }

    public ChecklistItemDTO(ChecklistItem item) {
        this.description = item.getDescription();
        this.completed = item.isCompleted();
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
