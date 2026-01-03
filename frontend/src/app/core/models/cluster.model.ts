export type ClusterStatus = 'pending' | 'creating' | 'provisioning' | 'forming' | 'running' | 'degraded' | 'error' | 'deleting';

// Connection info from API
export interface ConnectionInfo {
  hostname: string;
  port: number;
  username: string;
  password?: string;
  connectionString?: string;
}

// Resources info
export interface ClusterResources {
  storageGb: number;
  memoryMb: number;
  cpuCores: number;
}

// Node info
export interface ClusterNode {
  id: string;
  name: string;
  publicIp: string;
  status: string;
  role: string;
  serverType: string;
  location: string;
}

// API cluster response structure (flat)
export interface ClusterApiResponse {
  id: string;
  name: string;
  slug: string;
  plan: string;
  status: ClusterStatus;
  postgresVersion: string;
  nodeCount: number;
  nodeSize: string;
  region: string;
  connection: ConnectionInfo | null;
  resources: ClusterResources;
  nodes: ClusterNode[] | null;
  errorMessage: string | null;
  provisioningStep: string | null;
  provisioningProgress: number | null;
  totalSteps: number | null;
  createdAt: string;
  updatedAt: string;
}

// Combined cluster for display
export interface Cluster {
  id: string;
  name: string;
  slug: string;
  plan: string;
  status: ClusterStatus;
  postgresVersion: string;
  nodeCount: number;
  nodeSize: string;
  region: string;
  connection: ConnectionInfo | null;
  resources: ClusterResources;
  nodes: ClusterNode[];
  errorMessage: string | null;
  provisioningStep: string | null;
  provisioningProgress: number | null;
  totalSteps: number | null;
  createdAt: string;
  updatedAt: string;
}

// API Response types
export interface ClusterListResponse {
  clusters: ClusterApiResponse[] | null;
  count: number;
}

// Patroni status from health check
export interface PatroniStatus {
  leader: string | null;
  replicas: number;
  haEnabled: boolean;
}

// Node health from health check
export interface NodeHealth {
  name: string;
  ip: string;
  role: string | null;
  state: string | null;
  reachable: boolean;
  lagBytes: number | null;
}

// Health response from backend
export interface ClusterHealth {
  clusterId: string;
  clusterSlug: string;
  overallStatus: 'healthy' | 'degraded' | 'unhealthy';
  patroni: PatroniStatus;
  nodes: NodeHealth[];
}

export interface CreateClusterRequest {
  name: string;
  nodeCount: number;
  nodeSize: string;
  region: string;
  postgresVersion: string;
}

// Helper to convert API response to Cluster
export function toCluster(item: ClusterApiResponse): Cluster {
  return {
    id: item.id,
    name: item.name,
    slug: item.slug,
    plan: item.plan,
    status: item.status,
    postgresVersion: item.postgresVersion,
    nodeCount: item.nodeCount,
    nodeSize: item.nodeSize,
    region: item.region,
    connection: item.connection,
    resources: item.resources,
    nodes: item.nodes || [],
    errorMessage: item.errorMessage,
    provisioningStep: item.provisioningStep,
    provisioningProgress: item.provisioningProgress,
    totalSteps: item.totalSteps,
    createdAt: item.createdAt,
    updatedAt: item.updatedAt,
  };
}
