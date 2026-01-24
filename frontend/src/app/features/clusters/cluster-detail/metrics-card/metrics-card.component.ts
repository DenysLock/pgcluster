import { Component, Input, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { interval, Subscription } from 'rxjs';
import { MetricsService } from '../../../../core/services/metrics.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { ClusterMetrics, TimeRange } from '../../../../core/models';
import { MetricChartComponent } from './metric-chart.component';

@Component({
  selector: 'app-metrics-card',
  standalone: true,
  imports: [
    CommonModule,
    MetricChartComponent
  ],
  template: `
    <div class="card">
      <div class="card-header">Metrics</div>
      <div class="space-y-6">
        <!-- Time Range Selector -->
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2 text-sm text-muted-foreground">
            @if (loading() && metrics()) {
              <span class="spinner w-4 h-4"></span>
              <span>Refreshing...</span>
            } @else if (metrics()) {
              <span>Last updated: {{ formatTime(metrics()!.queryTime) }}</span>
            }
          </div>
          <div class="flex items-center gap-1 bg-bg-tertiary border border-border p-1">
            @for (range of timeRanges; track range.value) {
              <button
                (click)="selectRange(range.value)"
                [class]="getButtonClass(range.value)"
                [disabled]="loading() && !metrics()"
              >
                {{ range.label }}
              </button>
            }
          </div>
        </div>

        @if (loading() && !metrics()) {
          <!-- Initial loading state -->
          <div class="flex items-center justify-center py-16">
            <span class="spinner w-8 h-8"></span>
          </div>
        } @else if (error() && !metrics()) {
          <!-- Error state (no data) -->
          <div class="text-center py-12">
            <svg class="w-12 h-12 mx-auto mb-3 text-muted-foreground/50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <p class="text-muted-foreground mb-2">{{ error() }}</p>
            <button
              (click)="loadMetrics()"
              class="text-sm text-neon-green hover:underline"
            >
              Try again
            </button>
          </div>
        } @else if (metrics()) {
          <!-- Charts Grid -->
          <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            <app-metric-chart
              title="CPU Usage"
              unit="%"
              [series]="metrics()!.cpu"
              [maxValue]="100"
            />
            <app-metric-chart
              title="Memory Usage"
              unit="%"
              [series]="metrics()!.memory"
              [maxValue]="100"
            />
            <app-metric-chart
              title="Disk Usage"
              unit="%"
              [series]="metrics()!.disk"
              [maxValue]="100"
            />
            <app-metric-chart
              title="Active Connections"
              unit=""
              [series]="metrics()!.connections"
            />
            <app-metric-chart
              title="Queries per Second"
              unit="qps"
              [series]="metrics()!.qps"
            />
            <app-metric-chart
              title="Replication Lag"
              unit="s"
              [series]="metrics()!.replicationLag"
            />
          </div>

          <!-- No Data Warning -->
          @if (hasNoData()) {
            <div class="bg-status-warning/10 border border-status-warning p-4">
              <div class="flex items-start gap-3">
                <svg class="w-5 h-5 text-status-warning shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
                <div>
                  <p class="font-semibold text-status-warning">Limited metrics available</p>
                  <p class="text-sm text-status-warning/80 mt-1">
                    Some metrics may not be available yet. This can happen if the cluster was recently created or if Prometheus hasn't scraped the metrics yet.
                  </p>
                </div>
              </div>
            </div>
          }
        }
      </div>
    </div>
  `
})
export class MetricsCardComponent implements OnInit, OnDestroy {
  @Input({ required: true }) clusterId!: string;
  @Input() isClusterRunning: boolean = false;

  loading = signal(false);
  error = signal<string | null>(null);
  metrics = signal<ClusterMetrics | null>(null);
  selectedRange = signal<TimeRange>('1h');

  readonly timeRanges: { value: TimeRange; label: string }[] = [
    { value: '1h', label: '1H' },
    { value: '6h', label: '6H' },
    { value: '24h', label: '24H' },
    { value: '7d', label: '7D' },
  ];

  private pollingSubscription?: Subscription;
  private readonly REFRESH_INTERVAL = 30_000; // 30 seconds

  constructor(
    private metricsService: MetricsService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    if (this.isClusterRunning) {
      this.loadMetrics();
      this.startPolling();
    }
  }

  ngOnDestroy(): void {
    this.pollingSubscription?.unsubscribe();
  }

  selectRange(range: TimeRange): void {
    if (this.selectedRange() === range) return;
    this.selectedRange.set(range);
    this.loadMetrics();
  }

  loadMetrics(): void {
    this.loading.set(true);
    this.error.set(null);

    this.metricsService.getClusterMetrics(this.clusterId, this.selectedRange()).subscribe({
      next: (metrics) => {
        this.metrics.set(metrics);
        this.loading.set(false);
      },
      error: (err) => {
        const message = err.error?.message || err.message || 'Failed to load metrics';
        this.error.set(message);
        this.loading.set(false);
        // Don't show toast for every refresh failure
        if (!this.metrics()) {
          this.notificationService.error(message);
        }
      }
    });
  }

  private startPolling(): void {
    this.pollingSubscription = interval(this.REFRESH_INTERVAL).subscribe(() => {
      if (this.isClusterRunning) {
        this.loadMetrics();
      }
    });
  }

  getButtonClass(range: TimeRange): string {
    const base = 'px-3 py-1.5 text-sm font-semibold uppercase tracking-wide transition-colors disabled:opacity-50';
    if (this.selectedRange() === range) {
      return `${base} bg-neon-green text-bg-primary`;
    }
    return `${base} text-muted-foreground hover:text-foreground`;
  }

  formatTime(dateString: string): string {
    return new Date(dateString).toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  }

  hasNoData(): boolean {
    const m = this.metrics();
    if (!m) return false;
    return (
      m.cpu.length === 0 &&
      m.memory.length === 0 &&
      m.disk.length === 0 &&
      m.connections.length === 0 &&
      m.qps.length === 0 &&
      m.replicationLag.length === 0
    );
  }
}
