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
  password: string;
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
