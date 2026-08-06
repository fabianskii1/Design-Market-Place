package com.lecture.course.repository;

import com.lecture.course.entity.DesignAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DesignAssetRepository extends JpaRepository<DesignAsset, Long> {

    Optional<DesignAsset> findByCourseId(Long courseId);

    Optional<DesignAsset> findByAssetToken(String assetToken);

    boolean existsByCourseId(Long courseId);

    /**
     * 메타데이터만 필요한 화면에서 LONGBLOB 두 개를 함께 읽어오면
     * 수십 MB가 힙에 올라온다. 바이너리는 아래 전용 쿼리로만 조회한다.
     */
    @Query("select a.previewData from DesignAsset a where a.courseId = :courseId")
    Optional<byte[]> findPreviewData(@Param("courseId") Long courseId);

    @Query("select a.watermarkedData from DesignAsset a where a.courseId = :courseId")
    Optional<byte[]> findWatermarkedData(@Param("courseId") Long courseId);
}
