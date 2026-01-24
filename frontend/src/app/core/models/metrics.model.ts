/**
 * A single data point in a time series.
 * Format matches TradingView Lightweight Charts expectations.
 */
export interface DataPoint {
  time: number;  // Unix timestamp in seconds
  value: number;
}

/**
 * A single time-series for one node
 */
export interface MetricSeries {
  nodeName: string;
  nodeRole: 'leader' | 'replica' | string;
  nodeIp: string;
  data: DataPoint[];
}

/**
 * Response from the metrics API endpoint
 */
export interface ClusterMetrics {
  clusterId: string;
  clusterSlug: string;
  queryTime: string;
  timeRange: TimeRange;
  stepSeconds: number;
  cpu: MetricSeries[];
  memory: MetricSeries[];
  disk: MetricSeries[];
  connections: MetricSeries[];
  qps: MetricSeries[];
  replicationLag: MetricSeries[];
}

/**
 * Supported time range options
 */
export type TimeRange = '1h' | '6h' | '24h' | '7d';
