import { Component, Input, Output, EventEmitter, OnInit, OnChanges, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface PitrPickerSelection {
  isoUtc: string;
  isValid: boolean;
  wasClamped: boolean;
  message: string | null;
}

export interface PitrPickerInterval {
  startTime: string;
  endTime: string;
}

@Component({
  selector: 'app-pitr-picker',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-4">
      <div class="text-sm text-muted-foreground">
        <span class="font-medium">Recovery window (UTC):</span>
        {{ formatDate(windowStartIso()) }} - {{ formatDate(windowEndIso()) }}
      </div>
      @if (effectiveIntervals().length > 1) {
        <div class="text-xs text-muted-foreground">
          Recoverable segments: {{ effectiveIntervals().length }} (gap times are not recoverable)
        </div>
      }

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
              [disabled]="!day.inMonth"
              [class]="getDayClasses(day)"
              class="h-9 w-full rounded text-sm transition-colors"
              type="button"
            >
              {{ day.date }}
            </button>
          }
        </div>
      </div>

      <div class="flex items-start gap-4">
        <label class="text-sm font-medium">Time (UTC):</label>
        <div class="space-y-2">
          <div class="flex items-center gap-2">
            <div class="flex items-center gap-1">
              <button
                type="button"
                class="h-8 w-8 rounded border border-input text-sm hover:bg-accent"
                (click)="nudgeHour(-1)"
                aria-label="Decrease hour UTC"
              >
                -
              </button>
              <input
                type="number"
                [ngModel]="selectedHour()"
                (ngModelChange)="onHourInput($event)"
                (keydown.arrowUp)="nudgeHour(1); $event.preventDefault()"
                (keydown.arrowDown)="nudgeHour(-1); $event.preventDefault()"
                min="0"
                max="23"
                class="w-16 rounded-md border border-input bg-background px-2 py-1.5 text-sm text-center"
                aria-label="Hour UTC"
              />
              <button
                type="button"
                class="h-8 w-8 rounded border border-input text-sm hover:bg-accent"
                (click)="nudgeHour(1)"
                aria-label="Increase hour UTC"
              >
                +
              </button>
            </div>
            <span class="text-lg">:</span>
            <div class="flex items-center gap-1">
              <button
                type="button"
                class="h-8 w-8 rounded border border-input text-sm hover:bg-accent"
                (click)="nudgeMinute(-1)"
                aria-label="Decrease minute UTC"
              >
                -
              </button>
              <input
                type="number"
                [ngModel]="selectedMinute()"
                (ngModelChange)="onMinuteInput($event)"
                (keydown.arrowUp)="nudgeMinute(1); $event.preventDefault()"
                (keydown.arrowDown)="nudgeMinute(-1); $event.preventDefault()"
                min="0"
                max="59"
                class="w-16 rounded-md border border-input bg-background px-2 py-1.5 text-sm text-center"
                aria-label="Minute UTC"
              />
              <button
                type="button"
                class="h-8 w-8 rounded border border-input text-sm hover:bg-accent"
                (click)="nudgeMinute(1)"
                aria-label="Increase minute UTC"
              >
                +
              </button>
            </div>
            <span class="text-lg">:</span>
            <div class="flex items-center gap-1">
              <button
                type="button"
                class="h-8 w-8 rounded border border-input text-sm hover:bg-accent"
                (click)="nudgeSecond(-1)"
                aria-label="Decrease second UTC"
              >
                -
              </button>
              <input
                type="number"
                [ngModel]="selectedSecond()"
                (ngModelChange)="onSecondInput($event)"
                (keydown.arrowUp)="nudgeSecond(1); $event.preventDefault()"
                (keydown.arrowDown)="nudgeSecond(-1); $event.preventDefault()"
                min="0"
                max="59"
                class="w-16 rounded-md border border-input bg-background px-2 py-1.5 text-sm text-center"
                aria-label="Second UTC"
              />
              <button
                type="button"
                class="h-8 w-8 rounded border border-input text-sm hover:bg-accent"
                (click)="nudgeSecond(1)"
                aria-label="Increase second UTC"
              >
                +
              </button>
            </div>
          </div>
          @if (selectedDayRangeLabel()) {
            <div class="text-xs text-muted-foreground">
              Allowed for selected date: {{ selectedDayRangeLabel() }}
            </div>
          }
          @if (selectionMessage()) {
            <div class="text-xs text-status-warning">
              {{ selectionMessage() }}
            </div>
          }
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
  @Input() intervals: PitrPickerInterval[] | null = null;
  @Output() selectionChange = new EventEmitter<PitrPickerSelection>();

  currentMonth = signal(this.currentUtcMonth());
  selectedDay = signal<Date | null>(null);
  selectedHour = signal(0);
  selectedMinute = signal(0);
  selectedSecond = signal(0);
  selectedDateTime = signal<Date | null>(null);
  selectionMessage = signal<string | null>(null);

  weekDays = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];

  effectiveIntervals = computed(() => this.buildEffectiveIntervals());
  windowStartIso = computed(() => {
    const intervals = this.effectiveIntervals();
    if (intervals.length === 0) return null;
    return intervals[0].start.toISOString();
  });
  windowEndIso = computed(() => {
    const intervals = this.effectiveIntervals();
    if (intervals.length === 0) return null;
    return intervals[intervals.length - 1].end.toISOString();
  });

  selectedDayRangeLabel = computed(() => {
    const day = this.selectedDay();
    if (!day) return '';
    const ranges = this.getAllowedRangesForDay(day);
    if (ranges.length === 0) return '';
    return ranges
      .map(range => `${this.formatTime(range.min)} - ${this.formatTime(range.max)}`)
      .join(', ') + ' UTC';
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

    const selected = this.selectedDay();

    for (let d = 1; d <= daysInMonth; d++) {
      const startOfDay = new Date(Date.UTC(year, monthIndex, d, 0, 0, 0));
      const endOfDay = new Date(Date.UTC(year, monthIndex, d, 23, 59, 59));

      const inRange = this.isDayWithinWindow(startOfDay, endOfDay);
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
    const candidateInput = this.initialTime || this.windowEndIso() || this.windowStartIso();
    const current = this.selectedDateTime();
    if (candidateInput && current) {
      const parsed = this.parseInputDate(candidateInput);
      if (parsed && parsed.toISOString() === current.toISOString()) {
        return;
      }
    }
    this.initializeFromInput();
  }

  private currentUtcMonth(): Date {
    const now = new Date();
    return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
  }

  private buildEffectiveIntervals(): EffectiveInterval[] {
    const parsedFromApi = (this.intervals ?? [])
      .map(interval => ({
        start: this.parseInputDate(interval.startTime),
        end: this.parseInputDate(interval.endTime)
      }))
      .filter((interval): interval is { start: Date; end: Date } =>
        interval.start !== null &&
        interval.end !== null &&
        interval.start.getTime() <= interval.end.getTime()
      )
      .map(interval => ({ start: interval.start, end: interval.end }));

    const fallbackStart = this.parseInputDate(this.earliestTime);
    const fallbackEnd = this.parseInputDate(this.latestTime);
    if (parsedFromApi.length === 0 && fallbackStart && fallbackEnd && fallbackStart.getTime() <= fallbackEnd.getTime()) {
      parsedFromApi.push({ start: fallbackStart, end: fallbackEnd });
    }

    if (parsedFromApi.length === 0) {
      return [];
    }

    parsedFromApi.sort((a, b) => a.start.getTime() - b.start.getTime());

    const merged: EffectiveInterval[] = [];
    let current: EffectiveInterval = {
      start: new Date(parsedFromApi[0].start.getTime()),
      end: new Date(parsedFromApi[0].end.getTime())
    };

    for (let i = 1; i < parsedFromApi.length; i++) {
      const next = parsedFromApi[i];
      if (next.start.getTime() <= current.end.getTime() + 1000) {
        if (next.end.getTime() > current.end.getTime()) {
          current.end = new Date(next.end.getTime());
        }
        continue;
      }

      merged.push(current);
      current = {
        start: new Date(next.start.getTime()),
        end: new Date(next.end.getTime())
      };
    }

    merged.push(current);
    return merged;
  }

  private getPreferredTimestamp(): string | null {
    if (this.initialTime) {
      return this.initialTime;
    }

    const intervals = this.effectiveIntervals();
    if (intervals.length > 0) {
      return intervals[intervals.length - 1].end.toISOString();
    }

    if (this.latestTime) {
      return this.latestTime;
    }

    return this.earliestTime;
  }

  private initializeFromInput(): void {
    const preferredTime = this.getPreferredTimestamp();
    if (preferredTime) {
      this.setFromTimestamp(preferredTime);
      this.recalculateSelection(false);
    } else {
      this.selectedDay.set(null);
      this.selectedHour.set(0);
      this.selectedMinute.set(0);
      this.selectedSecond.set(0);
      this.selectedDateTime.set(null);
      this.selectionMessage.set(null);
      this.currentMonth.set(this.currentUtcMonth());
      this.emitInvalid();
    }
  }

  private setFromTimestamp(timestamp: string): void {
    const dt = this.parseInputDate(timestamp);
    if (!dt) {
      this.selectedDay.set(null);
      this.currentMonth.set(this.currentUtcMonth());
      return;
    }

    this.selectedDay.set(new Date(Date.UTC(dt.getUTCFullYear(), dt.getUTCMonth(), dt.getUTCDate())));
    this.selectedHour.set(dt.getUTCHours());
    this.selectedMinute.set(dt.getUTCMinutes());
    this.selectedSecond.set(dt.getUTCSeconds());
    this.currentMonth.set(new Date(Date.UTC(dt.getUTCFullYear(), dt.getUTCMonth(), 1)));
  }

  selectDate(day: CalendarDay): void {
    if (!day.inMonth) return;

    const month = this.currentMonth();
    const date = new Date(Date.UTC(month.getUTCFullYear(), month.getUTCMonth(), day.date));
    this.selectedDay.set(date);
    this.recalculateSelection(true);
  }

  onHourInput(value: number | string): void {
    this.selectedHour.set(this.sanitizeInput(value, 0, 23));
    this.recalculateSelection(true);
  }

  onMinuteInput(value: number | string): void {
    this.selectedMinute.set(this.sanitizeInput(value, 0, 59));
    this.recalculateSelection(true);
  }

  onSecondInput(value: number | string): void {
    this.selectedSecond.set(this.sanitizeInput(value, 0, 59));
    this.recalculateSelection(true);
  }

  nudgeHour(delta: number): void {
    this.selectedHour.set(this.sanitizeInput(this.selectedHour() + delta, 0, 23));
    this.recalculateSelection(true);
  }

  nudgeMinute(delta: number): void {
    this.selectedMinute.set(this.sanitizeInput(this.selectedMinute() + delta, 0, 59));
    this.recalculateSelection(true);
  }

  nudgeSecond(delta: number): void {
    this.selectedSecond.set(this.sanitizeInput(this.selectedSecond() + delta, 0, 59));
    this.recalculateSelection(true);
  }

  private sanitizeInput(value: number | string, min: number, max: number): number {
    const numeric = typeof value === 'number' ? value : Number(value);
    if (!Number.isFinite(numeric)) {
      return min;
    }
    return Math.max(min, Math.min(max, Math.floor(numeric)));
  }

  private recalculateSelection(showValidationMessage: boolean): void {
    const day = this.selectedDay();
    if (!day) {
      this.selectedDateTime.set(null);
      this.selectionMessage.set(null);
      this.emitInvalid();
      return;
    }

    const candidate = new Date(Date.UTC(
      day.getUTCFullYear(),
      day.getUTCMonth(),
      day.getUTCDate(),
      this.selectedHour(),
      this.selectedMinute(),
      this.selectedSecond(),
      0
    ));

    const validation = this.validateCandidate(candidate);
    if (!validation.isValid) {
      this.selectedDateTime.set(candidate);
      this.selectionMessage.set(validation.message);
      this.emitInvalid(validation.message, candidate);
      return;
    }

    this.selectedDateTime.set(candidate);
    const message = showValidationMessage ? validation.message : null;
    this.selectionMessage.set(message);
    this.selectionChange.emit({
      isoUtc: candidate.toISOString(),
      isValid: true,
      wasClamped: false,
      message
    });
  }

  private emitInvalid(message: string | null = null, candidate: Date | null = null): void {
    this.selectionChange.emit({
      isoUtc: candidate ? candidate.toISOString() : '',
      isValid: false,
      wasClamped: false,
      message
    });
  }

  private validateCandidate(candidate: Date): CandidateValidation {
    const intervals = this.effectiveIntervals();
    if (intervals.length === 0) {
      return {
        isValid: false,
        message: 'PITR is unavailable because there are no recoverable intervals.'
      };
    }

    const windowStartMs = intervals[0].start.getTime();
    const windowEndMs = intervals[intervals.length - 1].end.getTime();
    const candidateMs = candidate.getTime();

    if (candidateMs < windowStartMs) {
      return {
        isValid: false,
        message: 'Selected time is before the earliest PITR time in this window.'
      };
    }

    if (candidateMs > windowEndMs) {
      return {
        isValid: false,
        message: 'Selected time is after the latest PITR time in this window.'
      };
    }

    const inRecoverableInterval = intervals.some(interval =>
      candidateMs >= interval.start.getTime() && candidateMs <= interval.end.getTime()
    );

    if (inRecoverableInterval) {
      return {
        isValid: true,
        message: null
      };
    }

    return {
      isValid: false,
      message: 'Selected time is in a PITR gap and is not recoverable right now.'
    };
  }

  private getAllowedRangesForDay(day: Date): { min: Date; max: Date }[] {
    const dayStart = new Date(Date.UTC(day.getUTCFullYear(), day.getUTCMonth(), day.getUTCDate(), 0, 0, 0));
    const dayEnd = new Date(Date.UTC(day.getUTCFullYear(), day.getUTCMonth(), day.getUTCDate(), 23, 59, 59));

    return this.effectiveIntervals()
      .filter(interval => interval.end.getTime() >= dayStart.getTime() && interval.start.getTime() <= dayEnd.getTime())
      .map(interval => {
        const min = interval.start.getTime() > dayStart.getTime() ? interval.start : dayStart;
        const max = interval.end.getTime() < dayEnd.getTime() ? interval.end : dayEnd;
        return {
          min: new Date(min.getTime()),
          max: new Date(max.getTime())
        };
      });
  }

  private parseInputDate(value: string | null): Date | null {
    if (!value) return null;
    const parsed = new Date(this.normalizeUtcInput(value));
    if (Number.isNaN(parsed.getTime())) return null;
    return parsed;
  }

  private normalizeUtcInput(value: string): string {
    if (/\dZ$|[+-]\d{2}:\d{2}$/.test(value)) {
      return value;
    }

    if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(\.\d+)?$/.test(value)) {
      return value.replace(' ', 'T') + 'Z';
    }

    if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$/.test(value)) {
      return value + 'Z';
    }

    return value;
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
    const earliestIso = this.windowStartIso();
    if (!earliestIso) return true;
    const earliest = this.parseInputDate(earliestIso);
    if (!earliest) return true;
    const current = this.currentMonth();

    const earliestMonthStart = Date.UTC(earliest.getUTCFullYear(), earliest.getUTCMonth(), 1);
    const currentMonthStart = Date.UTC(current.getUTCFullYear(), current.getUTCMonth(), 1);
    return currentMonthStart > earliestMonthStart;
  }

  canGoNext(): boolean {
    const latestIso = this.windowEndIso();
    if (!latestIso) return true;
    const latest = this.parseInputDate(latestIso);
    if (!latest) return true;
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
      classes.push('text-muted-foreground/70 hover:bg-accent cursor-pointer');
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
    const date = this.parseInputDate(dateStr);
    if (!date) return 'N/A';
    return this.formatUtc(date);
  }

  formatDateTime(date: Date): string {
    return this.formatUtc(date);
  }

  private formatTime(date: Date): string {
    return `${this.pad(date.getUTCHours())}:${this.pad(date.getUTCMinutes())}:${this.pad(date.getUTCSeconds())}`;
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

  private isDayWithinWindow(startOfDay: Date, endOfDay: Date): boolean {
    const intervals = this.effectiveIntervals();
    if (intervals.length === 0) {
      return false;
    }

    const windowStart = intervals[0].start.getTime();
    const windowEnd = intervals[intervals.length - 1].end.getTime();
    return endOfDay.getTime() >= windowStart && startOfDay.getTime() <= windowEnd;
  }
}

interface CalendarDay {
  date: number;
  inMonth: boolean;
  inRange: boolean;
  isSelected: boolean;
  isToday: boolean;
}

interface CandidateValidation {
  isValid: boolean;
  message: string | null;
}

interface EffectiveInterval {
  start: Date;
  end: Date;
}
