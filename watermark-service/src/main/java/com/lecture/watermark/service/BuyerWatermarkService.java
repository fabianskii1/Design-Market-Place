package com.lecture.watermark.service;

import com.lecture.watermark.client.CourseServiceClient;
import com.lecture.watermark.entity.BuyerWatermark;
import com.lecture.watermark.repository.BuyerWatermarkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuyerWatermarkService {

    private final CourseServiceClient courseServiceClient;
    private final WatermarkService watermarkService;
    private final WatermarkStorageService storageService;
    private final BuyerWatermarkRepository repository;

    /**
     * 구매 확정 이벤트를 받아 구매자 전용 사본을 만든다.
     *
     * 같은 사람이 같은 디자인을 다시 사도 사본은 하나만 유지한다.
     * (재구매 시 새 페이로드로 갱신)
     */
    @Transactional
    public void createForPurchase(Long courseId, Long buyerId) {
        Long fetched = courseServiceClient.fetchOwnerId(courseId);
        if (fetched == null) {
            // 판매자를 못 찾아도 사본 생성은 계속한다. 추적에 필요한 건 구매자 ID이므로.
            log.warn("[BuyerWatermark] 판매자 ID를 확인하지 못했습니다. courseId={}", courseId);
        }
        // 아래 람다에서 참조하므로 재할당하지 않는 변수로 확정한다
        final Long ownerId = fetched == null ? 0L : fetched;

        byte[] original = courseServiceClient.fetchOriginal(courseId);
        if (original == null || original.length == 0) {
            throw new IllegalStateException("원본 이미지를 가져오지 못했습니다: courseId=" + courseId);
        }

        WatermarkService.BuyerCopyResult result =
                watermarkService.embedBuyer(original, courseId, ownerId, buyerId);

        String storedName = storageService.store(courseId, buyerId, result.bytes());

        BuyerWatermark entity = repository.findByCourseIdAndBuyerId(courseId, buyerId)
                .orElseGet(() -> BuyerWatermark.builder()
                        .courseId(courseId)
                        .buyerId(buyerId)
                        .ownerId(ownerId)
                        .storedName(storedName)
                        .payload(result.payload())
                        .checksum(result.checksum())
                        .fileSize((long) result.bytes().length)
                        .build());

        entity.replace(storedName, result.payload(), result.checksum(), (long) result.bytes().length);
        repository.save(entity);

        log.info("[BuyerWatermark] 사본 준비 완료 courseId={} buyerId={} file={}",
                courseId, buyerId, storedName);
    }

    /** 구매자 사본 바이트. 아직 만들어지지 않았으면 empty */
    public Optional<byte[]> loadBuyerCopy(Long courseId, Long buyerId) {
        return repository.findByCourseIdAndBuyerId(courseId, buyerId)
                .filter(w -> storageService.exists(w.getStoredName()))
                .map(w -> storageService.load(w.getStoredName()));
    }

    /** 유출본 페이로드로 구매 건을 역추적한다 */
    public Optional<BuyerWatermark> traceByPayload(String payload) {
        return repository.findByPayload(payload);
    }
}
