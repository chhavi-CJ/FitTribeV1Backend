package com.fittribe.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class SessionFeedbackRequest {

    @NotBlank(message = "Rating is required")
    @Pattern(
            regexp = "KILLED_ME|HARD|GOOD|TOO_EASY",
            message = "Rating must be KILLED_ME, HARD, GOOD, or TOO_EASY"
    )
    private String rating;

    @Size(max = 200, message = "Notes must be 200 characters or less")
    private String notes;

    public String getRating()              { return rating; }
    public void setRating(String rating)   { this.rating = rating; }

    public String getNotes()           { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
