-- V52: Fix glute-bridge equipment tagging.
--
-- glute-bridge was previously tagged BODYWEIGHT / is_bodyweight=true, but in
-- real gym practice it is almost always performed with a dumbbell or plate
-- resting on the hips — even beginners typically start at 2.5kg+.
-- Retagging as DUMBBELL (is_bodyweight=false) since that is the most
-- accessible loaded variant. Users who want heavier loading can swap to
-- hip-thrust (BARBELL), which already exists in the catalog.

UPDATE exercises
SET equipment     = 'DUMBBELL',
    is_bodyweight = false
WHERE id = 'glute-bridge';
