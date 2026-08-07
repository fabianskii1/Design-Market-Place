package com.lecture.payment.repository;

import com.lecture.payment.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByUserIdAndDesignerId(Long userId, Long designerId);
    List<Subscription> findByUserId(Long userId);
    List<Subscription> findByDesignerId(Long designerId);
}