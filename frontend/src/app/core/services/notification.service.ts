import { Injectable, signal } from '@angular/core';

export interface Notification {
  id: number;
  type: 'success' | 'error' | 'warning' | 'info';
  message: string;
  duration?: number;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private nextId = 0;
  notifications = signal<Notification[]>([]);

  show(type: Notification['type'], message: string, duration = 5000): void {
    const notification: Notification = {
      id: this.nextId++,
      type,
      message,
      duration
    };

    this.notifications.update(n => [...n, notification]);

    if (duration > 0) {
      setTimeout(() => this.dismiss(notification.id), duration);
    }
  }

  success(message: string, duration = 5000): void {
    this.show('success', message, duration);
  }

  error(message: string, duration = 8000): void {
    this.show('error', message, duration);
  }

  warning(message: string, duration = 6000): void {
    this.show('warning', message, duration);
  }

  info(message: string, duration = 5000): void {
    this.show('info', message, duration);
  }

  dismiss(id: number): void {
    this.notifications.update(n => n.filter(x => x.id !== id));
  }

  clear(): void {
    this.notifications.set([]);
  }
}
