import { Component, Input, Output, EventEmitter, OnInit, OnChanges, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-pitr-picker',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-4">
      <!-- Recovery window info -->
      <div class="text-sm text-muted-foreground">
        <span class="font-medium">Recovery window:</span>
        {{ formatDate(earliestTime) }} - {{ formatDate(latestTime) }}
      </div>

      <!-- Calendar -->
      <div class="border rounded-lg p-4">
        <!-- Month/Year navigation -->
        <div class="flex items-center justify-between mb-4">
          <button
            (click)="previousMonth()"
            [disabled]="!canGoPrevious()"
            class="p-1 hover:bg-accent rounded disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <span class="font-medium">{{ monthYearLabel() }}</span>
          <button
            (click)="nextMonth()"
            [disabled]="!canGoNext()"
            class="p-1 hover:bg-accent rounded disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>

        <!-- Day headers -->
        <div class="grid grid-cols-7 gap-1 mb-2">
          @for (day of weekDays; track day) {
            <div class="text-center text-xs font-medium text-muted-foreground py-1">{{ day }}</div>
          }
        </div>

        <!-- Calendar days -->
        <div class="grid grid-cols-7 gap-1">
          @for (day of calendarDays(); track $index) {
            <button
              (click)="selectDate(day)"
              [disabled]="!day.inMonth || !day.inRange"
              [class]="getDayClasses(day)"
              class="h-9 w-full rounded text-sm transition-colors"
            >
              {{ day.date }}
            </button>
          }
        </div>
      </div>

      <!-- Time picker -->
      <div class="flex items-center gap-4">
        <label class="text-sm font-medium">Time:</label>
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

      <!-- Selected datetime display -->
      @if (selectedDateTime()) {
        <div class="p-3 bg-muted rounded-lg">
          <div class="text-sm font-medium">Selected recovery point:</div>
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

  currentMonth = signal(new Date());
  selectedDay = signal<Date | null>(null);
  selectedHour = 0;
  selectedMinute = 0;
  selectedSecond = 0;

  weekDays = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];

  selectedDateTime = computed(() => {
    const day = this.selectedDay();
    if (!day) return null;

    const dt = new Date(day);
    dt.setHours(this.selectedHour, this.selectedMinute, this.selectedSecond, 0);
    return dt;
  });

  monthYearLabel = computed(() => {
    const date = this.currentMonth();
    return date.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
  });

  calendarDays = computed(() => {
    const month = this.currentMonth();
    const year = month.getFullYear();
    const monthIndex = month.getMonth();

    const firstDay = new Date(year, monthIndex, 1);
    const lastDay = new Date(year, monthIndex + 1, 0);
    const startPadding = firstDay.getDay();
    const daysInMonth = lastDay.getDate();

    const days: CalendarDay[] = [];

    // Previous month padding
    const prevMonth = new Date(year, monthIndex, 0);
    const prevMonthDays = prevMonth.getDate();
    for (let i = startPadding - 1; i >= 0; i--) {
      days.push({
        date: prevMonthDays - i,
        inMonth: false,
        inRange: false,
        isSelected: false,
        isToday: false
      });
    }

    // Current month days
    const today = new Date();
    const earliest = this.earliestTime ? new Date(this.earliestTime) : null;
    const latest = this.latestTime ? new Date(this.latestTime) : null;
    const selected = this.selectedDay();

    for (let d = 1; d <= daysInMonth; d++) {
      const date = new Date(year, monthIndex, d);
      const endOfDay = new Date(year, monthIndex, d, 23, 59, 59);
      const startOfDay = new Date(year, monthIndex, d, 0, 0, 0);

      const inRange = (!earliest || endOfDay >= earliest) && (!latest || startOfDay <= latest);
      const isSelected = selected &&
        selected.getDate() === d &&
        selected.getMonth() === monthIndex &&
        selected.getFullYear() === year;
      const isToday =
        today.getDate() === d &&
        today.getMonth() === monthIndex &&
        today.getFullYear() === year;

      days.push({
        date: d,
        inMonth: true,
        inRange,
        isSelected: isSelected || false,
        isToday
      });
    }

    // Next month padding
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

  private initializeFromInput(): void {
    if (this.initialTime) {
      const dt = new Date(this.initialTime);
      this.selectedDay.set(new Date(dt.getFullYear(), dt.getMonth(), dt.getDate()));
      this.selectedHour = dt.getHours();
      this.selectedMinute = dt.getMinutes();
      this.selectedSecond = dt.getSeconds();
      this.currentMonth.set(new Date(dt.getFullYear(), dt.getMonth(), 1));
    } else if (this.latestTime) {
      const dt = new Date(this.latestTime);
      this.selectedDay.set(new Date(dt.getFullYear(), dt.getMonth(), dt.getDate()));
      this.selectedHour = dt.getHours();
      this.selectedMinute = dt.getMinutes();
      this.selectedSecond = dt.getSeconds();
      this.currentMonth.set(new Date(dt.getFullYear(), dt.getMonth(), 1));
    }
    this.emitSelection();
  }

  selectDate(day: CalendarDay): void {
    if (!day.inMonth || !day.inRange) return;

    const month = this.currentMonth();
    const date = new Date(month.getFullYear(), month.getMonth(), day.date);
    this.selectedDay.set(date);
    this.emitSelection();
  }

  onTimeChange(): void {
    // Clamp values
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
    this.currentMonth.set(new Date(current.getFullYear(), current.getMonth() - 1, 1));
  }

  nextMonth(): void {
    const current = this.currentMonth();
    this.currentMonth.set(new Date(current.getFullYear(), current.getMonth() + 1, 1));
  }

  canGoPrevious(): boolean {
    if (!this.earliestTime) return true;
    const earliest = new Date(this.earliestTime);
    const current = this.currentMonth();
    return current > new Date(earliest.getFullYear(), earliest.getMonth(), 1);
  }

  canGoNext(): boolean {
    if (!this.latestTime) return true;
    const latest = new Date(this.latestTime);
    const current = this.currentMonth();
    return current < new Date(latest.getFullYear(), latest.getMonth(), 1);
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
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  formatDateTime(date: Date): string {
    return date.toLocaleString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    });
  }
}

interface CalendarDay {
  date: number;
  inMonth: boolean;
  inRange: boolean;
  isSelected: boolean;
  isToday: boolean;
}
