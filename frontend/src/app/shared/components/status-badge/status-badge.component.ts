import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

type StatusType = 'pending' | 'creating' | 'provisioning' | 'forming' | 'running' | 'ready' | 'degraded' | 'error' | 'deleting' | 'deleted' | 'draining' | 'leader' | 'replica' | 'unknown' | string;

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span [class]="badgeClasses">
      {{ displayText }}
    </span>
  `
})
export class StatusBadgeComponent {
  @Input() status: StatusType = 'unknown';
  @Input() size: 'sm' | 'md' = 'md';

  get displayText(): string {
    return this.status.toUpperCase();
  }

  get badgeClasses(): string {
    const base = 'inline-flex items-center font-semibold uppercase tracking-wide border';
    const sizeClasses = this.size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-3 py-1 text-sm';

    const colorClasses: Record<string, string> = {
      running: 'border-status-running text-status-running',
      ready: 'border-status-running text-status-running',
      leader: 'border-neon-green text-neon-green',
      creating: 'border-status-warning text-status-warning',
      provisioning: 'border-status-warning text-status-warning',
      forming: 'border-status-warning text-status-warning',
      pending: 'border-status-warning text-status-warning',
      degraded: 'border-status-warning text-status-warning',
      draining: 'border-status-warning text-status-warning',
      replica: 'border-neon-cyan text-neon-cyan',
      error: 'border-status-error text-status-error',
      deleting: 'border-status-error text-status-error',
      deleted: 'border-status-stopped text-status-stopped',
      unknown: 'border-status-stopped text-status-stopped',
    };

    return `${base} ${sizeClasses} ${colorClasses[this.status] || colorClasses['unknown']}`;
  }
}
