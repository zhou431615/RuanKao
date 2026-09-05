/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js}'],
  theme: {
    extend: {
      colors: {
        primary: { DEFAULT: '#4F46E5', light: '#6366F1', lighter: '#818CF8', dark: '#4338CA' }
      },
      fontFamily: {
        sans: ['Source Han Sans SC', 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', 'sans-serif']
      }
    }
  },
  plugins: []
}