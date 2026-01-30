import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-auth-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet],
  template: `
    <div class="min-h-screen flex items-center justify-center bg-bg-primary p-4">
      <div class="w-full max-w-md">
        <div class="text-center mb-8">
          <h1 class="text-lg font-bold text-primary tracking-wider">PGCLUSTER</h1>
          <p class="text-muted-foreground mt-2 text-sm">Managed PostgreSQL Platform</p>
        </div>
        <ng-content />
      </div>
    </div>
  `
})
export class AuthLayoutComponent {}
