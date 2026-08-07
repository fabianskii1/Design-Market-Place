package com.lecture.enrollment.service;

import com.lecture.enrollment.entity.Enrollment;
import com.lecture.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentWriteService {

    private final EnrollmentRepository enrollmentRepository;

    /**
     * 반드시 독립 트랜잭션으로 실행
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Enrollment createPendingEnrollment(Long userId, Long courseId, Long licenseTierId, BigDecimal paidAmount) {
        Enrollment enrollment = enrollmentRepository.save(
                Enrollment.builder()
                        .userId(userId)
                        .courseId(courseId)
                        .licenseTierId(licenseTierId)
                        .paidAmount(paidAmount)
                        .build()
        );
        log.info("[EnrollmentWriteService] PENDING enrollment 생성 - id: {}, tier: {}, amount: {}",
                enrollment.getId(), licenseTierId, paidAmount);
        return enrollment;
    }
}