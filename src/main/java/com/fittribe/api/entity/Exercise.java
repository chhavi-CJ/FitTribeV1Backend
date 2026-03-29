package com.fittribe.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "exercises")
public class Exercise {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "muscle_group")
    private String muscleGroup;

    @Column(name = "equipment")
    private String equipment;

    @Column(name = "icon", length = 10)
    private String icon;

    // ── AI Phase 2 fields (V8 + V9 migrations) ────────────────────────
    @Column(name = "demo_video_url")
    private String demoVideoUrl;

    @Column(name = "muscle_diagram_url")
    private String muscleDiagramUrl;

    @Column(name = "steps", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] steps;

    @Column(name = "common_mistakes", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] commonMistakes;

    @Column(name = "coach_tip")
    private String coachTip;

    @Column(name = "secondary_muscles", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] secondaryMuscles;

    @Column(name = "swap_alternatives", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] swapAlternatives;

    // ── Required by JPA ──────────────────────────────────────────────
    public Exercise() {}

    // ── Getters ───────────────────────────────────────────────────────
    public String getId()               { return id; }
    public String getName()             { return name; }
    public String getMuscleGroup()      { return muscleGroup; }
    public String getEquipment()        { return equipment; }
    public String getIcon()             { return icon; }
    public String getDemoVideoUrl()     { return demoVideoUrl; }
    public String getMuscleDiagramUrl() { return muscleDiagramUrl; }
    public String[] getSteps()          { return steps; }
    public String[] getCommonMistakes() { return commonMistakes; }
    public String getCoachTip()         { return coachTip; }
    public String[] getSecondaryMuscles(){ return secondaryMuscles; }
    public String[] getSwapAlternatives(){ return swapAlternatives; }
}
