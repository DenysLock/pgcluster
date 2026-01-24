import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotificationService, Notification } from '../../core/services/notification.service';
import { LucideAngularModule, X, CheckCircle, AlertCircle, AlertTriangle, Info } from 'lucide-angular';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  template: `
    <div class="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm">
      @for (notification of notificationService.notifications(); track notification.id) {
        <div
          class="flex items-start gap-3 p-4 rounded-lg shadow-lg border animate-slide-in"
          [class]="getNotificationClasses(notification.type)"
          role="alert"
        >
          <lucide-icon
            [img]="getIcon(notification.type)"
            class="w-5 h-5 flex-shrink-0 mt-0.5"
          ></lucide-icon>
          <p class="flex-1 text-sm">{{ notification.message }}</p>
          <button
            (click)="notificationService.dismiss(notification.id)"
            class="flex-shrink-0 p-1 rounded hover:bg-black/10 transition-colors"
          >
            <lucide-icon [img]="closeIcon" class="w-4 h-4"></lucide-icon>
          </button>
        </div>
      }
    </div>
  `,
  styles: [`
    @keyframes slide-in {
      from {
        transform: translateX(100%);
        opacity: 0;
      }
      to {
        transform: translateX(0);
        opacity: 1;
      }
    }
    .animate-slide-in {
      animation: slide-in 0.2s ease-out;
    }
  `]
})
export class ToastComponent {
  notificationService = inject(NotificationService);

  closeIcon = X;
  successIcon = CheckCircle;
  errorIcon = AlertCircle;
  warningIcon = AlertTriangle;
  infoIcon = Info;

  getIcon(type: Notification['type']) {
    switch (type) {
      case 'success': return this.successIcon;
      case 'error': return this.errorIcon;
      case 'warning': return this.warningIcon;
      case 'info': return this.infoIcon;
    }
  }

  getNotificationClasses(type: Notification['type']): string {
    const base = 'bg-white dark:bg-zinc-800';
    switch (type) {
      case 'success':
        return `${base} border-green-200 dark:border-green-800 text-green-800 dark:text-green-200`;
      case 'error':
        return `${base} border-red-200 dark:border-red-800 text-red-800 dark:text-red-200`;
      case 'warning':
        return `${base} border-yellow-200 dark:border-yellow-800 text-yellow-800 dark:text-yellow-200`;
      case 'info':
        return `${base} border-blue-200 dark:border-blue-800 text-blue-800 dark:text-blue-200`;
    }
  }
}
