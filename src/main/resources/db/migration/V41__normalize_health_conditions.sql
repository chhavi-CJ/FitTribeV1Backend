-- V41: Backfill health_conditions column to canonical values.
-- Existing DB has mixed formats because two frontend paths sent different
-- strings. This migration normalizes every existing row so only canonical
-- enum-name strings remain. Application-layer normalizer ensures no new
-- bad data enters after this deploy.

UPDATE users
SET health_conditions = ARRAY(
  SELECT DISTINCT canonical FROM (
    SELECT CASE
      WHEN LOWER(TRIM(c)) IN ('heart','heart condition','heart_condition') THEN 'HEART_CONDITION'
      WHEN LOWER(TRIM(c)) IN ('diabetes','type 2 diabetes')                THEN 'DIABETES'
      WHEN LOWER(TRIM(c)) IN ('thyroid','thyroid condition')               THEN 'THYROID'
      WHEN LOWER(TRIM(c)) IN ('joints','joint issues','joint issues (knees/hips)','joint_issues') THEN 'JOINT_ISSUES'
      WHEN LOWER(TRIM(c)) IN ('back','back pain','lower back pain','back_pain') THEN 'BACK_PAIN'
      WHEN LOWER(TRIM(c)) IN ('shoulder','shoulder injury','shoulder_injury') THEN 'SHOULDER_INJURY'
      WHEN LOWER(TRIM(c)) = 'asthma'                                       THEN 'ASTHMA'
      WHEN LOWER(TRIM(c)) = 'pcos'                                         THEN 'PCOS'
      WHEN LOWER(TRIM(c)) IN ('postnatal','post-natal','post natal')       THEN 'POSTNATAL'
      WHEN LOWER(TRIM(c)) IN ('pregnancy','pregnant')                      THEN 'PREGNANCY'
      ELSE NULL
    END AS canonical
    FROM unnest(health_conditions) AS c
  ) mapped
  WHERE canonical IS NOT NULL
)
WHERE array_length(health_conditions, 1) > 0;
