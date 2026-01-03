import { Component, Input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CopyButtonComponent } from '../copy-button/copy-button.component';

@Component({
  selector: 'app-connection-string',
  standalone: true,
  imports: [CommonModule, CopyButtonComponent],
  template: `
    <div class="space-y-2">
      <div class="flex items-center justify-between">
        <label class="text-sm font-medium text-foreground">{{ label }}</label>
        <button
          (click)="toggleMask()"
          class="text-xs text-muted-foreground hover:text-foreground transition-colors"
        >
          {{ masked() ? 'Show password' : 'Hide password' }}
        </button>
      </div>
      <div class="flex items-center gap-2">
        <div class="flex-1 font-mono text-sm bg-muted px-3 py-2 rounded-md overflow-x-auto whitespace-nowrap">
          @if (masked()) {
            <span>{{ prefix }}</span><span class="password-mask"></span><span>{{ suffix }}</span>
          } @else {
            {{ value }}
          }
        </div>
        <app-copy-button [value]="value" />
      </div>
      @if (description) {
        <p class="text-xs text-muted-foreground">{{ description }}</p>
      }
    </div>
  `,
  styles: [`
    .password-mask {
      display: inline-block;
      width: 4em;
      height: 0.5em;
      background: currentColor;
      border-radius: 2px;
      vertical-align: middle;
      margin: 0 1px;
    }
  `]
})
export class ConnectionStringComponent {
  @Input() label: string = 'Connection String';
  @Input() value: string = '';
  @Input() description?: string;

  masked = signal(true);

  // Extract part before password (e.g., "postgresql://postgres:")
  get prefix(): string {
    const match = this.value.match(/^(.*:\/\/[^:]+:)/);
    return match ? match[1] : '';
  }

  // Extract part after password (e.g., "@host:port/database")
  get suffix(): string {
    const match = this.value.match(/@(.*)$/);
    return match ? '@' + match[1] : '';
  }

  toggleMask(): void {
    this.masked.update(v => !v);
  }
}
