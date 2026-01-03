import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div [class]="cardClasses">
      @if (title || description) {
        <div class="p-6 pb-0">
          @if (title) {
            <h3 class="text-lg font-semibold leading-none tracking-tight">{{ title }}</h3>
          }
          @if (description) {
            <p class="text-sm text-muted-foreground mt-1.5">{{ description }}</p>
          }
        </div>
      }
      <div class="p-6">
        <ng-content />
      </div>
    </div>
  `
})
export class CardComponent {
  @Input() title?: string;
  @Input() description?: string;
  @Input() variant: 'default' | 'outline' | 'danger' = 'default';

  get cardClasses(): string {
    const base = 'rounded-lg border bg-card text-card-foreground shadow-sm';

    const variants: Record<string, string> = {
      default: 'border-border',
      outline: 'border-border bg-transparent shadow-none',
      danger: 'border-destructive/50 bg-destructive/5',
    };

    return `${base} ${variants[this.variant]}`;
  }
}
