export type ClusterStatus = 'pending' | 'creating' | 'provisioning' | 'forming' | 'running' | 'degraded' | 'error' | 'deleting' | 'deleted';

// Connection info from API
export interface ConnectionInfo {
  hostname: string;
  port: number;
  username: string;
  credentialsAvailable: boolean;
}

// Credentials from /api/v1/clusters/{id}/credentials endpoint
export interface ClusterCredentials {
  hostname: string;
  port: number;
  pooledPort: number;
  database: string;
  username: string;
  password: string;
  connectionString: string;
  pooledConnectionString: string;
  sslMode: string;
  warning: string;
  retrievedAt: string;
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
  location: string | null;  // e.g., "fsn1", "hel1"
  flag: string | null;      // e.g., "🇩🇪", "🇫🇮"
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
  nodeRegions: string[];  // ["fsn1", "hel1", "nbg1"]
}

// Hetzner location info
export interface Location {
  id: string;       // "fsn1"
  name: string;     // "Falkenstein DC Park 1"
  city: string;     // "Falkenstein"
  country: string;  // "DE"
  countryName: string; // "Germany"
  flag: string;     // "🇩🇪"
  available: boolean; // true if server type is available at this location
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
