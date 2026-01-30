/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: 'class',
  content: [
    './src/**/*.{html,ts}',
  ],
  theme: {
    fontFamily: {
      mono: ['IBM Plex Mono', 'monospace'],
      sans: ['IBM Plex Mono', 'monospace'],
    },
    container: {
      center: true,
      padding: '2rem',
      screens: {
        '2xl': '1400px',
      },
    },
    extend: {
    fontSize: {
            'xs': ['15px', { lineHeight: '24px' }],   // small - metadata, badges
            'sm': ['18px', { lineHeight: '28px' }],   // medium - body text
            'lg': ['22px', { lineHeight: '34px' }],   // large - headings
          },
      colors: {
        // Background colors - Light theme
        bg: {
          primary: '#F8FAFC',    // slate-50 - main background
          secondary: '#FFFFFF',  // white - cards, panels
          tertiary: '#F1F5F9',   // slate-100 - hover states
        },
        // Status colors - adjusted for light backgrounds
        status: {
          running: '#22C55E',    // green-500
          warning: '#F59E0B',    // amber-500
          error: '#EF4444',      // red-500
          stopped: '#94A3B8',    // slate-400
        },
        // Accent colors - blue theme
        neon: {
          green: '#2563EB',      // blue-600 (primary actions)
          cyan: '#3B82F6',       // blue-500 (secondary accent)
          purple: '#7C3AED',     // violet-600
        },
        // Legacy color mappings
        border: '#E2E8F0',       // slate-200
        input: '#F1F5F9',        // slate-100
        ring: '#3B82F6',         // blue-500
        background: '#F8FAFC',   // slate-50
        foreground: '#1E293B',   // slate-800
        primary: {
          DEFAULT: '#2563EB',    // blue-600
          foreground: '#FFFFFF',
        },
        secondary: {
          DEFAULT: '#F1F5F9',    // slate-100
          foreground: '#1E293B', // slate-800
        },
        destructive: {
          DEFAULT: '#EF4444',    // red-500
          foreground: '#FFFFFF',
        },
        muted: {
          DEFAULT: '#F1F5F9',    // slate-100
          foreground: '#64748B', // slate-500
        },
        accent: {
          DEFAULT: '#3B82F6',    // blue-500
          foreground: '#FFFFFF',
        },
        popover: {
          DEFAULT: '#FFFFFF',
          foreground: '#1E293B',
        },
        card: {
          DEFAULT: '#FFFFFF',
          foreground: '#1E293B',
        },
      },
      borderRadius: {
        lg: '0.5rem',
        md: '0.375rem',
        sm: '0.25rem',
        DEFAULT: '0.25rem',
        none: '0',
      },
      keyframes: {
        'accordion-down': {
          from: { height: '0' },
          to: { height: 'var(--radix-accordion-content-height)' },
        },
        'accordion-up': {
          from: { height: 'var(--radix-accordion-content-height)' },
          to: { height: '0' },
        },
        'spin-slow': {
          from: { transform: 'rotate(0deg)' },
          to: { transform: 'rotate(360deg)' },
        },
        'pulse-glow': {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.5' },
        },
      },
      animation: {
        'accordion-down': 'accordion-down 0.2s ease-out',
        'accordion-up': 'accordion-up 0.2s ease-out',
        'spin-slow': 'spin-slow 1s linear infinite',
        'pulse-glow': 'pulse-glow 2s ease-in-out infinite',
      },
    },
  },
  plugins: [],
}
