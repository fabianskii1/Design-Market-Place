package com.lecture.course.service;

import com.lecture.course.dto.CourseDto;
import com.lecture.course.entity.Course;
import com.lecture.course.repository.CourseRepository;
import com.lecture.course.repository.LicenseTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final LicenseTierRepository licenseTierRepository;
    private final PaymentServiceClient paymentServiceClient;

    /**
     * 강의 등록 (강사만 가능 - SecurityConfig에서 role 검증)
     */
    @Transactional
    public CourseDto.CourseResponse createCourse(CourseDto.CreateRequest request, Long instructorId) {
        Course course = Course.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .price(request.getPrice())
                .instructorId(instructorId)
                .build();

        return CourseDto.CourseResponse.from(courseRepository.save(course));
    }

    /**
     * 강의 단건 조회
     */
    public CourseDto.CourseResponse getCourse(Long id) {
        Course course = findCourseById(id);
        return CourseDto.CourseResponse.from(course);
    }

    /**
     * 전체 활성 강의 목록 조회
     */
    public List<CourseDto.CourseResponse> getAllCourses() {
        return courseRepository.findByStatus(Course.Status.ACTIVE).stream()
                .map(CourseDto.CourseResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 카테고리별 강의 조회
     */
    public List<CourseDto.CourseResponse> getCoursesByCategory(Course.Category category) {
        return courseRepository.findByCategoryAndStatus(category, Course.Status.ACTIVE).stream()
                .map(CourseDto.CourseResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 강의 존재 여부 확인 (Enrollment Service → Course Service REST 호출용)
     */
    public boolean existsCourse(Long id) {
        return courseRepository.existsById(id);
    }

    /**
     * 수강생 수 증가 (Enrollment Service 수강 활성화 시 호출)
     */
    @Transactional
    public void increaseEnrollmentCount(Long courseId) {
        Course course = findCourseById(courseId);
        course.increaseEnrollmentCount();
    }

    /**
     * 추천 서비스용: 카테고리별 미수강 강의 조회
     * - excludeCourseIds: 이미 수강한 강의 ID 목록
     */
    public List<CourseDto.CourseResponse> getRecommendCourses(
            Course.Category category, List<Long> excludeCourseIds) {

        List<Course> courses = excludeCourseIds.isEmpty()
                ? courseRepository.findByCategoryAndStatus(category, Course.Status.ACTIVE)
                : courseRepository.findByCategoryAndStatusAndIdNotIn(
                        category, Course.Status.ACTIVE, excludeCourseIds);

        // 수강생 수 기준 내림차순 정렬
        return courses.stream()
                .sorted((a, b) -> b.getEnrollmentCount() - a.getEnrollmentCount())
                .map(CourseDto.CourseResponse::from)
                .collect(Collectors.toList());
    }

    private Course findCourseById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("강의를 찾을 수 없습니다: " + id));
    }

    /**
     * 강의(디자인)별 라이선스 등급 목록 조회
     * - 등록 API는 Should 스프린트에서 추가 예정. 오늘은 조회만 가능(빈 목록 정상)
     */
    public List<CourseDto.LicenseTierResponse> getLicenseTiers(Long courseId) {
        return licenseTierRepository.findByCourseId(courseId).stream()
                .map(CourseDto.LicenseTierResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 판매 통계 조회 (마이페이지 판매 대시보드)
     * - X-User-Id(=instructorId) 기준으로 본인 강의만 집계
     */
    public CourseDto.SalesDashboardResponse getMySales(Long instructorId) {
        List<Course> myCourses = courseRepository.findByInstructorId(instructorId);
        List<Long> courseIds = myCourses.stream().map(Course::getId).collect(Collectors.toList());

        Map<Long, PaymentServiceClient.CourseSales> salesMap =
                paymentServiceClient.getSalesSummary(courseIds);

        List<CourseDto.SalesItem> items = myCourses.stream()
                .map(course -> {
                    PaymentServiceClient.CourseSales sales =
                            salesMap.getOrDefault(course.getId(),
                                    new PaymentServiceClient.CourseSales(0L, java.math.BigDecimal.ZERO));
                    return CourseDto.SalesItem.builder()
                            .courseId(course.getId())
                            .title(course.getTitle())
                            .salesCount(sales.salesCount())
                            .revenue(sales.totalRevenue())
                            .build();
                })
                .collect(Collectors.toList());

        long totalCount = items.stream().mapToLong(CourseDto.SalesItem::getSalesCount).sum();
        java.math.BigDecimal totalRevenue = items.stream()
                .map(CourseDto.SalesItem::getRevenue)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        return CourseDto.SalesDashboardResponse.builder()
                .items(items)
                .totalSalesCount(totalCount)
                .totalRevenue(totalRevenue)
                .build();
    }
    
}
