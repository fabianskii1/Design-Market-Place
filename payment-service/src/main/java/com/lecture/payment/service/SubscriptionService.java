package com.lecture.payment.service;

import com.lecture.payment.dto.SubscriptionDto;
import com.lecture.payment.entity.Payment;
import com.lecture.payment.entity.Subscription;
import com.lecture.payment.repository.PaymentRepository;
import com.lecture.payment.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;

    @Value("${subscription.monthly-price:9900}")
    private BigDecimal monthlyPrice;

    // 일괄 정액 할인율. DB 컬럼이 아니라 백엔드 상수(설정값)로 관리 →
    // 정책이 바뀌어도 이 값 하나만 고치면 전체 디자이너/전체 등급에 즉시 반영된다.
    @Value("${subscription.discount-rate:0.30}")
    private BigDecimal discountRate;

    @Transactional
    public SubscriptionDto.SubscriptionResponse subscribe(Long userId, Long designerId) {
        if (userId.equals(designerId)) {
            throw new IllegalArgumentException("본인을 구독할 수 없습니다.");
        }
        subscriptionRepository.findByUserIdAndDesignerId(userId, designerId)
                .filter(Subscription::isBenefitActive)
                .ifPresent(s -> { throw new IllegalArgumentException("이미 구독 중인 디자이너입니다."); });

        Payment payment = paymentRepository.save(
                Payment.builder()
                        .userId(userId)
                        .amount(monthlyPrice)
                        .paymentType(Payment.PaymentType.SUBSCRIPTION)
                        .build()
        );
        payment.complete(UUID.randomUUID().toString());

        Subscription subscription = subscriptionRepository.save(
                Subscription.start(userId, designerId, monthlyPrice));

        return SubscriptionDto.SubscriptionResponse.from(subscription);
    }

    @Transactional
    public void cancel(Long userId, Long designerId) {
        Subscription subscription = subscriptionRepository.findByUserIdAndDesignerId(userId, designerId)
                .orElseThrow(() -> new IllegalArgumentException("구독 정보를 찾을 수 없습니다."));
        subscription.cancel();
    }

    public List<SubscriptionDto.SubscriptionResponse> getMySubscriptions(Long userId) {
        return subscriptionRepository.findByUserId(userId).stream()
                .map(SubscriptionDto.SubscriptionResponse::from)
                .collect(Collectors.toList());
    }

    /** Enrollment Service 내부 호출: 구매 시점 할인 여부 확인. 할인율은 DB가 아니라 여기 상수(discountRate)에서 나온다. */
    public SubscriptionDto.DiscountCheckResponse checkDiscount(Long userId, Long designerId) {
        boolean subscribed = subscriptionRepository.findByUserIdAndDesignerId(userId, designerId)
                .filter(Subscription::isBenefitActive)
                .isPresent();

        return SubscriptionDto.DiscountCheckResponse.builder()
                .subscribed(subscribed)
                .discountRate(subscribed ? discountRate : BigDecimal.ZERO)
                .build();
    }

    /** course-service 판매 대시보드 내부 호출: 디자이너의 활성 구독자 수 */
    public long countActiveSubscribers(Long designerId) {
        return subscriptionRepository.findByDesignerId(designerId).stream()
                .filter(Subscription::isBenefitActive)
                .count();
    }
}