package com.lecture.course.repository;

import com.lecture.course.entity.LicenseTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LicenseTierRepository extends JpaRepository<LicenseTier, Long> {
    List<LicenseTier> findByCourseId(Long courseId);
}