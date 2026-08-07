package com.lecture.course.repository;

import com.lecture.course.entity.LicenseTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LicenseTierRepository extends JpaRepository<LicenseTier, Long> {
    List<LicenseTier> findByCourseId(Long courseId);

    Optional<LicenseTier> findByCourseIdAndTier(Long courseId, LicenseTier.Tier tier);
}

