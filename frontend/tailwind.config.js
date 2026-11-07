/** @type {import('tailwindcss').Config} */
// The slate ramp resolves to CSS variables so the whole chrome can flip
// between the default WHITE (light) theme and the BLACK (dark) theme with a
// single class on <html> (see src/theme.tsx). Values live in index.css.
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        slate: {
          50: 'rgb(var(--st-50) / <alpha-value>)',
          100: 'rgb(var(--st-100) / <alpha-value>)',
          200: 'rgb(var(--st-200) / <alpha-value>)',
          300: 'rgb(var(--st-300) / <alpha-value>)',
          400: 'rgb(var(--st-400) / <alpha-value>)',
          500: 'rgb(var(--st-500) / <alpha-value>)',
          600: 'rgb(var(--st-600) / <alpha-value>)',
          700: 'rgb(var(--st-700) / <alpha-value>)',
          800: 'rgb(var(--st-800) / <alpha-value>)',
          900: 'rgb(var(--st-900) / <alpha-value>)',
          950: 'rgb(var(--st-950) / <alpha-value>)',
        },
        surface: {
          DEFAULT: 'var(--surface)',
          raised: 'var(--surface-raised)',
          border: 'var(--surface-border)',
        },
      },
      boxShadow: {
        card: 'var(--shadow-card)',
      },
      keyframes: {
        'pulse-dot': {
          '0%, 100%': { opacity: '1', transform: 'scale(1)' },
          '50%': { opacity: '0.45', transform: 'scale(0.82)' },
        },
        ticker: {
          '0%': { transform: 'translateX(0%)' },
          '100%': { transform: 'translateX(-50%)' },
        },
        'fade-in': {
          '0%': { opacity: '0', transform: 'translateY(4px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
      },
      animation: {
        'pulse-dot': 'pulse-dot 1.6s ease-in-out infinite',
        ticker: 'ticker 40s linear infinite',
        'fade-in': 'fade-in 0.25s ease-out both',
      },
    },
  },
  plugins: [],
};
