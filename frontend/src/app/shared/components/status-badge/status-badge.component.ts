import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

type StatusType = 'pending' | 'creating' | 'provisioning' | 'forming' | 'running' | 'ready' | 'degraded' | 'error' | 'deleting' | 'draining' | 'leader' | 'replica' | 'unknown' | string;

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span [class]="badgeClasses">
      <span [class]="dotClasses"></span>
      {{ displayText }}
    </span>
  `
})
export class StatusBadgeComponent {
  @Input() status: StatusType = 'unknown';
  @Input() size: 'sm' | 'md' = 'md';

  get displayText(): string {
    return this.status.charAt(0).toUpperCase() + this.status.slice(1);
  }

  get badgeClasses(): string {
    const base = 'inline-flex items-center gap-1.5 font-medium rounded-full';
    const sizeClasses = this.size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-2.5 py-1 text-sm';

    const colorClasses: Record<string, string> = {
      running: 'bg-emerald-50 text-emerald-700 border border-emerald-200',
      ready: 'bg-emerald-50 text-emerald-700 border border-emerald-200',
      leader: 'bg-emerald-50 text-emerald-700 border border-emerald-200',
      creating: 'bg-amber-50 text-amber-700 border border-amber-200',
      provisioning: 'bg-amber-50 text-amber-700 border border-amber-200',
      forming: 'bg-amber-50 text-amber-700 border border-amber-200',
      pending: 'bg-blue-50 text-blue-700 border border-blue-200',
      degraded: 'bg-amber-50 text-amber-700 border border-amber-200',
      draining: 'bg-amber-50 text-amber-700 border border-amber-200',
      replica: 'bg-blue-50 text-blue-700 border border-blue-200',
      error: 'bg-red-50 text-red-700 border border-red-200',
      deleting: 'bg-red-50 text-red-700 border border-red-200',
      unknown: 'bg-gray-50 text-gray-700 border border-gray-200',
    };

    return `${base} ${sizeClasses} ${colorClasses[this.status] || colorClasses['unknown']}`;
  }

  get dotClasses(): string {
    const base = 'rounded-full';
    const sizeClasses = this.size === 'sm' ? 'w-1.5 h-1.5' : 'w-2 h-2';

    const colorClasses: Record<string, string> = {
      running: 'bg-emerald-500',
      ready: 'bg-emerald-500',
      leader: 'bg-emerald-500',
      creating: 'bg-amber-500 animate-pulse',
      provisioning: 'bg-amber-500 animate-pulse',
      forming: 'bg-amber-500 animate-pulse',
      pending: 'bg-blue-500 animate-pulse',
      degraded: 'bg-amber-500',
      draining: 'bg-amber-500 animate-pulse',
      replica: 'bg-blue-500',
      error: 'bg-red-500',
      deleting: 'bg-red-500 animate-pulse',
      unknown: 'bg-gray-500',
    };

    return `${base} ${sizeClasses} ${colorClasses[this.status] || colorClasses['unknown']}`;
  }
}
