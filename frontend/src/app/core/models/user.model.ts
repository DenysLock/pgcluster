import { AdminCluster } from './node.model';

export interface User {
  id: string;
  email: string;
  first_name?: string;
  last_name?: string;
  role: 'user' | 'admin';
  active: boolean;
  created_at: string;
  updated_at: string;
}

export interface UserDetail extends User {
  clusters: AdminCluster[];
  cluster_count: number;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface CreateUserRequest {
  email: string;
  password: string;
  first_name?: string;
  last_name?: string;
  role?: string;
}

export interface ResetPasswordRequest {
  new_password: string;
}

// Audit Log types
export interface AuditLog {
  id: string;
  timestamp: string;
  user_id: string | null;
  user_email: string | null;
  action: string;
  resource_type: string | null;
  resource_id: string | null;
  details: Record<string, any> | null;
  ip_address: string | null;
}

export interface AuditLogListResponse {
  logs: AuditLog[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}
