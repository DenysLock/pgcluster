import { Injectable } from '@angular/core';

/**
 * Service for consistent date and time formatting across the application.
 * Centralizes all date formatting logic to avoid duplication.
 */
@Injectable({
  providedIn: 'root'
})
export class DateFormattingService {
  private readonly locale = 'en-US';

  /**
   * Format a date string to a readable date (e.g., "Jan 20, 2026")
   */
  formatDate(dateString: string | null | undefined): string {
    if (!dateString) return '';
    return new Date(dateString).toLocaleDateString(this.locale, {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  }

  /**
   * Format a date string to a full datetime (e.g., "Jan 20, 2026, 14:30")
   */
  formatDateTime(dateString: string | null | undefined): string {
    if (!dateString) return '';
    return new Date(dateString).toLocaleString(this.locale, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  /**
   * Format a Date object to a full datetime with seconds
   */
  formatDateTimeFull(date: Date | null | undefined): string {
    if (!date) return '';
    return date.toLocaleString(this.locale, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    });
  }

  /**
   * Format a date string to time only (e.g., "14:30:00")
   */
  formatTime(dateString: string | null | undefined): string {
    if (!dateString) return '';
    return new Date(dateString).toLocaleTimeString(this.locale, {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  }

  /**
   * Format a date to month and year (e.g., "January 2026")
   */
  formatMonthYear(date: Date | null | undefined): string {
    if (!date) return '';
    return date.toLocaleDateString(this.locale, {
      month: 'long',
      year: 'numeric'
    });
  }

  /**
   * Get relative time string (e.g., "2 hours ago", "in 3 days")
   */
  formatRelative(dateString: string | null | undefined): string {
    if (!dateString) return '';

    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffSeconds = Math.floor(diffMs / 1000);
    const diffMinutes = Math.floor(diffSeconds / 60);
    const diffHours = Math.floor(diffMinutes / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffSeconds < 60) return 'just now';
    if (diffMinutes < 60) return `${diffMinutes} minute${diffMinutes > 1 ? 's' : ''} ago`;
    if (diffHours < 24) return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`;
    if (diffDays < 7) return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`;

    return this.formatDate(dateString);
  }
}
