import { Component, Input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-copy-button',
  standalone: true,
  imports: [CommonModule],
  template: `
    <button
      (click)="copy()"
      [class]="buttonClasses"
      [title]="copied() ? 'Copied!' : 'Copy to clipboard'"
    >
      @if (copied()) {
        <svg class="w-4 h-4 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
        </svg>
      } @else {
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
        </svg>
      }
      @if (showLabel) {
        <span class="ml-1">{{ copied() ? 'Copied!' : 'Copy' }}</span>
      }
    </button>
  `
})
export class CopyButtonComponent {
  @Input() value: string = '';
  @Input() showLabel: boolean = false;
  @Input() variant: 'default' | 'outline' | 'ghost' = 'ghost';

  copied = signal(false);

  get buttonClasses(): string {
    const base = 'inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50';

    const variants: Record<string, string> = {
      default: 'bg-primary text-primary-foreground hover:bg-primary/90 h-9 px-3',
      outline: 'border border-input bg-background hover:bg-accent hover:text-accent-foreground h-9 px-3',
      ghost: 'hover:bg-accent hover:text-accent-foreground h-8 w-8',
    };

    return `${base} ${variants[this.variant]}`;
  }

  async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.value);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  }
}
