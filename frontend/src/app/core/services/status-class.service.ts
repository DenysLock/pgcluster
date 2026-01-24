import { Injectable } from '@angular/core';

/**
 * Common status types used across the application.
 */
export type CommonStatus = 'completed' | 'in_progress' | 'pending' | 'failed' | 'running' | 'stopped' | 'error' | 'creating' | 'deleting';

/**
 * Service for consistent status-based CSS classes across the application.
 * Centralizes all status styling to avoid duplication.
 */
@Injectable({
  providedIn: 'root'
})
export class StatusClassService {

  /**
   * Get dot/indicator classes for a status (small colored circle).
   * Used for status indicators next to items.
   */
  getStatusDotClasses(status: string): string {
    switch (status) {
      case 'completed':
      case 'running':
        return 'bg-emerald-500';
      case 'in_progress':
      case 'pending':
      case 'creating':
      case 'deleting':
        return 'bg-amber-500 animate-pulse';
      case 'failed':
      case 'error':
      case 'stopped':
        return 'bg-red-500';
      default:
        return 'bg-gray-400';
    }
  }

  /**
   * Get badge classes for a status (pill-shaped label).
   * Used for status badges in cards and lists.
   */
  getStatusBadgeClasses(status: string): string {
    switch (status) {
      case 'completed':
      case 'running':
        return 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-400';
      case 'in_progress':
      case 'pending':
      case 'creating':
      case 'deleting':
        return 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400';
      case 'failed':
      case 'error':
      case 'stopped':
        return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400';
      default:
        return 'bg-gray-100 text-gray-800 dark:bg-gray-900/30 dark:text-gray-400';
    }
  }

  /**
   * Get alert/banner classes for a status.
   * Used for full-width status alerts.
   */
  getStatusAlertClasses(status: string): string {
    switch (status) {
      case 'completed':
      case 'running':
        return 'bg-emerald-50 dark:bg-emerald-900/20 border-emerald-200 dark:border-emerald-800';
      case 'in_progress':
      case 'pending':
      case 'creating':
      case 'deleting':
        return 'bg-amber-50 dark:bg-amber-900/20 border-amber-200 dark:border-amber-800';
      case 'failed':
      case 'error':
      case 'stopped':
        return 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800';
      default:
        return 'bg-gray-50 dark:bg-gray-900/20 border-gray-200 dark:border-gray-800';
    }
  }

  /**
   * Check if a status indicates active processing.
   */
  isActiveStatus(status: string): boolean {
    return ['in_progress', 'pending', 'creating', 'deleting'].includes(status);
  }

  /**
   * Check if a status indicates a successful/healthy state.
   */
  isSuccessStatus(status: string): boolean {
    return ['completed', 'running'].includes(status);
  }

  /**
   * Check if a status indicates an error/failed state.
   */
  isErrorStatus(status: string): boolean {
    return ['failed', 'error', 'stopped'].includes(status);
  }
}
