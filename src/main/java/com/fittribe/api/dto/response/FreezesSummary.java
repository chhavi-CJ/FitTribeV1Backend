package com.fittribe.api.dto.response;

import java.util.List;

public record FreezesSummary(
        int purchasedBalance,
        int activeBonusCount,
        int totalAvailable,
        List<BonusGrantSummary> bonusGrants
) {}
