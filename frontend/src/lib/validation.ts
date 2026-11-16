/**
 * Client-side mirrors of the backend constraints (see backend
 * `ValidEmailValidator` and the annotations on `RegisterRequest`).
 *
 * The backend is always authoritative; validating here gives immediate feedback,
 * prevents obviously invalid requests from being sent, and keeps the messages in
 * sync with the fields shown by the forms.
 */

const MAX_EMAIL_LENGTH = 254
const MAX_DOMAIN_LENGTH = 253
const MAX_LOCAL_PART_LENGTH = 64
const MAX_FULL_NAME_LENGTH = 120
const MAX_PHONE_LENGTH = 30
const MAX_PASSWORD_LENGTH = 72
const MAX_PASSWORD_BYTES = 72

const LOCAL_PART = /^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*$/
const DOMAIN_LABEL = /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$/
const ALPHABETIC_TLD = /^[A-Za-z]{2,63}$/
const FULL_NAME = /^(?=.*\p{L})[\p{L}\p{M} .'’\-]{1,120}$/u
const PHONE = /^(?=(?:[^0-9]*[0-9]){7,15}[^0-9]*$)\+?[0-9][0-9 ()-]{5,28}$/

export const EMAIL_FORMAT_MESSAGE = 'Enter a valid email address, e.g. name@example.com'
export const FULL_NAME_FORMAT_MESSAGE =
  'Use letters, spaces, apostrophes, hyphens and periods only'
export const PHONE_FORMAT_MESSAGE = 'Enter a valid phone number (7–15 digits)'

/** Trim and lowercase an email — the same normalisation the backend persists. */
export function normalizeEmail(value: string): string {
  return value.trim().toLowerCase()
}

export function isValidEmail(value: string): boolean {
  const email = value
  if (email.length === 0 || email.length > MAX_EMAIL_LENGTH) return false

  const at = email.indexOf('@')
  if (at <= 0 || at !== email.lastIndexOf('@') || at === email.length - 1) return false

  const localPart = email.slice(0, at)
  const domain = email.slice(at + 1)

  if (localPart.length > MAX_LOCAL_PART_LENGTH || !LOCAL_PART.test(localPart)) return false
  if (domain.length > MAX_DOMAIN_LENGTH) return false

  const labels = domain.split('.')
  if (labels.length < 2) return false

  for (let i = 0; i < labels.length; i += 1) {
    const label = labels[i]
    if (!DOMAIN_LABEL.test(label)) return false
    if (i === labels.length - 1) {
      const isAlphabeticTld = ALPHABETIC_TLD.test(label)
      const isPunycodeTld = label.length > 4 && label.toLowerCase().startsWith('xn--')
      if (!isAlphabeticTld && !isPunycodeTld) return false
    }
  }

  return true
}

export function validateEmail(value: string): string | null {
  const email = value.trim()
  if (email.length === 0) return 'Email is required'
  return isValidEmail(email) ? null : EMAIL_FORMAT_MESSAGE
}

export function validatePassword(value: string, minLength = 0): string | null {
  if (value.length === 0) return 'Password is required'
  if (value.length < minLength) return `Password must be at least ${minLength} characters`
  if (value.length > MAX_PASSWORD_LENGTH) {
    return `Password must be at most ${MAX_PASSWORD_LENGTH} characters`
  }
  if (utf8Length(value) > MAX_PASSWORD_BYTES) {
    return 'Password must be at most 72 bytes when UTF-8 encoded'
  }
  return null
}

export function validateFullName(value: string): string | null {
  const name = value.trim()
  if (name.length === 0) return 'Full name is required'
  if (name.length > MAX_FULL_NAME_LENGTH) {
    return `Full name must be at most ${MAX_FULL_NAME_LENGTH} characters`
  }
  return FULL_NAME.test(name) ? null : FULL_NAME_FORMAT_MESSAGE
}

/** Phone is optional; an empty value is valid and is omitted from the request. */
export function validatePhone(value: string): string | null {
  const phone = value.trim()
  if (phone.length === 0) return null
  if (phone.length > MAX_PHONE_LENGTH) {
    return `Phone must be at most ${MAX_PHONE_LENGTH} characters`
  }
  return PHONE.test(phone) ? null : PHONE_FORMAT_MESSAGE
}

function utf8Length(value: string): number {
  return new TextEncoder().encode(value).length
}
