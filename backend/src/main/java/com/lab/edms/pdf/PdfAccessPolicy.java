package com.lab.edms.pdf;

import com.lab.edms.document.Document;
import com.lab.edms.document.DocumentVersion;
import com.lab.edms.security.EdmsPermissionEvaluator;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * M7.1 PR1 — PDF access policy matrix (plan D2).
 *
 * <p>Single source of truth for "who can view / download which PDF rendition under
 * which {@link PdfStatus}". Delegates the row-level {@code can_view} / {@code can_download}
 * permission columns to {@link EdmsPermissionEvaluator} — do NOT duplicate that logic here.</p>
 *
 * <p>Rules summary:</p>
 * <ul>
 *   <li>{@code PENDING_CONVERSION} / {@code STAMPING} / {@code WATERMARKING} → nobody.
 *       Caller surfaces this as HTTP 409 {@code NOT_READY} (this class only signals
 *       "not viewable yet" via {@link Decision#notReady}).</li>
 *   <li>{@code CONVERTED} + {@code INITIAL} → active-step assignee, Author (owner), ADMIN/AUDITOR,
 *       and category admin (i.e. anyone with {@code can_view} on the row) only.</li>
 *   <li>{@code STAMPED} (rendition) → same as INITIAL.</li>
 *   <li>{@code EFFECTIVE_STAMPED} + {@code EFFECTIVE} (rendition) → everyone with {@code can_view}.</li>
 *   <li>{@code ORIGINAL} (docx/hwp) → NOT handled here — delegated to {@code DocumentFileController}.</li>
 * </ul>
 *
 * <p>"Unauthorized" decisions intentionally return a {@link Decision} that the
 * caller maps to HTTP 404 {@code NOT_FOUND} (IDOR protection, identical to the
 * M6 visibility pattern).</p>
 */
@Component
public class PdfAccessPolicy {

    private final EdmsPermissionEvaluator permissionEvaluator;
    private final UserRepository userRepo;
    private final JdbcTemplate jdbc;

    public PdfAccessPolicy(EdmsPermissionEvaluator permissionEvaluator,
                          UserRepository userRepo,
                          JdbcTemplate jdbc) {
        this.permissionEvaluator = permissionEvaluator;
        this.userRepo = userRepo;
        this.jdbc = jdbc;
    }

    /** Outcome enum — keeps the policy decoupled from HTTP status codes. */
    public enum Outcome {
        ALLOW,         // user may proceed
        DENY,          // user is not authorized — caller returns 404 NOT_FOUND for IDOR
        NOT_READY      // PDF pipeline still in-flight — caller returns 409 NOT_READY
    }

    /** Decision record bundling outcome + advisory retry hint for NOT_READY. */
    public record Decision(Outcome outcome, Integer retryAfterSeconds) {
        public static Decision allow()    { return new Decision(Outcome.ALLOW, null); }
        public static Decision deny()     { return new Decision(Outcome.DENY, null); }
        public static Decision notReady() { return new Decision(Outcome.NOT_READY, 5); }

        public boolean allowed()  { return outcome == Outcome.ALLOW; }
        public boolean denied()   { return outcome == Outcome.DENY; }
        public boolean notReady_(){ return outcome == Outcome.NOT_READY; }
    }

    /**
     * Evaluate view permission for a (document, version, rendition kind) tuple.
     *
     * @param auth          Spring Security authentication (may be null → DENY)
     * @param document      the parent document (carries categoryId, department, ownerId, pdfStatus)
     * @param version       the requested DocumentVersion
     * @param renditionKind the PDF rendition kind being requested (INITIAL/STAMPED/EFFECTIVE)
     */
    public Decision canView(Authentication auth, Document document, DocumentVersion version,
                            RenditionKind renditionKind) {
        if (auth == null || !auth.isAuthenticated()) return Decision.deny();
        if (document == null || version == null || renditionKind == null) return Decision.deny();

        // 1. Pipeline-in-flight gate — strictly NOT_READY (409) regardless of role.
        if (isInFlight(document.getPdfStatus())) {
            return Decision.notReady();
        }

        // 2. EFFECTIVE rendition on an EFFECTIVE_STAMPED document → broad read access:
        //    every user with can_view on the (category, department) tuple.
        if (renditionKind == RenditionKind.EFFECTIVE
                && PdfStatus.EFFECTIVE_STAMPED.name().equals(document.getPdfStatus())) {
            return hasCanView(auth, document) ? Decision.allow() : Decision.deny();
        }

        // 3. INITIAL / STAMPED renditions (or EFFECTIVE on non-EFFECTIVE_STAMPED docs):
        //    restricted to active-step assignee, owner (Author), ADMIN/AUDITOR,
        //    or anyone with can_view on the row (which includes category admins).
        return hasRestrictedAccess(auth, document, version) ? Decision.allow() : Decision.deny();
    }

    /**
     * Evaluate download permission. Reuses {@link #canView} as a precondition, then
     * additionally requires the {@code can_download} permission column for non-admin
     * users. ADMIN / AUDITOR / owner / active assignee bypass {@code can_download}.
     */
    public Decision canDownload(Authentication auth, Document document, DocumentVersion version,
                                RenditionKind renditionKind) {
        Decision view = canView(auth, document, version, renditionKind);
        if (!view.allowed()) return view;

        // Owners, admins, auditors, and active-step assignees may always download (consistent
        // with how M3 file download treats privileged actors).
        if (isOwner(auth, document)
                || EdmsPermissionEvaluator.hasRole(auth, "ADMIN")
                || EdmsPermissionEvaluator.hasRole(auth, "AUDITOR")
                || isActiveStepAssignee(auth, version.getId())) {
            return Decision.allow();
        }

        // Everyone else needs can_download on the row.
        return permissionEvaluator.hasPermission(auth, document, "DOWNLOAD")
                ? Decision.allow()
                : Decision.deny();
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /** True iff the pdf_status indicates an in-flight pipeline run. */
    public boolean isInFlight(String pdfStatus) {
        if (pdfStatus == null) return false;
        return PdfStatus.PENDING_CONVERSION.name().equals(pdfStatus)
                || PdfStatus.STAMPING.name().equals(pdfStatus)
                || PdfStatus.WATERMARKING.name().equals(pdfStatus);
    }

    /** Pure {@code can_view} check via the permission evaluator (handles ADMIN bypass). */
    private boolean hasCanView(Authentication auth, Document document) {
        // ADMIN and AUDITOR always have read access for compliance.
        if (EdmsPermissionEvaluator.hasRole(auth, "ADMIN")
                || EdmsPermissionEvaluator.hasRole(auth, "AUDITOR")) {
            return true;
        }
        return permissionEvaluator.hasPermission(auth, document, "VIEW");
    }

    /** Restricted access tier — owner, admin/auditor, active-step assignee, or row can_view. */
    private boolean hasRestrictedAccess(Authentication auth, Document document, DocumentVersion version) {
        if (EdmsPermissionEvaluator.hasRole(auth, "ADMIN")
                || EdmsPermissionEvaluator.hasRole(auth, "AUDITOR")) {
            return true;
        }
        if (isOwner(auth, document)) return true;
        if (isActiveStepAssignee(auth, version.getId())) return true;
        // Fallback: row-level can_view (covers category admins set up via permissions).
        return permissionEvaluator.hasPermission(auth, document, "VIEW");
    }

    private boolean isOwner(Authentication auth, Document document) {
        if (auth == null || document == null) return false;
        Optional<User> opt = userRepo.findByUserId(auth.getName());
        return opt.map(u -> u.getId().equals(document.getOwnerId())).orElse(false);
    }

    /**
     * Active-step assignee check: is there any in-flight workflow on this version where the
     * current step is IN_PROGRESS and the authenticated user is listed in {@code assignees[]}?
     *
     * <p>Implemented as a native JSONB query against {@code workflow_step_instances} +
     * {@code workflow_instances} — mirrors {@code WorkflowStepInstanceRepository.findMyPending}.</p>
     */
    public boolean isActiveStepAssignee(Authentication auth, Long versionId) {
        if (auth == null || versionId == null) return false;
        String userIdString = auth.getName();

        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                  FROM workflow_step_instances wsi
                  JOIN workflow_instances wi ON wi.id = wsi.workflow_id
                 WHERE wi.version_id = ?
                   AND wi.state = 'IN_PROGRESS'
                   AND wsi.state = 'IN_PROGRESS'
                   AND wsi.assignees @> jsonb_build_array(jsonb_build_object('userIdString', ?::text))
                """,
                Integer.class, versionId, userIdString);
        return count != null && count > 0;
    }
}
