package com.fittribe.api.scheduler;

import com.fittribe.api.repository.DeviceTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Monitors device_tokens table for emptiness or near-emptiness.
 * Logs a WARN if count is below threshold so a silent data loss is never missed.
 */
@Component
public class DeviceTokenHealthScheduler {

  private static final Logger log = LoggerFactory.getLogger(DeviceTokenHealthScheduler.class);
  private static final long WARN_THRESHOLD = 10; // Warn if fewer than 10 tokens across all users

  private final DeviceTokenRepository deviceTokenRepo;

  public DeviceTokenHealthScheduler(DeviceTokenRepository deviceTokenRepo) {
    this.deviceTokenRepo = deviceTokenRepo;
  }

  /**
   * Daily health check on device_tokens table count.
   * Runs at 1 AM IST. Logs a WARN if count is suspiciously low.
   */
  @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Kolkata")
  public void checkDeviceTokenHealth() {
    try {
      long count = deviceTokenRepo.count();
      if (count < WARN_THRESHOLD) {
        log.warn("Device tokens health check: only {} tokens in device_tokens table. " +
                "If this is unexpected, check if user cleanup is deleting tokens or if clients are not registering.",
                count);
      } else {
        log.info("Device tokens health check: {} tokens registered", count);
      }
    } catch (Exception e) {
      log.error("Device token health check failed", e);
    }
  }
}
