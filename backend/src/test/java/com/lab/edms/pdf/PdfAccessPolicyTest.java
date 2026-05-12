package com.lab.edms.pdf;

import com.lab.edms.document.Document;
import com.lab.edms.document.DocumentVersion;
import com.lab.edms.security.EdmsPermissionEvaluator;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * M7.1 PR1 — unit tests for {@link PdfAccessPolicy}.
 *
 * <p>Coverage targets the access-matrix from plan D2:</p>
 * <ul>
 *   <li>In-flight pipeline ({@code PENDING_CONVERSION/STAMPING/WATERMARKING}) → NOT_READY for everyone</li>
 *   <li>{@code CONVERTED + INITIAL/STAMPED} → restricted (assignee / owner / ADMIN / AUDITOR / can_view)</li>
 *   <li>{@code EFFECTIVE_STAMPED + EFFECTIVE} → all with can_view</li>
 *   <li>Unknown user / null authentication → DENY</li>
 *   <li>{@code canDownload} requires {@code can_download} unless caller is owner/admin/auditor/assignee</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PdfAccessPolicyTest {

    @Mock EdmsPermissionEvaluator permissionEvaluator;
    @Mock UserRepository userRepo;
    @Mock JdbcTemplate jdbc;

    @InjectMocks PdfAccessPolicy policy;

    private Document doc;
    private DocumentVersion version;

    @BeforeEach
    void buildFixtures() {
        doc = new Document();
        doc.setCategoryId(10L);
        doc.setDepartment("QC");
        doc.setOwnerId(100L);
        doc.setConfidential(false);
        // pdfStatus set per test

        version = new DocumentVersion();
        // Reflection-free id setter not available; the policy only ever calls getId(),
        // so a tiny subclass with a constant id suffices in scenarios where we need it.
    }

    private Authentication auth(String userId, String... roles) {
        List<GrantedAuthority> auths = java.util.Arrays.stream(roles)
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
        return new UsernamePasswordAuthenticationToken(userId, "n/a", auths);
    }

    private DocumentVersion versionWithId(long id) {
        DocumentVersion v = new DocumentVersion() {
            @Override public Long getId() { return id; }
        };
        return v;
    }

    private void stubUser(String userId, long dbId) {
        User u = new User();
        u.setUserId(userId);
        // User.id has no setter; build via reflection.
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(u, dbId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        lenient().when(userRepo.findByUserId(userId)).thenReturn(Optional.of(u));
    }

    // -----------------------------------------------------------------------
    // In-flight pipeline → NOT_READY for everyone (including ADMIN).
    // -----------------------------------------------------------------------

    @Test
    void inFlight_pendingConversion_returnsNotReady_evenForAdmin() {
        doc.setPdfStatus(PdfStatus.PENDING_CONVERSION.name());
        Authentication admin = auth("admin", "ADMIN");

        var d = policy.canView(admin, doc, versionWithId(1L), RenditionKind.INITIAL);

        assertThat(d.isNotReady()).isTrue();
        assertThat(d.retryAfterSeconds()).isNotNull();
    }

    @Test
    void inFlight_stamping_returnsNotReady() {
        doc.setPdfStatus(PdfStatus.STAMPING.name());
        var d = policy.canView(auth("u", "READER"), doc, versionWithId(1L), RenditionKind.STAMPED);
        assertThat(d.isNotReady()).isTrue();
    }

    @Test
    void inFlight_watermarking_returnsNotReady() {
        doc.setPdfStatus(PdfStatus.WATERMARKING.name());
        var d = policy.canView(auth("u", "READER"), doc, versionWithId(1L), RenditionKind.EFFECTIVE);
        assertThat(d.isNotReady()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Null / unauthenticated → DENY
    // -----------------------------------------------------------------------

    @Test
    void nullAuth_denies() {
        doc.setPdfStatus(PdfStatus.CONVERTED.name());
        var d = policy.canView(null, doc, versionWithId(1L), RenditionKind.INITIAL);
        assertThat(d.denied()).isTrue();
    }

    @Test
    void nullDocument_denies() {
        var d = policy.canView(auth("u", "READER"), null, versionWithId(1L), RenditionKind.INITIAL);
        assertThat(d.denied()).isTrue();
    }

    // -----------------------------------------------------------------------
    // CONVERTED + INITIAL/STAMPED — restricted tier
    // -----------------------------------------------------------------------

    @Test
    void converted_initial_admin_allowed() {
        doc.setPdfStatus(PdfStatus.CONVERTED.name());
        var d = policy.canView(auth("admin", "ADMIN"), doc, versionWithId(1L), RenditionKind.INITIAL);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void converted_initial_auditor_allowed() {
        doc.setPdfStatus(PdfStatus.CONVERTED.name());
        var d = policy.canView(auth("aud", "AUDITOR"), doc, versionWithId(1L), RenditionKind.INITIAL);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void converted_initial_owner_allowed() {
        doc.setPdfStatus(PdfStatus.CONVERTED.name());
        stubUser("alice", 100L);   // matches doc.ownerId=100
        // No active step → JDBC returns 0
        lenient().when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);

        var d = policy.canView(auth("alice", "AUTHOR"), doc, versionWithId(1L), RenditionKind.INITIAL);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void converted_initial_activeAssignee_allowed() {
        doc.setPdfStatus(PdfStatus.CONVERTED.name());
        stubUser("bob", 200L);   // NOT owner
        // assignee query returns 1 (matches)
        lenient().when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);

        var d = policy.canView(auth("bob", "REVIEWER"), doc, versionWithId(1L), RenditionKind.INITIAL);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void converted_initial_generalUser_withCanView_allowed() {
        doc.setPdfStatus(PdfStatus.CONVERTED.name());
        stubUser("carol", 300L);
        // Not active assignee
        lenient().when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        // can_view on row → true
        when(permissionEvaluator.hasPermission(any(), eq(doc), eq("VIEW"))).thenReturn(true);

        var d = policy.canView(auth("carol", "READER"), doc, versionWithId(1L), RenditionKind.INITIAL);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void converted_initial_unrelatedUser_denied() {
        doc.setPdfStatus(PdfStatus.CONVERTED.name());
        stubUser("dave", 400L);
        lenient().when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        when(permissionEvaluator.hasPermission(any(), eq(doc), eq("VIEW"))).thenReturn(false);

        var d = policy.canView(auth("dave", "READER"), doc, versionWithId(1L), RenditionKind.INITIAL);
        assertThat(d.denied()).isTrue();
    }

    @Test
    void converted_stamped_unrelatedUser_denied() {
        doc.setPdfStatus(PdfStatus.CONVERTED.name());
        stubUser("dave", 400L);
        lenient().when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        when(permissionEvaluator.hasPermission(any(), eq(doc), eq("VIEW"))).thenReturn(false);

        var d = policy.canView(auth("dave", "READER"), doc, versionWithId(1L), RenditionKind.STAMPED);
        assertThat(d.denied()).isTrue();
    }

    // -----------------------------------------------------------------------
    // EFFECTIVE_STAMPED + EFFECTIVE — broad read tier
    // -----------------------------------------------------------------------

    @Test
    void effectiveStamped_effective_anyUserWithCanView_allowed() {
        doc.setPdfStatus(PdfStatus.EFFECTIVE_STAMPED.name());
        when(permissionEvaluator.hasPermission(any(), eq(doc), eq("VIEW"))).thenReturn(true);

        var d = policy.canView(auth("eve", "READER"), doc, versionWithId(1L), RenditionKind.EFFECTIVE);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void effectiveStamped_effective_noPermission_denied() {
        doc.setPdfStatus(PdfStatus.EFFECTIVE_STAMPED.name());
        when(permissionEvaluator.hasPermission(any(), eq(doc), eq("VIEW"))).thenReturn(false);

        var d = policy.canView(auth("eve", "READER"), doc, versionWithId(1L), RenditionKind.EFFECTIVE);
        assertThat(d.denied()).isTrue();
    }

    @Test
    void effectiveStamped_effective_admin_allowed_evenWithoutPerm() {
        doc.setPdfStatus(PdfStatus.EFFECTIVE_STAMPED.name());
        // permissionEvaluator never called because ADMIN bypasses
        var d = policy.canView(auth("admin", "ADMIN"), doc, versionWithId(1L), RenditionKind.EFFECTIVE);
        assertThat(d.allowed()).isTrue();
    }

    /**
     * Pre-EFFECTIVE document but requesting EFFECTIVE kind: falls through to the restricted tier;
     * not auto-granted to general users via can_view alone.
     */
    @Test
    void converted_effective_generalUser_fallsToRestricted() {
        doc.setPdfStatus(PdfStatus.CONVERTED.name());
        stubUser("frank", 500L);
        lenient().when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        when(permissionEvaluator.hasPermission(any(), eq(doc), eq("VIEW"))).thenReturn(false);

        var d = policy.canView(auth("frank", "READER"), doc, versionWithId(1L), RenditionKind.EFFECTIVE);
        assertThat(d.denied()).isTrue();
    }

    // -----------------------------------------------------------------------
    // canDownload — requires can_download for general users
    // -----------------------------------------------------------------------

    @Test
    void download_generalUser_withCanView_butNoDownload_denied() {
        doc.setPdfStatus(PdfStatus.EFFECTIVE_STAMPED.name());
        stubUser("greg", 600L);
        lenient().when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        when(permissionEvaluator.hasPermission(any(), eq(doc), eq("VIEW"))).thenReturn(true);
        when(permissionEvaluator.hasPermission(any(), eq(doc), eq("DOWNLOAD"))).thenReturn(false);

        var d = policy.canDownload(auth("greg", "READER"), doc, versionWithId(1L), RenditionKind.EFFECTIVE);
        assertThat(d.denied()).isTrue();
    }

    @Test
    void download_generalUser_withCanDownload_allowed() {
        doc.setPdfStatus(PdfStatus.EFFECTIVE_STAMPED.name());
        stubUser("greg", 600L);
        lenient().when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        when(permissionEvaluator.hasPermission(any(), eq(doc), eq("VIEW"))).thenReturn(true);
        when(permissionEvaluator.hasPermission(any(), eq(doc), eq("DOWNLOAD"))).thenReturn(true);

        var d = policy.canDownload(auth("greg", "READER"), doc, versionWithId(1L), RenditionKind.EFFECTIVE);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void download_admin_alwaysAllowed_evenWithoutCanDownloadRow() {
        doc.setPdfStatus(PdfStatus.EFFECTIVE_STAMPED.name());
        // ADMIN bypasses both view and download checks.
        var d = policy.canDownload(auth("admin", "ADMIN"), doc, versionWithId(1L), RenditionKind.EFFECTIVE);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void download_owner_alwaysAllowed_evenWithoutCanDownloadRow() {
        doc.setPdfStatus(PdfStatus.EFFECTIVE_STAMPED.name());
        stubUser("alice", 100L);  // == ownerId
        lenient().when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        when(permissionEvaluator.hasPermission(any(), eq(doc), eq("VIEW"))).thenReturn(true);
        // No stub for "DOWNLOAD" — owner short-circuits before reaching it.

        var d = policy.canDownload(auth("alice", "AUTHOR"), doc, versionWithId(1L), RenditionKind.EFFECTIVE);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void download_inFlight_returnsNotReady_neverDownloads() {
        doc.setPdfStatus(PdfStatus.STAMPING.name());
        var d = policy.canDownload(auth("admin", "ADMIN"), doc, versionWithId(1L), RenditionKind.STAMPED);
        assertThat(d.isNotReady()).isTrue();
    }

    // -----------------------------------------------------------------------
    // isInFlight helper
    // -----------------------------------------------------------------------

    @Test
    void isInFlight_nullStatus_isFalse() {
        assertThat(policy.isInFlight(null)).isFalse();
    }

    @Test
    void isInFlight_converted_isFalse() {
        assertThat(policy.isInFlight(PdfStatus.CONVERTED.name())).isFalse();
    }

    @Test
    void isInFlight_effectiveStamped_isFalse() {
        assertThat(policy.isInFlight(PdfStatus.EFFECTIVE_STAMPED.name())).isFalse();
    }

    @Test
    void isInFlight_pendingConversion_isTrue() {
        assertThat(policy.isInFlight(PdfStatus.PENDING_CONVERSION.name())).isTrue();
    }

    @Test
    void isInFlight_stamping_isTrue() {
        assertThat(policy.isInFlight(PdfStatus.STAMPING.name())).isTrue();
    }

    @Test
    void isInFlight_watermarking_isTrue() {
        assertThat(policy.isInFlight(PdfStatus.WATERMARKING.name())).isTrue();
    }
}
