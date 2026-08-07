package com.lecture.payment.service;

import com.lecture.payment.dto.PaymentDto;
import com.lecture.payment.entity.Payment;
import com.lecture.payment.kafka.PaymentKafkaProducer;
import com.lecture.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentKafkaProducer kafkaProducer;

    /**
     * 내부 결제 요청 (Enrollment Service → Payment Service REST 호출)
     * 실습 환경에서는 PG 연동 없이 항상 성공으로 처리
     *
     * 처리 흐름:
     * 1. Payment 생성 (PENDING)
     * 2. PG 결제 처리 (실습: UUID 트랜잭션 ID 발급으로 대체)
     * 3. Payment 상태 → COMPLETED
     * 4. payment.completed 이벤트 발행 → Kafka
     */
    @Transactional
    public PaymentDto.InternalPaymentResult processInternalPayment(
            PaymentDto.InternalPaymentRequest request) {

        log.info("[PaymentService] 결제 요청 - userId: {}, courseId: {}, amount: {}",
                request.getUserId(), request.getCourseId(), request.getAmount());

        Payment payment = paymentRepository.save(
                Payment.builder()
                        .userId(request.getUserId())
                        .courseId(request.getCourseId())
                        .amount(request.getAmount())
                        .licenseTierId(request.getLicenseTierId())
                        .paymentType(request.getPaymentType() != null
                                ? Payment.PaymentType.valueOf(request.getPaymentType())
                                : Payment.PaymentType.ENROLLMENT)
                        .build()
        );

        try {
            String transactionId = UUID.randomUUID().toString();

            payment.complete(transactionId);
            log.info("[PaymentService] 결제 완료 처리 - paymentId: {}, transactionId: {}",
                    payment.getId(), transactionId);

            kafkaProducer.publishPaymentCompleted(
                    PaymentKafkaProducer.PaymentCompletedEvent.builder()
                            .paymentId(payment.getId())
                            .userId(request.getUserId())
                            .courseId(request.getCourseId())
                            .status("COMPLETED")
                            .build()
            );

            log.info("[PaymentService] 결제 최종 성공 - paymentId: {}", payment.getId());

            return PaymentDto.InternalPaymentResult.builder()
                    .paymentId(payment.getId())
                    .status("COMPLETED")
                    .build();

        } catch (Exception e) {
            payment.fail();

            log.error("[PaymentService] 결제 실패 - paymentId: {}, userId: {}, courseId: {}, error: {}",
                    payment.getId(),
                    request.getUserId(),
                    request.getCourseId(),
                    e.getMessage(),
                    e);

            return PaymentDto.InternalPaymentResult.builder()
                    .paymentId(payment.getId())
                    .status("FAILED")
                    .build();
        }
    }

    /**
     * 결제 단건 조회
     */
    public PaymentDto.PaymentResponse getPayment(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다: " + id));
        return PaymentDto.PaymentResponse.from(payment);
    }

    /**
     * 사용자 결제 내역 조회
     */
    public List<PaymentDto.PaymentResponse> getPaymentsByUser(Long userId) {
        return paymentRepository.findByUserId(userId).stream()
                .map(PaymentDto.PaymentResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 강의 ID 목록으로 판매 집계 조회 (course-service 판매 통계용, internal)
     */
    public List<PaymentDto.CourseSalesSummary> getSalesSummary(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        return paymentRepository.findSalesSummaryByCourseIds(courseIds).stream()
                .map(p -> PaymentDto.CourseSalesSummary.builder()
                        .courseId(p.getCourseId())
                        .salesCount(p.getSalesCount())
                        .totalRevenue(p.getTotalRevenue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 강의별 월별 매출 (course-service 판매 대시보드 - monthlyBreakdown용, internal)
     */
    public List<PaymentDto.MonthlySales> getMonthlySales(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        return paymentRepository.findMonthlySalesByCourseIds(courseIds).stream()
                .map(p -> PaymentDto.MonthlySales.builder()
                        .courseId(p.getCourseId())
                        .yearMonth(p.getYearMonth())
                        .revenue(p.getRevenue())
                        .salesCount(p.getSalesCount())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 강의별 · 라이선스 등급별 판매 집계 (course-service 판매 대시보드 - salesByLicense용, internal)
     */
    public List<PaymentDto.LicenseTierSales> getLicenseTierSales(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        return paymentRepository.findLicenseTierSalesByCourseIds(courseIds).stream()
                .map(p -> PaymentDto.LicenseTierSales.builder()
                        .courseId(p.getCourseId())
                        .licenseTierId(p.getLicenseTierId())
                        .salesCount(p.getSalesCount())
                        .revenue(p.getRevenue())
                        .build())
                .collect(Collectors.toList());
    }
}