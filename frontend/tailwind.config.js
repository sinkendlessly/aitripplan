/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        sand: {
          50: '#fdf8f0',
          100: '#f9edda',
          200: '#f2d9b5',
          300: '#e9bf87',
          400: '#dfa05a',
          500: '#d7893a',
          600: '#c9732f',
          700: '#a75b28',
          800: '#864928',
          900: '#6d3d23',
          950: '#3a1e11',
        },
        sage: {
          50: '#f5f8f4',
          100: '#e7ede5',
          200: '#cfdccb',
          300: '#adc2a6',
          400: '#85a37b',
          500: '#65855b',
          600: '#4f6a47',
          700: '#3f5439',
          800: '#354530',
          900: '#2e3a2a',
          950: '#151f13',
        },
        coral: {
          50: '#fef3f0',
          100: '#fde3db',
          200: '#fbcbbb',
          300: '#f8a991',
          400: '#f37f5f',
          500: '#ee5e36',
          600: '#df462b',
          700: '#bb3522',
          800: '#942d21',
          900: '#78291f',
          950: '#40120d',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
        display: ['Quicksand', 'Inter', 'system-ui', 'sans-serif'],
      },
      animation: {
        'pulse-soft': 'pulse-soft 2s ease-in-out infinite',
        'float': 'float 6s ease-in-out infinite',
        'fade-in': 'fade-in 0.5s ease-out',
        'slide-up': 'slide-up 0.5s ease-out',
        'slide-in-right': 'slide-in-right 0.3s ease-out',
      },
      keyframes: {
        'pulse-soft': {
          '0%, 100%': { opacity: 1 },
          '50%': { opacity: 0.6 },
        },
        'float': {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-10px)' },
        },
        'fade-in': {
          '0%': { opacity: 0 },
          '100%': { opacity: 1 },
        },
        'slide-up': {
          '0%': { opacity: 0, transform: 'translateY(10px)' },
          '100%': { opacity: 1, transform: 'translateY(0)' },
        },
        'slide-in-right': {
          '0%': { opacity: 0, transform: 'translateX(20px)' },
          '100%': { opacity: 1, transform: 'translateX(0)' },
        },
      },
    },
  },
  plugins: [],
}
