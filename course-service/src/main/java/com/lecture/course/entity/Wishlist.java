package com.lecture.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 찜.
 * 토글 방식(추가/삭제)으로만 다루므로 수정 메서드가 없다.
 * (user_id, course_id) 유니크로 중복 찜을 DB 레벨에서 막는다.
 */
@Entity
@Table(name = "wishlist",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_wishlist_user_course",
               columnNames = {"user_id", "course_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 디자인 ID (courses.id) */
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public static Wishlist of(Long userId, Long courseId) {
        return Wishlist.builder()
                .userId(userId)
                .courseId(courseId)
                .build();
    }
}
