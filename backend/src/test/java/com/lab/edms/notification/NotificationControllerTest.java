package com.lab.edms.notification;

import com.lab.edms.common.ForbiddenException;
import com.lab.edms.common.NotFoundException;
import com.lab.edms.notification.dto.NotificationDto;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * M8 PR4 T24 — unit tests for {@link NotificationController}.
 *
 * <p>순수 Mockito 단위 테스트 — Spring 컨텍스트 없이 컨트롤러 메서드를 직접 호출합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepo;

    @InjectMocks
    private NotificationController controller;

    @Mock
    private Authentication auth;

    private User mockUser;

    @BeforeEach
    void setUp() throws Exception {
        mockUser = new User();
        // User.id 는 @GeneratedValue 로 관리되므로 리플렉션으로 주입
        java.lang.reflect.Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(mockUser, 42L);
        mockUser.setUserId("testUser");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 헬퍼 — Notification 스텁 생성
    // ─────────────────────────────────────────────────────────────────────────

    private Notification buildNotification(Long id) {
        Notification n = new Notification();
        try {
            java.lang.reflect.Field idField = Notification.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(n, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        n.setRecipientId(42L);
        n.setEventCode("DOC_APPROVED");
        n.setTitle("승인 완료");
        n.setBody("문서가 승인되었습니다.");
        n.setRead(false);
        n.setLinkPath("/documents/1");
        n.setRelatedDocumentId(1L);
        n.setSeverity("INFO");
        return n;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1: list — 인증된 사용자에게 Page 200 반환
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void list_returns_page_for_authenticated_user() {
        when(auth.getName()).thenReturn("testUser");
        when(userRepo.findByUserId("testUser")).thenReturn(Optional.of(mockUser));

        Notification n = buildNotification(1L);
        Page<Notification> notifPage = new PageImpl<>(List.of(n));
        when(notificationService.listForUser(eq(42L), any(Pageable.class))).thenReturn(notifPage);

        ResponseEntity<Page<NotificationDto>> response = controller.list(auth, 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalElements()).isEqualTo(1);
        assertThat(response.getBody().getContent().get(0).id()).isEqualTo(1L);
        assertThat(response.getBody().getContent().get(0).eventCode()).isEqualTo("DOC_APPROVED");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2: list — 페이지네이션 파라미터가 올바르게 전달되는지 검증
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void list_pagination_params_passed_correctly() {
        when(auth.getName()).thenReturn("testUser");
        when(userRepo.findByUserId("testUser")).thenReturn(Optional.of(mockUser));

        Page<Notification> emptyPage = new PageImpl<>(List.of());
        when(notificationService.listForUser(eq(42L), any(Pageable.class))).thenReturn(emptyPage);

        controller.list(auth, 1, 10);

        verify(notificationService).listForUser(eq(42L), eq(PageRequest.of(1, 10)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3: unreadCount — 미읽음 수 Map 반환
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void unreadCount_returns_count_map() {
        when(auth.getName()).thenReturn("testUser");
        when(userRepo.findByUserId("testUser")).thenReturn(Optional.of(mockUser));
        when(notificationService.unreadCount(42L)).thenReturn(5L);

        ResponseEntity<Map<String, Long>> response = controller.unreadCount(auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsEntry("count", 5L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 4: markRead — 성공 시 204 반환
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void markRead_returns_204() {
        when(auth.getName()).thenReturn("testUser");
        when(userRepo.findByUserId("testUser")).thenReturn(Optional.of(mockUser));
        doNothing().when(notificationService).markRead(100L, 42L);

        ResponseEntity<Void> response = controller.markRead(100L, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(notificationService).markRead(100L, 42L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 5: markRead — 소유자가 아닌 경우 ForbiddenException 전파
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void markRead_forbidden_when_not_owner() {
        when(auth.getName()).thenReturn("testUser");
        when(userRepo.findByUserId("testUser")).thenReturn(Optional.of(mockUser));
        doThrow(new ForbiddenException("알림에 대한 접근 권한이 없습니다."))
                .when(notificationService).markRead(200L, 42L);

        assertThatThrownBy(() -> controller.markRead(200L, auth))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("접근 권한");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 6: markRead — 알림이 없는 경우 EntityNotFoundException 전파
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void markRead_not_found_when_missing() {
        when(auth.getName()).thenReturn("testUser");
        when(userRepo.findByUserId("testUser")).thenReturn(Optional.of(mockUser));
        doThrow(new jakarta.persistence.EntityNotFoundException("Notification not found: 999"))
                .when(notificationService).markRead(999L, 42L);

        assertThatThrownBy(() -> controller.markRead(999L, auth))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 7: list — userRepo.findByUserId 가 auth.getName() 으로 호출되는지 검증
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void list_resolves_user_id_correctly() {
        when(auth.getName()).thenReturn("specificUser");
        when(userRepo.findByUserId("specificUser")).thenReturn(Optional.of(mockUser));
        when(notificationService.listForUser(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.list(auth, 0, 20);

        verify(userRepo).findByUserId("specificUser");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 8: unreadCount — userRepo.findByUserId 가 auth.getName() 으로 호출되는지 검증
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void unreadCount_resolves_user_id_correctly() {
        when(auth.getName()).thenReturn("anotherUser");
        when(userRepo.findByUserId("anotherUser")).thenReturn(Optional.of(mockUser));
        when(notificationService.unreadCount(42L)).thenReturn(0L);

        controller.unreadCount(auth);

        verify(userRepo).findByUserId("anotherUser");
        verify(notificationService).unreadCount(42L);
    }
}
