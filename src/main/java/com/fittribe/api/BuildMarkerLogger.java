package com.fittribe.api;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TEMPORARY — debug instrumentation for verifying which build Railway is
 * actually running. Logs a unique marker on application startup. After the
 * isPr trophies bug is resolved, REMOVE this file in a cleanup commit.
 *
 * <p>To verify a deploy: search Railway logs for the marker string. If
 * present, this build (parent commit 10e3d49 + debug instrumentation) is
 * live. If absent, an older build is still active.
 */
@Component
public class BuildMarkerLogger {

    private static final Logger log = LoggerFactory.getLogger(BuildMarkerLogger.class);

    @PostConstruct
    public void logBuildMarker() {
        log.info("FitTribe boot — DEBUG BUILD parent=10e3d49 marker=isPr-trophies-debug");
    }
}
