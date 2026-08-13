export function isDarkTheme() {
  const stored = localStorage.getItem('darkMode')
  return stored === null ? true : stored === 'true'
}

export function applyTheme(dark) {
  document.documentElement.classList.toggle('dark', Boolean(dark))
  localStorage.setItem('darkMode', String(Boolean(dark)))
}
