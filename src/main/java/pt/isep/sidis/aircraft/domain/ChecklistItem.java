package pt.isep.sidis.aircraft.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ChecklistItem {

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private boolean completed;

    public ChecklistItem() {
    }

    public ChecklistItem(String description) {
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Checklist item description cannot be blank.");
        }
        this.description = description.trim();
        this.completed = false;
    }

    public ChecklistItem copy() {
        return new ChecklistItem(this.description);
    }

    public void markDone() {
        if (this.completed) {
            throw new IllegalStateException(
                    "Checklist item '" + description + "' is already marked as completed.");
        }
        this.completed = true;
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
