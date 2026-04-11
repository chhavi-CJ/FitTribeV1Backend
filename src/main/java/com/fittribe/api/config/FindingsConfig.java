package com.fittribe.api.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed view of {@code src/main/resources/findings-templates.yml}.
 *
 * Backing store for the weekly findings engine (Wynners A1.3 / A2.1). The
 * YAML file holds all user-facing copy for the 8 finding templates plus the
 * 3 fallback green findings — editing copy does not require a Java change,
 * only a redeploy.
 *
 * Binding uses Spring's relaxed-binding rules, so YAML keys like
 * {@code week_one_eligible} map to Java field {@code weekOneEligible} and
 * {@code fallback_greens} maps to {@code fallbackGreens}.
 *
 * Rule classes (A2.1) look up their template by rule ID:
 * <pre>
 *   FindingTemplate t = findingsConfig.getTemplates().get("pr_regression");
 * </pre>
 *
 * The @PostConstruct hook logs load counts so boot-time integration issues
 * show up in the startup log rather than silently binding to empty.
 */
@Component
@PropertySource(
        value = "classpath:findings-templates.yml",
        factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "fittribe.findings")
public class FindingsConfig {

    private static final Logger log = LoggerFactory.getLogger(FindingsConfig.class);

    /** Keyed by rule ID (e.g. "pr_regression", "missing_muscle"). */
    private Map<String, FindingTemplate> templates = new LinkedHashMap<>();

    /** Ordered list; the first one whose {@code requires} expression evaluates true wins. */
    private List<FallbackGreen> fallbackGreens = new ArrayList<>();

    public Map<String, FindingTemplate> getTemplates() {
        return templates;
    }

    public void setTemplates(Map<String, FindingTemplate> templates) {
        this.templates = templates;
    }

    public List<FallbackGreen> getFallbackGreens() {
        return fallbackGreens;
    }

    public void setFallbackGreens(List<FallbackGreen> fallbackGreens) {
        this.fallbackGreens = fallbackGreens;
    }

    @PostConstruct
    public void logLoaded() {
        log.info("FindingsConfig loaded: {} templates, {} fallback_greens",
                templates.size(), fallbackGreens.size());
    }

    // ── Nested types ─────────────────────────────────────────────────────

    /**
     * Maps to one entry under {@code fittribe.findings.templates.*}.
     * Fields match the v1.0 A1.3 YAML schema; Spring relaxed binding handles
     * the snake_case → camelCase translation automatically.
     */
    public static class FindingTemplate {
        /** RED / AMBER / GREEN. */
        private String severity;
        /** Priority weight (higher = more important). Used when filtering to top-N findings. */
        private int weight;
        /** Template string with {placeholder} tokens — rule fills in at eval time. */
        private String title;
        /** Primary detail copy. */
        private String detail;
        /** Optional alternate detail; only {@code multi_pr_week} uses this today. */
        private String detailAlt;
        /** If false, the rule is suppressed during the user's first week (no prior data). */
        private boolean weekOneEligible;

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }

        public int getWeight() { return weight; }
        public void setWeight(int weight) { this.weight = weight; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDetail() { return detail; }
        public void setDetail(String detail) { this.detail = detail; }

        public String getDetailAlt() { return detailAlt; }
        public void setDetailAlt(String detailAlt) { this.detailAlt = detailAlt; }

        public boolean isWeekOneEligible() { return weekOneEligible; }
        public void setWeekOneEligible(boolean weekOneEligible) { this.weekOneEligible = weekOneEligible; }
    }

    /**
     * Maps to one entry under {@code fittribe.findings.fallback_greens[*]}.
     * Used when no rule fires but we still want a green finding on the report.
     */
    public static class FallbackGreen {
        private String title;
        private String detail;
        /** Simple expression evaluated by the fallback picker — e.g. "sessions >= 1", "always". */
        private String requires;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDetail() { return detail; }
        public void setDetail(String detail) { this.detail = detail; }

        public String getRequires() { return requires; }
        public void setRequires(String requires) { this.requires = requires; }
    }
}
