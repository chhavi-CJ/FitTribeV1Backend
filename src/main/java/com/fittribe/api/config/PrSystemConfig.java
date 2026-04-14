package com.fittribe.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Feature flag configuration for PR System V2.
 *
 * <p>The {@code app.pr-system-v2.enabled} property gates all new PR detection
 * and ledger logic. Default is {@code false} (disabled) for MVP safety. Once
 * Phase 2+ code is complete and tested, flip to {@code true} in deployment.
 *
 * <h3>Usage</h3>
 * Inject this configuration bean and call {@link #isPrSystemV2Enabled()} to
 * check the flag before executing any PR System V2 code path.
 */
@Configuration
public class PrSystemConfig {

    private final boolean enabled;

    public PrSystemConfig(@Value("${app.pr-system-v2.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPrSystemV2Enabled() {
        return enabled;
    }
}
