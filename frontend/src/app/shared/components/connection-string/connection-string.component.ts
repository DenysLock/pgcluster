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
        <label class="text-xs font-semibold uppercase tracking-wider text-muted-foreground">{{ label }}</label>
        <button
          (click)="toggleMask()"
          class="text-xs text-muted-foreground hover:text-foreground transition-colors"
        >
          {{ masked() ? 'Show password' : 'Hide password' }}
        </button>
      </div>
      <div class="flex items-center gap-2">
        <div class="flex-1 font-mono text-sm bg-bg-tertiary border border-border rounded px-3 py-2 overflow-x-auto whitespace-nowrap text-foreground">
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
