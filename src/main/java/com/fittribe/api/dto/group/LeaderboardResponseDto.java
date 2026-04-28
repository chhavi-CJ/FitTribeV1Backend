package com.fittribe.api.dto.group;

import java.util.List;

public class LeaderboardResponseDto {

    private final String                    type;
    private final int                       isoYear;
    private final int                       isoWeek;
    private final List<LeaderboardEntryDto> entries;

    public LeaderboardResponseDto(String type, int isoYear, int isoWeek,
                                  List<LeaderboardEntryDto> entries) {
        this.type    = type;
        this.isoYear = isoYear;
        this.isoWeek = isoWeek;
        this.entries = entries;
    }

    public String                    getType()    { return type; }
    public int                       getIsoYear() { return isoYear; }
    public int                       getIsoWeek() { return isoWeek; }
    public List<LeaderboardEntryDto> getEntries() { return entries; }
}
