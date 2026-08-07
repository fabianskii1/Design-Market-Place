package com.lecture.course.service;

import com.lecture.course.exception.AssetNotFoundException;
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
     * 처리된 이미지 바이트를 저장하고 저장된 파일명을 반환한다.
     *
     * 워터마크 처리 결과는 항상 PNG다. JPEG로 저장하면 비가시적 워터마크의
     * 최하위 비트가 파괴되어 추출이 불가능해진다.
     *
     * @param suffix 용도 구분자 ("original" | "preview")
     */
    public String storePng(Long courseId, byte[] bytes, String suffix) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("저장할 데이터가 없습니다.");
        }

        String storedName = "design-" + courseId + "-" + suffix + "-" + UUID.randomUUID() + ".png";
        Path target = resolveSafely(storedName);

        try {
            Files.write(target, bytes,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장에 실패했습니다: " + storedName, e);
        }

        log.info("[Storage] 저장 완료 courseId={} file={} size={}B", courseId, storedName, bytes.length);
        return storedName;
    }

    /** 업로드 파일을 바이트로 읽는다 (워터마크 처리 입력용) */
    public byte[] readUpload(MultipartFile file) {
        validate(file);
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("업로드 파일을 읽을 수 없습니다.", e);
        }
    }

    /** 저장된 파일을 읽어온다 (미리보기·다운로드용) */
    public Resource load(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            throw new AssetNotFoundException("등록된 파일이 없습니다.");
        }

        try {
            Path target = resolveSafely(storedName);

            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new AssetNotFoundException("파일을 찾을 수 없습니다: " + storedName);
            }
            return resource;
        } catch (IOException e) {
            throw new AssetNotFoundException("파일을 읽을 수 없습니다: " + storedName);
        }
    }

    /** 저장된 파일의 바이트 (검증 시 체크섬 비교용) */
    public byte[] loadBytes(String storedName) {
        try {
            return Files.readAllBytes(resolveSafely(storedName));
        } catch (IOException e) {
            throw new AssetNotFoundException("파일을 읽을 수 없습니다: " + storedName);
        }
    }

    /** 이전 파일 삭제 (교체 시). 실패해도 업로드 자체는 막지 않는다. */
    public void deleteQuietly(String storedName) {
        if (storedName == null || storedName.isBlank()) return;
        try {
            Files.deleteIfExists(resolveSafely(storedName));
        } catch (Exception e) {
            log.warn("[Storage] 이전 파일 삭제 실패 (무시): {}", storedName, e);
        }
    }

    /** 저장된 파일명으로 Content-Type 추정 */
    public String contentTypeOf(String storedName) {
        if (storedName == null) return "application/octet-stream";
        String lower = storedName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    /** 업로드 파일 사전 검증. 클라이언트 입력 오류이므로 400으로 처리된다. */
    public void validate(MultipartFile file) {
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

    /** 정규화 후에도 루트 밖을 가리키면 거부한다 (경로 조작 방지) */
    private Path resolveSafely(String storedName) {
        Path target = root.resolve(storedName).normalize();
        if (!target.getParent().equals(root)) {
            throw new IllegalArgumentException("잘못된 파일 경로입니다.");
        }
        return target;
    }
}
