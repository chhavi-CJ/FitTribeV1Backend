package com.fittribe.api.repository;

import com.fittribe.api.entity.SplitTemplateDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SplitTemplateDayRepository extends JpaRepository<SplitTemplateDay, Integer> {

    List<SplitTemplateDay> findByDaysPerWeekAndFitnessLevelOrderByDayNumber(
            Integer daysPerWeek, String fitnessLevel);
}
