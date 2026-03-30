package com.fittribe.api.repository;

import com.fittribe.api.entity.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, String> {

    List<Exercise> findAllByOrderByMuscleGroupAsc();

    @Query("SELECT e FROM Exercise e WHERE UPPER(e.muscleGroup) = UPPER(:muscleGroup) AND e.id != :id")
    List<Exercise> findByMuscleGroupAndIdNot(@Param("muscleGroup") String muscleGroup,
                                              @Param("id") String id);
}
