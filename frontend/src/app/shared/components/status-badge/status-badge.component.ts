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
  @Input() size: 'sm' | 'md' = 'sm'; // Default to sm for consistency

  get displayText(): string {
    return this.status.toUpperCase();
  }

  get badgeClasses(): string {
    // Use unified badge class from styles.scss
    const base = 'badge';

    const colorClasses: Record<string, string> = {
      running: 'badge-success',
      ready: 'badge-success',
      leader: 'badge-success',
      creating: 'badge-warning',
      provisioning: 'badge-warning',
      forming: 'badge-warning',
      pending: 'badge-warning',
      degraded: 'badge-warning',
      draining: 'badge-warning',
      replica: 'badge-info',
      error: 'badge-error',
      deleting: 'badge-error',
      deleted: 'badge-muted',
      unknown: 'badge-muted',
    };

    return `${base} ${colorClasses[this.status] || colorClasses['unknown']}`;
  }
}
