import { Component, Input, Output, EventEmitter, OnInit, OnChanges, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-pitr-picker',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-4">
      <div class="text-sm text-muted-foreground">
        <span class="font-medium">Recovery window (UTC):</span>
        {{ formatDate(earliestTime) }} - {{ formatDate(latestTime) }}
      </div>

      <div class="border rounded-lg p-4">
        <div class="flex items-center justify-between mb-4">
          <button
            (click)="previousMonth()"
            [disabled]="!canGoPrevious()"
            class="p-1 hover:bg-accent rounded disabled:opacity-50 disabled:cursor-not-allowed"
            type="button"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <span class="font-medium">{{ monthYearLabel() }} (UTC)</span>
          <button
            (click)="nextMonth()"
            [disabled]="!canGoNext()"
            class="p-1 hover:bg-accent rounded disabled:opacity-50 disabled:cursor-not-allowed"
            type="button"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>

        <div class="grid grid-cols-7 gap-1 mb-2">
          @for (day of weekDays; track day) {
            <div class="text-center text-xs font-medium text-muted-foreground py-1">{{ day }}</div>
          }
        </div>

        <div class="grid grid-cols-7 gap-1">
          @for (day of calendarDays(); track $index) {
            <button
              (click)="selectDate(day)"
              [disabled]="!day.inMonth || !day.inRange"
              [class]="getDayClasses(day)"
              class="h-9 w-full rounded text-sm transition-colors"
              type="button"
            >
              {{ day.date }}
            </button>
          }
        </div>
      </div>

      <div class="flex items-center gap-4">
        <label class="text-sm font-medium">Time (UTC):</label>
        <div class="flex items-center gap-2">
          <input
            type="number"
            [(ngModel)]="selectedHour"
            (ngModelChange)="onTimeChange()"
            min="0"
            max="23"
            class="w-16 rounded-md border border-input bg-background px-2 py-1.5 text-sm text-center"
          />
          <span class="text-lg">:</span>
          <input
            type="number"
            [(ngModel)]="selectedMinute"
            (ngModelChange)="onTimeChange()"
            min="0"
            max="59"
            class="w-16 rounded-md border border-input bg-background px-2 py-1.5 text-sm text-center"
          />
          <span class="text-lg">:</span>
          <input
            type="number"
            [(ngModel)]="selectedSecond"
            (ngModelChange)="onTimeChange()"
            min="0"
            max="59"
            class="w-16 rounded-md border border-input bg-background px-2 py-1.5 text-sm text-center"
          />
        </div>
      </div>

      @if (selectedDateTime()) {
        <div class="p-3 bg-muted rounded-lg">
          <div class="text-sm font-medium">Selected recovery point (UTC):</div>
          <div class="text-lg font-mono">{{ formatDateTime(selectedDateTime()!) }}</div>
        </div>
      }
    </div>
  `
})
export class PitrPickerComponent implements OnInit, OnChanges {
  @Input() earliestTime: string | null = null;
  @Input() latestTime: string | null = null;
  @Input() initialTime: string | null = null;
  @Output() timeSelected = new EventEmitter<string>();

  currentMonth = signal(this.currentUtcMonth());
  selectedDay = signal<Date | null>(null);
  selectedHour = 0;
  selectedMinute = 0;
  selectedSecond = 0;

  weekDays = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];

  selectedDateTime = computed(() => {
    const day = this.selectedDay();
    if (!day) return null;

    return new Date(Date.UTC(
      day.getUTCFullYear(),
      day.getUTCMonth(),
      day.getUTCDate(),
      this.selectedHour,
      this.selectedMinute,
      this.selectedSecond,
      0
    ));
  });

  monthYearLabel = computed(() => {
    const date = this.currentMonth();
    return date.toLocaleDateString('en-US', { month: 'long', year: 'numeric', timeZone: 'UTC' });
  });

  calendarDays = computed(() => {
    const month = this.currentMonth();
    const year = month.getUTCFullYear();
    const monthIndex = month.getUTCMonth();

    const firstDay = new Date(Date.UTC(year, monthIndex, 1));
    const lastDay = new Date(Date.UTC(year, monthIndex + 1, 0));
    const startPadding = firstDay.getUTCDay();
    const daysInMonth = lastDay.getUTCDate();

    const days: CalendarDay[] = [];

    const prevMonth = new Date(Date.UTC(year, monthIndex, 0));
    const prevMonthDays = prevMonth.getUTCDate();
    for (let i = startPadding - 1; i >= 0; i--) {
      days.push({
        date: prevMonthDays - i,
        inMonth: false,
        inRange: false,
        isSelected: false,
        isToday: false
      });
    }

    const now = new Date();
    const todayYear = now.getUTCFullYear();
    const todayMonth = now.getUTCMonth();
    const todayDay = now.getUTCDate();

    const earliest = this.earliestTime ? new Date(this.earliestTime) : null;
    const latest = this.latestTime ? new Date(this.latestTime) : null;
    const selected = this.selectedDay();

    for (let d = 1; d <= daysInMonth; d++) {
      const startOfDay = new Date(Date.UTC(year, monthIndex, d, 0, 0, 0));
      const endOfDay = new Date(Date.UTC(year, monthIndex, d, 23, 59, 59));

      const inRange = (!earliest || endOfDay >= earliest) && (!latest || startOfDay <= latest);
      const isSelected = !!selected &&
        selected.getUTCDate() === d &&
        selected.getUTCMonth() === monthIndex &&
        selected.getUTCFullYear() === year;
      const isToday =
        todayDay === d &&
        todayMonth === monthIndex &&
        todayYear === year;

      days.push({
        date: d,
        inMonth: true,
        inRange,
        isSelected,
        isToday
      });
    }

    const remaining = 42 - days.length;
    for (let d = 1; d <= remaining; d++) {
      days.push({
        date: d,
        inMonth: false,
        inRange: false,
        isSelected: false,
        isToday: false
      });
    }

    return days;
  });

  ngOnInit(): void {
    this.initializeFromInput();
  }

  ngOnChanges(): void {
    this.initializeFromInput();
  }

  private currentUtcMonth(): Date {
    const now = new Date();
    return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
  }

  private initializeFromInput(): void {
    if (this.initialTime) {
      this.setFromTimestamp(this.initialTime);
    } else if (this.latestTime) {
      this.setFromTimestamp(this.latestTime);
    } else {
      this.selectedDay.set(null);
      this.currentMonth.set(this.currentUtcMonth());
    }
    this.emitSelection();
  }

  private setFromTimestamp(timestamp: string): void {
    const dt = new Date(timestamp);
    if (Number.isNaN(dt.getTime())) {
      this.selectedDay.set(null);
      this.currentMonth.set(this.currentUtcMonth());
      return;
    }

    this.selectedDay.set(new Date(Date.UTC(dt.getUTCFullYear(), dt.getUTCMonth(), dt.getUTCDate())));
    this.selectedHour = dt.getUTCHours();
    this.selectedMinute = dt.getUTCMinutes();
    this.selectedSecond = dt.getUTCSeconds();
    this.currentMonth.set(new Date(Date.UTC(dt.getUTCFullYear(), dt.getUTCMonth(), 1)));
  }

  selectDate(day: CalendarDay): void {
    if (!day.inMonth || !day.inRange) return;

    const month = this.currentMonth();
    const date = new Date(Date.UTC(month.getUTCFullYear(), month.getUTCMonth(), day.date));
    this.selectedDay.set(date);
    this.emitSelection();
  }

  onTimeChange(): void {
    this.selectedHour = Math.max(0, Math.min(23, this.selectedHour || 0));
    this.selectedMinute = Math.max(0, Math.min(59, this.selectedMinute || 0));
    this.selectedSecond = Math.max(0, Math.min(59, this.selectedSecond || 0));
    this.emitSelection();
  }

  private emitSelection(): void {
    const dt = this.selectedDateTime();
    if (dt) {
      this.timeSelected.emit(dt.toISOString());
    }
  }

  previousMonth(): void {
    const current = this.currentMonth();
    this.currentMonth.set(new Date(Date.UTC(current.getUTCFullYear(), current.getUTCMonth() - 1, 1)));
  }

  nextMonth(): void {
    const current = this.currentMonth();
    this.currentMonth.set(new Date(Date.UTC(current.getUTCFullYear(), current.getUTCMonth() + 1, 1)));
  }

  canGoPrevious(): boolean {
    if (!this.earliestTime) return true;
    const earliest = new Date(this.earliestTime);
    const current = this.currentMonth();

    const earliestMonthStart = Date.UTC(earliest.getUTCFullYear(), earliest.getUTCMonth(), 1);
    const currentMonthStart = Date.UTC(current.getUTCFullYear(), current.getUTCMonth(), 1);
    return currentMonthStart > earliestMonthStart;
  }

  canGoNext(): boolean {
    if (!this.latestTime) return true;
    const latest = new Date(this.latestTime);
    const current = this.currentMonth();

    const latestMonthStart = Date.UTC(latest.getUTCFullYear(), latest.getUTCMonth(), 1);
    const currentMonthStart = Date.UTC(current.getUTCFullYear(), current.getUTCMonth(), 1);
    return currentMonthStart < latestMonthStart;
  }

  getDayClasses(day: CalendarDay): string {
    const classes = [];

    if (!day.inMonth) {
      classes.push('text-muted-foreground/30');
    } else if (!day.inRange) {
      classes.push('text-muted-foreground/50 cursor-not-allowed');
    } else {
      classes.push('hover:bg-accent cursor-pointer');
    }

    if (day.isSelected) {
      classes.push('bg-primary text-primary-foreground hover:bg-primary/90');
    }

    if (day.isToday && !day.isSelected) {
      classes.push('border border-primary');
    }

    return classes.join(' ');
  }

  formatDate(dateStr: string | null): string {
    if (!dateStr) return 'N/A';
    const date = new Date(dateStr);
    if (Number.isNaN(date.getTime())) return 'N/A';
    return this.formatUtc(date);
  }

  formatDateTime(date: Date): string {
    return this.formatUtc(date);
  }

  private formatUtc(date: Date): string {
    const year = date.getUTCFullYear();
    const month = this.pad(date.getUTCMonth() + 1);
    const day = this.pad(date.getUTCDate());
    const hour = this.pad(date.getUTCHours());
    const minute = this.pad(date.getUTCMinutes());
    const second = this.pad(date.getUTCSeconds());
    return `${year}-${month}-${day} ${hour}:${minute}:${second} UTC`;
  }

  private pad(value: number): string {
    return value.toString().padStart(2, '0');
  }
}

interface CalendarDay {
  date: number;
  inMonth: boolean;
  inRange: boolean;
  isSelected: boolean;
  isToday: boolean;
}
