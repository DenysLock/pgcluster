import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        // Token expired or invalid - force re-login
        authService.logout();
        router.navigate(['/login']);
      } else if (error.status === 403) {
        // Permission denied - user is authenticated but not authorized
        // Don't logout, just log for debugging
        console.warn('Access forbidden:', error.url);
      }

      return throwError(() => error);
    })
  );
};
