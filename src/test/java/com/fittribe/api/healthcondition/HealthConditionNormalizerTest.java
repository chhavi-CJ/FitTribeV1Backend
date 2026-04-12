package com.fittribe.api.healthcondition;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HealthConditionNormalizerTest {

    @Test
    void normalize_handlesShortIdsFromProfileEdit() {
        String[] result = HealthConditionNormalizer.normalize(
            new String[]{"heart", "diabetes", "joints", "back", "shoulder",
                         "asthma", "pcos", "postnatal", "pregnancy", "thyroid"});
        assertArrayEquals(new String[]{
            "HEART_CONDITION","DIABETES","JOINT_ISSUES","BACK_PAIN","SHOULDER_INJURY",
            "ASTHMA","PCOS","POSTNATAL","PREGNANCY","THYROID"
        }, result);
    }

    @Test
    void normalize_handlesFullLabelsFromOnboarding() {
        String[] result = HealthConditionNormalizer.normalize(
            new String[]{"Heart condition", "Diabetes", "Joint issues",
                         "Back pain", "Asthma", "PCOS", "Post-natal",
                         "Pregnancy", "Thyroid"});
        assertArrayEquals(new String[]{
            "HEART_CONDITION","DIABETES","JOINT_ISSUES","BACK_PAIN",
            "ASTHMA","PCOS","POSTNATAL","PREGNANCY","THYROID"
        }, result);
    }

    @Test
    void normalize_isIdempotent_whenGivenCanonicalValues() {
        String[] result = HealthConditionNormalizer.normalize(
            new String[]{"HEART_CONDITION", "BACK_PAIN", "PREGNANCY"});
        assertArrayEquals(new String[]{"HEART_CONDITION","BACK_PAIN","PREGNANCY"}, result);
    }

    @Test
    void normalize_trimsWhitespaceAndIsCaseInsensitive() {
        String[] result = HealthConditionNormalizer.normalize(
            new String[]{"  Heart Condition  ", "BACK pain", "pregnancy"});
        assertArrayEquals(new String[]{"HEART_CONDITION","BACK_PAIN","PREGNANCY"}, result);
    }

    @Test
    void normalize_dropsUnknownValues() {
        String[] result = HealthConditionNormalizer.normalize(
            new String[]{"heart", "made up condition", "joints"});
        assertArrayEquals(new String[]{"HEART_CONDITION","JOINT_ISSUES"}, result);
    }

    @Test
    void normalize_deduplicates() {
        String[] result = HealthConditionNormalizer.normalize(
            new String[]{"heart", "Heart condition", "HEART_CONDITION"});
        assertArrayEquals(new String[]{"HEART_CONDITION"}, result);
    }

    @Test
    void normalize_handlesNullAndEmpty() {
        assertArrayEquals(new String[0], HealthConditionNormalizer.normalize(null));
        assertArrayEquals(new String[0], HealthConditionNormalizer.normalize(new String[0]));
        assertArrayEquals(new String[0],
                HealthConditionNormalizer.normalize(new String[]{null, "", "   "}));
    }

    @Test
    void toShortIds_convertsCanonicalToFrontendIds() {
        List<String> result = HealthConditionNormalizer.toShortIds(
            new String[]{"HEART_CONDITION", "JOINT_ISSUES", "PREGNANCY"});
        assertEquals(List.of("heart", "joints", "pregnancy"), result);
    }

    @Test
    void toShortIds_handlesNullAndUnknowns() {
        assertEquals(List.of(), HealthConditionNormalizer.toShortIds(null));
        assertEquals(List.of("heart"),
                HealthConditionNormalizer.toShortIds(new String[]{"HEART_CONDITION", "garbage"}));
    }
}
