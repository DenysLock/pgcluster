import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (open) {
      <div class="fixed inset-0 z-50 flex items-center justify-center">
        <!-- Backdrop -->
        <div
          class="fixed inset-0 bg-slate-900/50"
          (click)="onCancel()"
        ></div>

        <!-- Dialog -->
        <div class="relative bg-white border border-border rounded-lg shadow-lg p-6 w-full max-w-md mx-4">
          <h2 class="text-lg font-semibold text-foreground mb-2">{{ title }}</h2>
          <p class="text-sm text-muted-foreground mb-6">{{ message }}</p>

          <div class="flex justify-end gap-3">
            <button
              (click)="onCancel()"
              class="btn-secondary"
            >
              Cancel
            </button>
            <button
              (click)="onConfirm()"
              [class]="confirmButtonClasses"
            >
              {{ confirmText }}
            </button>
          </div>
        </div>
      </div>
    }
  `
})
export class ConfirmDialogComponent {
  @Input() open: boolean = false;
  @Input() title: string = 'Confirm';
  @Input() message: string = 'Are you sure?';
  @Input() confirmText: string = 'Confirm';
  @Input() variant: 'default' | 'destructive' = 'default';

  @Output() confirm = new EventEmitter<void>();
  @Output() cancel = new EventEmitter<void>();

  get confirmButtonClasses(): string {
    if (this.variant === 'destructive') {
      return 'btn-danger';
    }
    return 'btn-primary';
  }

  onConfirm(): void {
    this.confirm.emit();
  }

  onCancel(): void {
    this.cancel.emit();
  }
}
