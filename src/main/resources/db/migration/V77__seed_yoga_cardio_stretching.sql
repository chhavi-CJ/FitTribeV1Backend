-- V77: seed yoga, cardio, stretching, and breathing exercises

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight) VALUES
-- YOGA (equipment = 'BODYWEIGHT', is_bodyweight = true)
  ('sun-salutation',       'Sun Salutation',        'Yoga',       'BODYWEIGHT',  '🧘', true),
  ('warrior-i',            'Warrior I',             'Yoga',       'BODYWEIGHT',  '🧘', true),
  ('warrior-ii',           'Warrior II',            'Yoga',       'BODYWEIGHT',  '🧘', true),
  ('warrior-iii',          'Warrior III',           'Yoga',       'BODYWEIGHT',  '🧘', true),
  ('downward-dog',         'Downward Dog',          'Yoga',       'BODYWEIGHT',  '🧘', true),
  ('cobra-pose',           'Cobra Pose',            'Yoga',       'BODYWEIGHT',  '🧘', true),
  ('tree-pose',            'Tree Pose',             'Yoga',       'BODYWEIGHT',  '🧘', true),
  ('triangle-pose',        'Triangle Pose',         'Yoga',       'BODYWEIGHT',  '🧘', true),
  ('childs-pose',          'Child''s Pose',         'Yoga',       'BODYWEIGHT',  '🧘', true),
  ('pigeon-pose',          'Pigeon Pose',           'Yoga',       'BODYWEIGHT',  '🧘', true),
  ('cat-cow-stretch',      'Cat-Cow Stretch',       'Yoga',       'BODYWEIGHT',  '🧘', true),
  ('bridge-pose',          'Bridge Pose',           'Yoga',       'BODYWEIGHT',  '🧘', true),
  ('plank-pose',           'Plank Pose',            'Yoga',       'BODYWEIGHT',  '🧘', true),
  ('chair-pose',           'Chair Pose',            'Yoga',       'BODYWEIGHT',  '🧘', true),
  ('camel-pose',           'Camel Pose',            'Yoga',       'BODYWEIGHT',  '🧘', true),

-- CARDIO
  ('incline-walk',         'Incline Walk',          'Cardio',     'TREADMILL',   '🏃', false),
  ('stair-climber',        'Stair Climber',         'Cardio',     'MACHINE',     '🏃', false),
  ('stationary-cycling',   'Stationary Cycling',    'Cardio',     'MACHINE',     '🏃', false),
  ('jump-rope',            'Jump Rope',             'Cardio',     'BODYWEIGHT',  '🏃', true),
  ('rowing-machine',       'Rowing Machine',        'Cardio',     'MACHINE',     '🏃', false),
  ('elliptical',           'Elliptical',            'Cardio',     'MACHINE',     '🏃', false),
  ('box-jumps',            'Box Jumps',             'Cardio',     'BODYWEIGHT',  '🏃', true),
  ('battle-ropes',         'Battle Ropes',          'Cardio',     'OTHER',       '🏃', false),
  ('burpees',              'Burpees',               'Cardio',     'BODYWEIGHT',  '🏃', true),
  ('mountain-climbers',    'Mountain Climbers',     'Cardio',     'BODYWEIGHT',  '🏃', true),
  ('high-knees',           'High Knees',            'Cardio',     'BODYWEIGHT',  '🏃', true),
  ('jumping-jacks',        'Jumping Jacks',         'Cardio',     'BODYWEIGHT',  '🏃', true),
  ('sprints',              'Sprints',               'Cardio',     'BODYWEIGHT',  '🏃', true),

-- STRETCHING (equipment = 'BODYWEIGHT', is_bodyweight = true)
  ('foam-rolling',         'Foam Rolling',          'Stretching', 'BODYWEIGHT',  '🧘', true),
  ('hip-flexor-stretch',   'Hip Flexor Stretch',    'Stretching', 'BODYWEIGHT',  '🧘', true),
  ('hamstring-stretch',    'Hamstring Stretch',     'Stretching', 'BODYWEIGHT',  '🧘', true),
  ('quad-stretch',         'Quad Stretch',          'Stretching', 'BODYWEIGHT',  '🧘', true),
  ('shoulder-stretch',     'Shoulder Stretch',      'Stretching', 'BODYWEIGHT',  '🧘', true),
  ('chest-opener-stretch', 'Chest Opener Stretch',  'Stretching', 'BODYWEIGHT',  '🧘', true),
  ('seated-spinal-twist',  'Seated Spinal Twist',   'Stretching', 'BODYWEIGHT',  '🧘', true),
  ('standing-calf-stretch','Standing Calf Stretch', 'Stretching', 'BODYWEIGHT',  '🧘', true),
  ('neck-rolls',           'Neck Rolls',            'Stretching', 'BODYWEIGHT',  '🧘', true),
  ('lat-stretch',          'Lat Stretch',           'Stretching', 'BODYWEIGHT',  '🧘', true),
  ('glute-stretch',        'Glute Stretch',         'Stretching', 'BODYWEIGHT',  '🧘', true),
  ('it-band-stretch',      'IT Band Stretch',       'Stretching', 'BODYWEIGHT',  '🧘', true),
  ('ankle-mobility-circles','Ankle Mobility Circles','Stretching', 'BODYWEIGHT',  '🧘', true),

-- BREATHING (equipment = 'BODYWEIGHT', is_bodyweight = true)
  ('box-breathing',        'Box Breathing',         'Breathing',  'BODYWEIGHT',  '🫁', true),
  ('deep-belly-breathing', 'Deep Belly Breathing',  'Breathing',  'BODYWEIGHT',  '🫁', true),
  ('alternate-nostril-breathing','Alternate Nostril Breathing','Breathing','BODYWEIGHT','🫁',true),
  ('4-7-8-breathing',      '4-7-8 Breathing Technique','Breathing','BODYWEIGHT','🫁',true),
  ('body-scan-meditation', 'Body Scan Meditation',  'Breathing',  'BODYWEIGHT',  '🫁', true),
  ('guided-savasana',      'Guided Savasana',       'Breathing',  'BODYWEIGHT',  '🫁', true)
ON CONFLICT DO NOTHING;
