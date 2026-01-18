import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // First check authentication (includes expiration check)
  if (!authService.isAuthenticated()) {
    // Clear stale auth data and redirect to login
    authService.logout();
    return false;
  }

  if (authService.isAdmin()) {
    return true;
  }

  // Authenticated but not admin - redirect to dashboard
  router.navigate(['/dashboard']);
  return false;
};
