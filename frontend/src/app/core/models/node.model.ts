import { User } from './user.model';

// Platform Stats
export interface PlatformStats {
  total_clusters: number;
  running_clusters: number;
  total_users: number;
}

// Response wrappers
export interface AdminClustersResponse {
  clusters: AdminCluster[];
  count: number;
}

export interface AdminUsersResponse {
  users: User[];
  count: number;
}

// Admin Cluster Types
export interface AdminCluster {
  id: string;
  name: string;
  slug: string;
  status: string;
  plan: string;
  owner_id?: string;
  owner_email?: string;
  postgres_version: string;
  node_count: number;
  node_size: string;
  region: string;
  created_at: string;
  updated_at: string;
}

export interface AdminClusterDetail {
  id: string;
  name: string;
  slug: string;
  status: string;
  plan: string;
  owner_id?: string;
  owner_email?: string;
  postgres_version: string;
  node_count: number;
  node_size: string;
  region: string;
  connection?: {
    hostname: string;
    port: number;
    username: string;
    credentialsAvailable: boolean;
  };
  resources?: {
    storage_gb: number;
    memory_mb: number;
    cpu_cores: number;
  };
  nodes?: VpsNodeInfo[];
  error_message?: string;
  provisioning_step?: string;
  provisioning_progress?: number;
  total_steps?: number;
  created_at: string;
  updated_at: string;
}

export interface VpsNodeInfo {
  id: string;
  name: string;
  public_ip: string;
  status: string;
  role: string;
  server_type: string;
  location: string;
}
