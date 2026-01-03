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
          class="fixed inset-0 bg-black/50"
          (click)="onCancel()"
        ></div>

        <!-- Dialog -->
        <div class="relative bg-background rounded-lg shadow-lg p-6 w-full max-w-md mx-4 animate-in fade-in zoom-in-95">
          <h2 class="text-lg font-semibold mb-2">{{ title }}</h2>
          <p class="text-muted-foreground mb-6">{{ message }}</p>

          <div class="flex justify-end gap-3">
            <button
              (click)="onCancel()"
              class="inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 border border-input bg-background hover:bg-accent hover:text-accent-foreground h-10 px-4 py-2"
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
    const base = 'inline-flex items-center justify-center rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 h-10 px-4 py-2';

    if (this.variant === 'destructive') {
      return `${base} bg-destructive text-destructive-foreground hover:bg-destructive/90`;
    }
    return `${base} bg-primary text-primary-foreground hover:bg-primary/90`;
  }

  onConfirm(): void {
    this.confirm.emit();
  }

  onCancel(): void {
    this.cancel.emit();
  }
}
