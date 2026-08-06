package com.lecture.course.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

/**
 * 디자인 자산 파일 저장소.
 *
 * 지금은 컨테이너 로컬 디스크에 저장한다.
 * 나중에 S3/MinIO 로 옮길 때 이 클래스의 store/load 구현만 바꾸면
 * 컨트롤러·서비스는 손대지 않아도 된다.
 */
@Slf4j
@Service
public class FileStorageService {

    private static final List<String> ALLOWED_CONTENT_TYPES =
            List.of("image/png", "image/jpeg", "image/webp");

    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10MB

    /** 저장 루트. application.yml 의 storage.location 으로 주입 */
    private final Path root;

    public FileStorageService(@Value("${storage.location:/app/uploads}") String location) {
        this.root = Paths.get(location).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(root);
            log.info("[Storage] 저장 경로 준비 완료: {}", root);
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장 디렉토리를 만들 수 없습니다: " + root, e);
        }
    }

    /**
     * 파일을 저장하고 저장된 파일명을 반환한다.
     * 원본 파일명은 신뢰하지 않는다 (경로 조작 방지). UUID + 확장자로만 저장한다.
     */
    public String store(Long courseId, MultipartFile file) {
        validate(file);

        String extension = resolveExtension(file.getContentType());
        String storedName = "design-" + courseId + "-" + UUID.randomUUID() + extension;
        Path target = root.resolve(storedName).normalize();

        // 정규화 후에도 루트 밖을 가리키면 거부한다
        if (!target.getParent().equals(root)) {
            throw new IllegalArgumentException("잘못된 파일 경로입니다.");
        }

        try (var in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장에 실패했습니다: " + storedName, e);
        }

        log.info("[Storage] 저장 완료 courseId={} file={} size={}B", courseId, storedName, file.getSize());
        return storedName;
    }

    /** 저장된 파일을 읽어온다 (미리보기·다운로드용) */
    public Resource load(String storedName) {
        try {
            Path target = root.resolve(storedName).normalize();
            if (!target.getParent().equals(root)) {
                throw new IllegalArgumentException("잘못된 파일 경로입니다.");
            }

            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("파일을 찾을 수 없습니다: " + storedName);
            }
            return resource;
        } catch (IOException e) {
            throw new IllegalArgumentException("파일을 읽을 수 없습니다: " + storedName, e);
        }
    }

    /** 이전 파일 삭제 (교체 시). 실패해도 업로드 자체는 막지 않는다. */
    public void deleteQuietly(String storedName) {
        if (storedName == null || storedName.isBlank()) return;
        try {
            Files.deleteIfExists(root.resolve(storedName).normalize());
        } catch (IOException e) {
            log.warn("[Storage] 이전 파일 삭제 실패 (무시): {}", storedName, e);
        }
    }

    /** 저장된 파일명으로 Content-Type 추정 */
    public String contentTypeOf(String storedName) {
        String lower = storedName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("파일 크기는 10MB를 넘을 수 없습니다.");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("PNG, JPG, WEBP 형식만 업로드할 수 있습니다.");
        }
    }

    private String resolveExtension(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}