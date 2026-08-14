WITH video_map (slug, vid) AS (
  VALUES
    ('push-up',                          'GHJgsTIW_bQ'),
    ('russian-twist',                    'BA-uP_-bVE8'),
    ('dead-bug',                         '-8xqJ2xXs2A'),
    ('mountain-climbers',                'bTAnnkjzw88'),
    ('plank',                            '5gU3pyrkv-E'),
    ('bicycle-crunches',                 'Or3BkGOTt8Y'),
    ('seated-cable-row',                 'HoWHac5nbLo'),
    ('lat-pulldown',                     'i_En2drerpY'),
    ('dumbbell-row',                     'jpi4reqwiKY'),
    ('face-pulls',                       'IeOqdw9WI90'),
    ('cable-straight-arm-lat-pullover',  'RtL11I6oX48'),
    ('pull-up',                          'ym1V5H35IpA'),
    ('chin-up',                          'Oi3bW9nQmGI'),
    ('barbell-bent-over-row',            'HamNqVyElPc'),
    ('conventional-deadlift',            'rDk1oz5bbMA'),
    ('bench-press',                      'XjrsqShr-Ic'),
    ('incline-press',                    'V3BNe4vJX60'),
    ('dumbbell-press',                   'kzKsy-TtddE'),
    ('chest-dip',                        '5k7Atrwsty0'),
    ('chest-flyes',                      'IZS2RMmqt3E'),
    ('overhead-shoulder-press',          'k6tzKisR3NY'),
    ('lateral-raise',                    'UFcaodmbXd8'),
    ('front-raise',                      '9ThlTL25DH8'),
    ('shrugs',                           'rFsSeClGnNA'),
    ('dumbbell-reverse-flyes',           'LsT-bR_zxLo'),
    ('reverse-flyes',                    '7tgx6QHB0-A'),
    ('dumbbell-curl',                    'j1FjaWu5Am4'),
    ('barbell-curl',                     '9Q7qbqAFw9Y'),
    ('hammer-curl',                      '8H5oWMNWWeQ'),
    ('preacher-curl',                    '7ixqAPO6JvU'),
    ('concentration-curl',               'r4YGqDn8_5w'),
    ('tricep-dip',                       'thx13oPVK5c'),
    ('tricep-pushdown',                  'EPn02tZL68U'),
    ('diamond-push-up',                  'PPTj-MW2tcs'),
    ('tricep-kickback',                  'c7N9gz2uNpA'),
    ('tricep-overhead-extension',        'AYqg9S5FrUU'),
    ('skull-crushers',                   'h4l3cpeNtbE'),
    ('close-grip-bench-press',           '4yKLxOsrGfg'),
    ('barbell-back-squat',               'd0xzaQyotMA'),
    ('front-squat',                      '_qv0m3tPd3s'),
    ('leg-press',                        'ckvkKINPzjg'),
    ('lunge',                            'mJilHWIBWO8'),
    ('bulgarian-split-squat',            'uBSoEWZu07k'),
    ('goblet-squat',                     'lRYBbchqxtI'),
    ('leg-extension',                    'uM86QE59Tgc'),
    ('romanian-deadlift',                '_TchJLlBO-4'),
    ('lying-leg-curl',                   'yjWAuFOjhuY'),
    ('nordic-curl',                      'h5S6KNwATsI'),
    ('sumo-deadlift',                    'uZoLwVBF0c8'),
    ('barbell-hip-thrust',               'PqC0fmyNlmw'),
    ('glute-bridge',                     'ysWdBc1a1FA'),
    ('cable-kickback',                   '1u0CGdZfjJE'),
    ('cable-pull-through',               'LGzE5oN-4hY'),
    ('single-leg-calf-raise',            'Bfl5du8ehao'),
    ('seated-calf-raises',               'NwA1N_EFTtk'),
    ('standing-calf-raises',             'sNqa1ad2qIQ')
)
UPDATE exercises e
SET    demo_video_url = m.vid
FROM   video_map m
WHERE  trim(both '-' from regexp_replace(lower(e.name), '[^a-z0-9]+', '-', 'g')) = m.slug
AND    (e.demo_video_url IS NULL OR e.demo_video_url = '');

-- ── Manual corrections for naming mismatches ──────────────────────────

-- Plurals vs singular
UPDATE exercises SET demo_video_url = 'ym1V5H35IpA' WHERE name = 'Pull Ups' AND demo_video_url IS NULL;
UPDATE exercises SET demo_video_url = 'GHJgsTIW_bQ' WHERE name = 'Push Ups' AND demo_video_url IS NULL;
UPDATE exercises SET demo_video_url = 'UFcaodmbXd8' WHERE name = 'Lateral Raises' AND demo_video_url IS NULL;
UPDATE exercises SET demo_video_url = 'EPn02tZL68U' WHERE name = 'Tricep Pushdowns' AND demo_video_url IS NULL;
UPDATE exercises SET demo_video_url = 'Or3BkGOTt8Y' WHERE name = 'Crunches' AND demo_video_url IS NULL;

-- Singular vs compound names
UPDATE exercises SET demo_video_url = 'k6tzKisR3NY' WHERE name IN ('Overhead Press', 'Shoulder Press') AND demo_video_url IS NULL;
UPDATE exercises SET demo_video_url = 'd0xzaQyotMA' WHERE name = 'Squat' AND demo_video_url IS NULL;
UPDATE exercises SET demo_video_url = 'ckvkKINPzjg' WHERE name = 'Leg Press' AND demo_video_url IS NULL;
UPDATE exercises SET demo_video_url = 'yjWAuFOjhuY' WHERE name = 'Leg Curl' AND demo_video_url IS NULL;
UPDATE exercises SET demo_video_url = 'HamNqVyElPc' WHERE name = 'Barbell Row' AND demo_video_url IS NULL;
UPDATE exercises SET demo_video_url = 'j1FjaWu5Am4' WHERE name = 'Bicep Curl' AND demo_video_url IS NULL;
UPDATE exercises SET demo_video_url = 'IZS2RMmqt3E' WHERE name = 'Cable Flyes' AND demo_video_url IS NULL;
UPDATE exercises SET demo_video_url = 'thx13oPVK5c' WHERE name = 'Dips' AND demo_video_url IS NULL;
UPDATE exercises SET demo_video_url = 'kzKsy-TtddE' WHERE name = 'Dumbbell Flat Press' AND demo_video_url IS NULL;
UPDATE exercises SET demo_video_url = 'V3BNe4vJX60' WHERE name = 'Incline DB Press' AND demo_video_url IS NULL;
UPDATE exercises SET demo_video_url = '9ThlTL25DH8' WHERE name = 'Front Raises' AND demo_video_url IS NULL;

-- DEBUG: show which exercises still have NULL demo_video_url
SELECT name FROM exercises WHERE demo_video_url IS NULL OR demo_video_url = '' ORDER BY name;
