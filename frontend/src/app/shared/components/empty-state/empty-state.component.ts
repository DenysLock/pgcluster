import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex flex-col items-center justify-center py-12 text-center">
      <div class="w-12 h-12 rounded-full bg-muted flex items-center justify-center mb-4">
        <ng-content select="[icon]" />
      </div>
      <h3 class="text-lg font-semibold mb-1">{{ title }}</h3>
      @if (description) {
        <p class="text-sm text-muted-foreground mb-4 max-w-sm">{{ description }}</p>
      }
      <ng-content select="[action]" />
    </div>
  `
})
export class EmptyStateComponent {
  @Input() title: string = 'No items';
  @Input() description?: string;
}
