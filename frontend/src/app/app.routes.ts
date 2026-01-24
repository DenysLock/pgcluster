import { Routes } from '@angular/router';
import { authGuard, adminGuard, guestGuard } from './core/guards';

export const routes: Routes = [
  // Public routes (guest only)
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register/register.component').then(m => m.RegisterComponent)
  },

  // Protected routes (authenticated users)
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shared/layouts/app-shell/app-shell.component').then(m => m.AppShellComponent),
    children: [
      { path: '', redirectTo: 'clusters/new', pathMatch: 'full' },
      {
        path: 'clusters/new',
        loadComponent: () => import('./features/clusters/cluster-create/cluster-create.component').then(m => m.ClusterCreateComponent)
      },
      {
        path: 'clusters/:id',
        loadComponent: () => import('./features/clusters/cluster-detail/cluster-detail.component').then(m => m.ClusterDetailComponent)
      },
      {
        path: 'settings',
        loadComponent: () => import('./features/settings/settings.component').then(m => m.SettingsComponent)
      }
    ]
  },

  // Admin routes
  {
    path: 'admin',
    canActivate: [authGuard, adminGuard],
    loadComponent: () => import('./shared/layouts/app-shell/app-shell.component').then(m => m.AppShellComponent),
    children: [
      {
        path: '',
        loadComponent: () => import('./features/admin/admin-dashboard/admin-dashboard.component').then(m => m.AdminDashboardComponent)
      },
      {
        path: 'clusters',
        loadComponent: () => import('./features/admin/clusters/cluster-list.component').then(m => m.AdminClusterListComponent)
      },
      {
        path: 'clusters/:id',
        loadComponent: () => import('./features/admin/clusters/cluster-detail.component').then(m => m.AdminClusterDetailComponent)
      },
      {
        path: 'users',
        loadComponent: () => import('./features/admin/users/user-list.component').then(m => m.UserListComponent)
      }
    ]
  },

  // Catch-all redirect
  { path: '**', redirectTo: 'clusters/new' }
];
