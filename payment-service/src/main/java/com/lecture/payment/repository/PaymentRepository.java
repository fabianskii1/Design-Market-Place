package com.lecture.payment.repository;

import com.lecture.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByUserId(Long userId);

    Optional<Payment> findByUserIdAndCourseId(Long userId, Long courseId);

    Optional<Payment> findByTransactionId(String transactionId);

    // 강의별 판매 집계 (COMPLETED 결제만) - course-service 판매 통계용
    @Query(
        "SELECT p.courseId AS courseId, COUNT(p) AS salesCount, SUM(p.amount) AS totalRevenue " +
        "FROM Payment p " +
        "WHERE p.status = com.lecture.payment.entity.Payment.Status.COMPLETED " +
        "AND p.courseId IN :courseIds " +
        "GROUP BY p.courseId"
    )
    List<CourseSalesProjection> findSalesSummaryByCourseIds(@Param("courseIds") List<Long> courseIds);

    interface CourseSalesProjection {
        Long getCourseId();
        Long getSalesCount();
        BigDecimal getTotalRevenue();
    }

    @Query(
        "SELECT p.courseId AS courseId, FUNCTION('DATE_FORMAT', p.createdAt, '%Y-%m') AS yearMonth, " +
        "SUM(p.amount) AS revenue, COUNT(p) AS salesCount " +
        "FROM Payment p " +
        "WHERE p.status = com.lecture.payment.entity.Payment.Status.COMPLETED " +
        "AND p.paymentType = com.lecture.payment.entity.Payment.PaymentType.ENROLLMENT " +
        "AND p.courseId IN :courseIds " +
        "GROUP BY p.courseId, FUNCTION('DATE_FORMAT', p.createdAt, '%Y-%m')"
    )
    List<MonthlySalesProjection> findMonthlySalesByCourseIds(@Param("courseIds") List<Long> courseIds);

    interface MonthlySalesProjection {
        Long getCourseId();
        String getYearMonth();
        BigDecimal getRevenue();
        Long getSalesCount();
    }

    // courseId + licenseTierId 둘 다로 묶는다 — "강의별" 등급 매출이라 courseId가 꼭 있어야 한다
    @Query(
        "SELECT p.courseId AS courseId, p.licenseTierId AS licenseTierId, " +
        "COUNT(p) AS salesCount, SUM(p.amount) AS revenue " +
        "FROM Payment p " +
        "WHERE p.status = com.lecture.payment.entity.Payment.Status.COMPLETED " +
        "AND p.courseId IN :courseIds " +
        "GROUP BY p.courseId, p.licenseTierId"
    )
    List<LicenseTierSalesProjection> findLicenseTierSalesByCourseIds(@Param("courseIds") List<Long> courseIds);

    interface LicenseTierSalesProjection {
        Long getCourseId();
        Long getLicenseTierId();
        Long getSalesCount();
        BigDecimal getRevenue();
    }

}