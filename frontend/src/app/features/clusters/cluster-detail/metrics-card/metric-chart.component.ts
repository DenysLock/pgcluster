import {
  Component,
  Input,
  OnChanges,
  OnDestroy,
  AfterViewInit,
  SimpleChanges,
  ElementRef,
  ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { createChart, IChartApi, ISeriesApi, LineSeries, LineData, UTCTimestamp, ColorType } from 'lightweight-charts';
import { MetricSeries } from '../../../../core/models';

@Component({
  selector: 'app-metric-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="border border-border bg-bg-secondary p-4">
      <div class="flex items-center justify-between mb-3">
        <h4 class="text-xs font-semibold uppercase tracking-wider text-muted-foreground">{{ title }}</h4>
        @if (currentValue !== null) {
          <span class="text-lg font-semibold tabular-nums text-foreground">
            {{ formatValue(currentValue) }}{{ unit ? ' ' + unit : '' }}
          </span>
        }
      </div>
      <div #chartContainer class="h-40"></div>
      <!-- Legend -->
      @if (series.length > 1) {
        <div class="flex flex-wrap gap-3 mt-2 text-xs">
          @for (s of sortedSeries; track s.nodeName; let i = $index) {
            <div class="flex items-center gap-1.5">
              <span
                class="w-2 h-2"
                [style.background-color]="getSeriesColor(i)"
              ></span>
              <span class="text-muted-foreground">
                {{ s.nodeName }}
                @if (s.nodeRole === 'leader') {
                  <span class="text-neon-green">(L)</span>
                }
              </span>
            </div>
          }
        </div>
      }
      <!-- Empty state -->
      @if (series.length === 0 || allSeriesEmpty()) {
        <div class="absolute inset-0 flex items-center justify-center text-muted-foreground text-sm">
          No data available
        </div>
      }
    </div>
  `,
  styles: [`
    :host {
      display: block;
      position: relative;
    }
  `]
})
export class MetricChartComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input({ required: true }) title!: string;
  @Input() unit: string = '';
  @Input() series: MetricSeries[] = [];
  @Input() maxValue?: number;

  @ViewChild('chartContainer') chartContainer!: ElementRef<HTMLDivElement>;

  private chart: IChartApi | null = null;
  private lineSeries: ISeriesApi<'Line'>[] = [];
  private resizeObserver?: ResizeObserver;

  currentValue: number | null = null;
  sortedSeries: MetricSeries[] = [];

  private readonly colors = [
    '#00ff00', // neon-green (leader)
    '#00aaff', // neon-cyan (replica 1)
    '#aa00ff', // neon-purple (replica 2)
    '#ffaa00', // status-warning
    '#ff3333', // status-error
  ];

  ngAfterViewInit(): void {
    this.initChart();
    this.updateSeries();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['series'] && this.chart) {
      this.updateSeries();
    }
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    if (this.chart) {
      this.chart.remove();
      this.chart = null;
    }
  }

  private initChart(): void {
    if (!this.chartContainer?.nativeElement) return;

    this.chart = createChart(this.chartContainer.nativeElement, {
      width: this.chartContainer.nativeElement.clientWidth,
      height: 160,
      layout: {
        background: { type: ColorType.Solid, color: 'transparent' },
        textColor: '#666666', // muted-foreground
      },
      grid: {
        vertLines: { visible: false },
        horzLines: { color: '#22222233' }, // border with opacity
      },
      rightPriceScale: {
        borderVisible: false,
        scaleMargins: { top: 0.1, bottom: 0.1 },
      },
      timeScale: {
        borderVisible: false,
        timeVisible: true,
        secondsVisible: false,
      },
      crosshair: {
        vertLine: { labelVisible: false },
        horzLine: { labelVisible: true },
      },
      handleScroll: false,
      handleScale: false,
    });

    // Handle resize
    this.resizeObserver = new ResizeObserver(() => {
      if (this.chart && this.chartContainer?.nativeElement) {
        this.chart.applyOptions({
          width: this.chartContainer.nativeElement.clientWidth,
        });
      }
    });
    this.resizeObserver.observe(this.chartContainer.nativeElement);
  }

  private updateSeries(): void {
    if (!this.chart) return;

    // Remove old series
    this.lineSeries.forEach(s => this.chart!.removeSeries(s));
    this.lineSeries = [];

    // Sort series: leader first
    this.sortedSeries = [...this.series].sort((a, b) => {
      if (a.nodeRole === 'leader') return -1;
      if (b.nodeRole === 'leader') return 1;
      return a.nodeName.localeCompare(b.nodeName);
    });

    this.sortedSeries.forEach((s, i) => {
      const lineSeries = this.chart!.addSeries(LineSeries, {
        color: this.getSeriesColor(i),
        lineWidth: 2,
        crosshairMarkerVisible: true,
        priceLineVisible: false,
        lastValueVisible: false,
      });

      const data: LineData[] = s.data.map(d => ({
        time: d.time as UTCTimestamp,
        value: d.value,
      }));

      lineSeries.setData(data);
      this.lineSeries.push(lineSeries);

      // Set current value from last data point of leader (or first series)
      if ((s.nodeRole === 'leader' || i === 0) && s.data.length > 0) {
        this.currentValue = s.data[s.data.length - 1].value;
      }
    });

    if (this.lineSeries.length > 0) {
      this.chart.timeScale().fitContent();
    }
  }

  getSeriesColor(index: number): string {
    return this.colors[index % this.colors.length];
  }

  formatValue(value: number): string {
    if (this.unit === 'bytes') {
      return this.formatBytes(value);
    }
    if (value >= 1000) {
      return (value / 1000).toFixed(1) + 'k';
    }
    return value.toFixed(1);
  }

  private formatBytes(bytes: number): string {
    if (bytes === 0) return '0';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(Math.abs(bytes)) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }

  allSeriesEmpty(): boolean {
    return this.series.every(s => s.data.length === 0);
  }
}
