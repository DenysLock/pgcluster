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
      colors: {
        // Background colors
        bg: {
          primary: '#0a0a0a',
          secondary: '#111111',
          tertiary: '#1a1a1a',
        },
        // Status colors
        status: {
          running: '#00ff00',
          warning: '#ffaa00',
          error: '#ff3333',
          stopped: '#666666',
        },
        // Neon accent colors
        neon: {
          green: '#00ff00',
          cyan: '#00aaff',
          purple: '#aa00ff',
        },
        // Legacy color mappings for existing components
        border: '#222222',
        input: '#1a1a1a',
        ring: '#00ff00',
        background: '#0a0a0a',
        foreground: '#e0e0e0',
        primary: {
          DEFAULT: '#00ff00',
          foreground: '#0a0a0a',
        },
        secondary: {
          DEFAULT: '#1a1a1a',
          foreground: '#e0e0e0',
        },
        destructive: {
          DEFAULT: '#ff3333',
          foreground: '#ffffff',
        },
        muted: {
          DEFAULT: '#1a1a1a',
          foreground: '#999999',
        },
        accent: {
          DEFAULT: '#00aaff',
          foreground: '#0a0a0a',
        },
        popover: {
          DEFAULT: '#111111',
          foreground: '#e0e0e0',
        },
        card: {
          DEFAULT: '#111111',
          foreground: '#e0e0e0',
        },
      },
      borderRadius: {
        lg: '0',
        md: '0',
        sm: '0',
        DEFAULT: '0',
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
