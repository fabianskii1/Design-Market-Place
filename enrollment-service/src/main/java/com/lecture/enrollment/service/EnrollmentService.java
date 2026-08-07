package com.lecture.enrollment.service;

import com.lecture.enrollment.dto.EnrollmentDto;
import com.lecture.enrollment.entity.Enrollment;
import com.lecture.enrollment.kafka.EnrollmentKafkaProducer;
import com.lecture.enrollment.kafka.KafkaEvent;
import com.lecture.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseServiceClient courseServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final EnrollmentKafkaProducer kafkaProducer;
    private final EnrollmentWriteService enrollmentWriteService;
    private final SubscriptionServiceClient subscriptionServiceClient;

    /**
     * 수강신청 전체 흐름
     * 1. 강의 존재 확인
     * 2. 중복 수강 확인
     * 3. 라이선스 등급 가격 조회 + 구독 할인 적용
     * 4. Enrollment 생성 및 즉시 커밋 (PENDING)
     * 5. 결제 요청
     */
    public EnrollmentDto.EnrollmentResponse enroll(Long userId, Long courseId, Long licenseTierId) {
        if (!courseServiceClient.existsCourse(courseId)) {
            throw new IllegalArgumentException("존재하지 않는 디자인입니다: " + courseId);
        }
        if (enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
            throw new IllegalArgumentException("이미 구매한 디자인입니다");
        }

        CourseServiceClient.LicenseTierInfo tier = courseServiceClient.getLicenseTier(courseId, licenseTierId);
        Map<String, Object> course = courseServiceClient.getCourse(courseId);
        Long instructorId = toLong(course.get("instructorId"));

        BigDecimal finalPrice = subscriptionServiceClient.applyDiscountIfSubscribed(
                userId, instructorId, tier.getPrice());

        Enrollment enrollment = enrollmentWriteService.createPendingEnrollment(
                userId, courseId, licenseTierId, finalPrice);

        paymentServiceClient.requestPayment(userId, courseId, licenseTierId, finalPrice);

        log.info("[EnrollmentService] 구매 신청 완료 - enrollmentId: {}, tier: {}, amount: {}",
                enrollment.getId(), tier.getTier(), finalPrice);
        return EnrollmentDto.EnrollmentResponse.from(enrollment);
    }

    /**
     * 수강 활성화
     */
    @Transactional
    public void activateEnrollment(Long userId, Long courseId) {
        Enrollment enrollment = enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "수강 정보를 찾을 수 없습니다 - userId: " + userId + ", courseId: " + courseId));

        enrollment.activate();

        courseServiceClient.increaseEnrollmentCount(courseId);

        kafkaProducer.publishEnrollmentCompleted(
                KafkaEvent.EnrollmentCompletedEvent.builder()
                        .enrollmentId(enrollment.getId())
                        .userId(userId)
                        .courseId(courseId)
                        .build()
        );

        // 구매 확정 → 구매자별 워터마크 사본 생성 요청
        kafkaProducer.publishPurchaseCompleted(
                KafkaEvent.PurchaseCompletedEvent.builder()
                        .purchaseId(enrollment.getId())
                        .buyerId(userId)
                        .courseId(courseId)
                        .occurredAt(java.time.Instant.now().getEpochSecond())
                        .build()
        );

        log.info("[EnrollmentService] 수강 활성화 완료 - enrollmentId: {}", enrollment.getId());
    }

    /**
     * 사용자 수강 목록 조회
     * - course-service에서 강의 상세 정보를 붙여서 반환
     */
    public List<EnrollmentDto.EnrollmentResponse> getEnrollmentsByUser(Long userId) {
        List<Enrollment> enrollments = enrollmentRepository.findByUserId(userId);

        return enrollments.stream()
                .map(enrollment -> {
                    Map<String, Object> courseInfo = courseServiceClient.getCourse(enrollment.getCourseId());

                    EnrollmentDto.CourseSummary courseSummary = EnrollmentDto.CourseSummary.builder()
                            .id(toLong(courseInfo.get("id")))
                            .title((String) courseInfo.get("title"))
                            .description((String) courseInfo.get("description"))
                            .category(normalizeCategory((String) courseInfo.get("category")))
                            .price(toInteger(courseInfo.get("price")))
                            .thumbnail((String) courseInfo.get("thumbnail"))
                            .instructorName(
                                    firstNonNull(
                                            (String) courseInfo.get("instructorName"),
                                            (String) courseInfo.get("teacherName"),
                                            (String) courseInfo.get("instructor_name")
                                    )
                            )
                            .enrollmentCount(toInteger(
                                    firstNonNullObject(
                                            courseInfo.get("enrollmentCount"),
                                            courseInfo.get("enrollment_count")
                                    )
                            ))
                            .build();

                    return EnrollmentDto.EnrollmentResponse.from(enrollment, courseSummary);
                })
                .collect(Collectors.toList());
    }

    /**
     * 수강 이력 조회 - 추천 서비스용
     */
    public EnrollmentDto.EnrollmentHistoryResponse getEnrollmentHistory(Long userId) {
        List<Long> activeCourseIds = enrollmentRepository
                .findByUserIdAndStatus(userId, Enrollment.Status.ACTIVE)
                .stream()
                .map(Enrollment::getCourseId)
                .collect(Collectors.toList());

        return EnrollmentDto.EnrollmentHistoryResponse.builder()
                .userId(userId)
                .activeCourseIds(activeCourseIds)
                .build();
    }

    private String normalizeCategory(String category) {
        if (category == null) return null;

        return switch (category) {
            case "BACKEND" -> "백엔드";
            case "FRONTEND" -> "프론트엔드";
            case "DEVOPS" -> "DevOps";
            case "DATA" -> "데이터";
            case "AI" -> "AI";
            default -> category;
        };
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(value.toString());
    }

    private String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Object firstNonNullObject(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}