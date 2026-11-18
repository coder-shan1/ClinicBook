const PAGE_TITLES: Record<string, string> = {
  '/': 'ClinicBook — book clinic appointments',
  '/doctors': 'Find a doctor · ClinicBook',
  '/login': 'Log in · ClinicBook',
  '/register': 'Create an account · ClinicBook',
  '/appointments': 'My appointments · ClinicBook',
  '/doctor/schedule': 'My schedule · ClinicBook',
  '/admin': 'Admin dashboard · ClinicBook',
  '/admin/users': 'User management · ClinicBook',
  '/settings/notifications': 'Notification preferences · ClinicBook',
}

/** Document title for a path. Unknown paths resolve to the 404 title. */
export function titleForPath(pathname: string): string {
  const normalized = pathname.length > 1 ? pathname.replace(/\/+$/, '') : pathname
  if (PAGE_TITLES[normalized]) return PAGE_TITLES[normalized]
  if (/^\/doctors\/[^/]+$/.test(normalized)) return 'Doctor profile · ClinicBook'
  return 'Page not found · ClinicBook'
}
