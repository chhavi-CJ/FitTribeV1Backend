package com.fittribe.api.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "split_template_days")
public class SplitTemplateDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "days_per_week", nullable = false)
    private Integer daysPerWeek;

    @Column(name = "fitness_level", nullable = false)
    private String fitnessLevel;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(name = "day_label", nullable = false)
    private String dayLabel;

    @Column(name = "day_type", nullable = false)
    private String dayType;

    @Column(name = "muscle_groups", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] muscleGroups;

    @Column(name = "includes_core", nullable = false)
    private boolean includesCore;

    @Column(name = "guidance_text")
    private String guidanceText;

    @Column(name = "cardio_type")
    private String cardioType;

    @Column(name = "cardio_duration_min")
    private Integer cardioDurationMin;

    @Column(name = "estimated_mins", nullable = false)
    private Integer estimatedMins;

    public SplitTemplateDay() {}

    public Integer getId()                { return id; }
    public Integer getDaysPerWeek()       { return daysPerWeek; }
    public String  getFitnessLevel()      { return fitnessLevel; }
    public Integer getDayNumber()         { return dayNumber; }
    public String  getDayLabel()          { return dayLabel; }
    public String  getDayType()           { return dayType; }
    public String[] getMuscleGroups()     { return muscleGroups; }
    public boolean isIncludesCore()       { return includesCore; }
    public String  getGuidanceText()      { return guidanceText; }
    public String  getCardioType()        { return cardioType; }
    public Integer getCardioDurationMin() { return cardioDurationMin; }
    public Integer getEstimatedMins()     { return estimatedMins; }
}
