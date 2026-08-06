-- V78: Allow NULL exercises in daily_plan_generated during async generation
-- When status='PENDING', exercises is empty (generated later by async worker)
-- This migration changes exercises from NOT NULL to nullable

ALTER TABLE daily_plan_generated
  ALTER COLUMN exercises DROP NOT NULL;
