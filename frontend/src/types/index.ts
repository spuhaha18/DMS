export interface ProblemDetail {
  code: string;
  message: string;
  detail?: string | null;
  timestamp: string;
  [extra: string]: unknown;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface User {
  id: number;
  user_id: string;
  full_name: string;
  email: string;
  department: string;
  title?: string | null;
  status: 'ACTIVE' | 'LOCKED' | 'DISABLED';
  force_change_pw: boolean;
  valid_from?: string | null;
  valid_until?: string | null;
  last_login_at?: string | null;
  created_at: string;
  role_codes: string[];
}

export interface Role {
  id: number;
  role_code: string;
  role_name: string;
  description?: string | null;
  is_system: boolean;
}

export interface Permission {
  id: number;
  role_id: number;
  role_code: string;
  category_id: number;
  category_code: string;
  department?: string | null;
  can_view: boolean;
  can_download: boolean;
  can_create: boolean;
  can_edit_draft: boolean;
  can_review: boolean;
  can_approve: boolean;
  can_retire: boolean;
}

export interface CreateUserRequest {
  user_id: string;
  full_name: string;
  email: string;
  department: string;
  title?: string;
  // Initial password is now backend-generated (see M2 commit 30568c1
  // "remove stale password field from user create form"). Kept optional to
  // preserve backward compatibility with older callers.
  password?: string;
  role_codes: string[];
  valid_from?: string | null;
  valid_until?: string | null;
}

export interface UpdateUserRequest {
  full_name: string;
  email: string;
  department: string;
  title?: string;
  valid_from?: string | null;
  valid_until?: string | null;
}

export interface UpdateRolesRequest {
  role_codes: string[];
}

export interface DisableUserRequest {
  reason: string;
}

export interface UpdateRoleRequest {
  role_name: string;
  description?: string;
}

export interface UpsertPermissionRequest {
  role_id: number;
  category_id: number;
  department?: string | null;
  can_view: boolean;
  can_download: boolean;
  can_create: boolean;
  can_edit_draft: boolean;
  can_review: boolean;
  can_approve: boolean;
  can_retire: boolean;
}

// M3 — Departments
export interface DepartmentAlias {
  id: number;
  aliasName: string;
  locale?: string | null;
}

export interface Department {
  id: number;
  deptCode: string;
  primaryName: string;
  source: string;
  active: boolean;
  createdAt: string;
  aliases: DepartmentAlias[];
}

export interface UpsertDepartmentRequest {
  deptCode: string;
  primaryName: string;
}

export interface UpsertAliasRequest {
  aliasName: string;
  locale?: string | null;
}

// M3 — Document Categories
export interface DocumentCategory {
  id: number;
  categoryCode: string;
  categoryName: string;
  description?: string | null;
  reviewPeriodMonths: number;
  qaMandatory: boolean;
  active: boolean;
  createdAt: string;
}

export interface UpsertCategoryRequest {
  categoryCode: string;
  categoryName: string;
  description?: string | null;
  reviewPeriodMonths: number;
  qaMandatory: boolean;
  active: boolean;
}

// M3 — Numbering Templates
export interface NumberingTemplate {
  id: number;
  categoryId: number;
  categoryCode: string;
  formatPattern: string;
  counterScope: 'PER_DEPT' | 'PER_PRODUCT' | 'PER_YEAR' | 'GLOBAL';
  updatedAt: string;
}

export interface UpsertNumberingTemplateRequest {
  categoryId: number;
  formatPattern: string;
  counterScope: 'PER_DEPT' | 'PER_PRODUCT' | 'PER_YEAR' | 'GLOBAL';
}

export interface NumberingPreviewRequest {
  categoryId: number;
  department?: string | null;
  projectCode?: string | null;
}

export interface NumberingPreviewResponse {
  nextDocNumber: string;
  nextSeq: number;
}

// M3 — Documents
export interface DocumentSummary {
  id: number;
  docNumber: string;
  categoryId: number;
  categoryCode: string;
  department: string;
  projectCode?: string | null;
  title: string;
  ownerId: number;
  confidential: boolean;
  createdAt: string;
}

export interface DocumentDetail extends DocumentSummary {
  versions: DocumentVersionSummary[];
}

export interface DocumentVersionSummary {
  id: number;
  documentId: number;
  revision?: number | null;
  state: string;
  title?: string | null;
  changeSummary?: string | null;
  reasonForChange?: string | null;
  sourceFileKey?: string | null;
  pdfStatus: string;
  createdAt: string;
  updatedAt: string;
  /**
   * M7.1 PR3 — populated by /me + per-row permission checks when available.
   * Drives the disabled+tooltip pattern (DS6) for "다운로드" actions.
   */
  canDownload?: boolean;
}

export interface DocumentFileSummary {
  id: number;
  versionId: number;
  fileType: string;
  minioKey: string;
  fileName: string;
  fileSizeBytes: number;
  contentType?: string | null;
  sha256Hash: string;
  uploadedAt: string;
}

export interface CreateDocumentRequest {
  categoryCode: string;
  department: string;
  projectCode?: string | null;
  title: string;
  confidential: boolean;
}

export interface CreateDocumentResponse {
  docId: number;
  versionId: number;
  docNumber: string;
  state: string;
}

// M7.1 — PDF viewer payloads (versionNumber + renditionKind shape)
// NOTE: A different `DocumentVersionSummary` already exists above for the
// document listing/detail flow. The PDF viewer endpoints return a slightly
// different projection, so we keep them as distinct types.
export interface PdfVersionSummary {
  versionId: number;
  versionNumber: string;
  status: string;
  pdfStatus: string | null;
  createdAt: string;
}

export interface DocumentFileInfo {
  id: number;
  versionId: number;
  fileType: string;
  fileName: string;
  renditionKind: string | null;
  stepNumber: number | null;
  sha256Hash: string;
}
