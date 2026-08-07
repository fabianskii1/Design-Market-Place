package com.lecture.watermark.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;

/**
 * 구매자별 사본 파일 저장소.
 *
 * course-service 의 업로드 저장소와 분리되어 있다.
 * 각 서비스가 자기 데이터를 소유한다는 원칙에 따라, 구매자 사본은 이 서비스가 관리한다.
 */
@Slf4j
@Service
public class WatermarkStorageService {

    private final Path root;

    public WatermarkStorageService(@Value("${storage.location:/app/watermarks}") String location) {
        this.root = Paths.get(location).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(root);
            log.info("[Storage] 워터마크 저장 경로 준비 완료: {}", root);
        } catch (IOException e) {
            throw new IllegalStateException("저장 디렉토리를 만들 수 없습니다: " + root, e);
        }
    }

    /** 파일명은 조회 키(courseId, buyerId)로 결정한다. 재구매 시 덮어쓴다. */
    public String store(Long courseId, Long buyerId, byte[] bytes) {
        String storedName = "buyer-" + courseId + "-" + buyerId + ".png";
        Path target = resolve(storedName);

        try {
            Files.write(target, bytes,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("사본 저장에 실패했습니다: " + storedName, e);
        }
        return storedName;
    }

    public byte[] load(String storedName) {
        try {
            return Files.readAllBytes(resolve(storedName));
        } catch (IOException e) {
            throw new IllegalStateException("사본을 읽을 수 없습니다: " + storedName, e);
        }
    }

    public boolean exists(String storedName) {
        return storedName != null && Files.exists(resolve(storedName));
    }

    /** 정규화 후 루트를 벗어나면 거부한다 (경로 조작 방지) */
    private Path resolve(String storedName) {
        Path target = root.resolve(storedName).normalize();
        if (!target.getParent().equals(root)) {
            throw new IllegalArgumentException("잘못된 파일 경로입니다: " + storedName);
        }
        return target;
    }
}
