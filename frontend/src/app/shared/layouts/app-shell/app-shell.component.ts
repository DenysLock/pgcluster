import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from '../../components/header/header.component';
import { SidebarComponent } from '../../components/sidebar/sidebar.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, HeaderComponent, SidebarComponent],
  template: `
    <div class="h-screen flex flex-col bg-bg-primary overflow-hidden">
      <!-- Header -->
      <app-header />

      <!-- Body: Sidebar + Main Content -->
      <div class="flex-1 flex overflow-hidden">
        <!-- Sidebar -->
        <app-sidebar />

        <!-- Main Content -->
        <main class="flex-1 overflow-y-auto bg-bg-primary p-6">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class AppShellComponent {}
