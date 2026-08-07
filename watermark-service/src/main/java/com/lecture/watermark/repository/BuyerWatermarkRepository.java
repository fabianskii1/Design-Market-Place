package com.lecture.watermark.repository;

import com.lecture.watermark.entity.BuyerWatermark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BuyerWatermarkRepository extends JpaRepository<BuyerWatermark, Long> {

    Optional<BuyerWatermark> findByCourseIdAndBuyerId(Long courseId, Long buyerId);

    /** 유출본 추적: 페이로드로 역조회 */
    Optional<BuyerWatermark> findByPayload(String payload);
}
