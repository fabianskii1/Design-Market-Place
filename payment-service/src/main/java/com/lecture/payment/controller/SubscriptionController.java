package com.lecture.payment.controller;

import com.lecture.payment.dto.SubscriptionDto;
import com.lecture.payment.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<SubscriptionDto.ApiResponse<SubscriptionDto.SubscriptionResponse>> subscribe(
            @Valid @RequestBody SubscriptionDto.SubscribeRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                SubscriptionDto.ApiResponse.success(
                        subscriptionService.subscribe(userId, request.getDesignerId())));
    }

    @DeleteMapping("/{designerId}")
    public ResponseEntity<Void> cancel(
            @PathVariable Long designerId, @RequestHeader("X-User-Id") Long userId) {
        subscriptionService.cancel(userId, designerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my")
    public ResponseEntity<SubscriptionDto.ApiResponse<List<SubscriptionDto.SubscriptionResponse>>> getMy(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(SubscriptionDto.ApiResponse.success(
                subscriptionService.getMySubscriptions(userId)));
    }

    @GetMapping("/internal/discount")
    public ResponseEntity<SubscriptionDto.DiscountCheckResponse> checkDiscount(
            @RequestParam Long userId, @RequestParam Long designerId) {
        return ResponseEntity.ok(subscriptionService.checkDiscount(userId, designerId));
    }

    @GetMapping("/internal/subscriber-count/{designerId}")
    public ResponseEntity<Long> subscriberCount(@PathVariable Long designerId) {
        return ResponseEntity.ok(subscriptionService.countActiveSubscribers(designerId));
    }
}