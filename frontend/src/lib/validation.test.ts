import { describe, expect, it } from 'vitest'
import {
  EMAIL_FORMAT_MESSAGE,
  FULL_NAME_FORMAT_MESSAGE,
  PHONE_FORMAT_MESSAGE,
  isValidEmail,
  normalizeEmail,
  validateEmail,
  validateFullName,
  validatePassword,
  validatePhone,
} from './validation'

describe('email validation', () => {
  const validEmails = [
    'jane@example.com',
    'jane.doe+tag@sub.example.co.uk',
    'JANE@EXAMPLE.COM',
    'a@b.co',
    'user_name@example.io',
    "o'brien@example.com",
    'user@example.xn--p1ai',
    'user@example.travel',
    'first.last@my-domain.example',
  ]

  const invalidEmails = [
    'user@gmail',
    'user@localhost',
    'user@com',
    'user@example.c',
    'user@example.123',
    'user@example.c0m',
    'user@.com',
    'user@com.',
    'user@example..com',
    'user@-example.com',
    'user@example-.com',
    'user@example_com',
    'user@exa mple.com',
    'user@@example.com',
    '@example.com',
    'user@',
    'plainaddress',
    '.user@example.com',
    'user.@example.com',
    'user..name@example.com',
    'user name@example.com',
    'user@[127.0.0.1]',
    '用户@example.com',
  ]

  it.each(validEmails)('accepts %s', (email) => {
    expect(isValidEmail(email)).toBe(true)
    expect(validateEmail(email)).toBeNull()
  })

  it.each(invalidEmails)('rejects %s', (email) => {
    expect(isValidEmail(email)).toBe(false)
    expect(validateEmail(email)).toBe(EMAIL_FORMAT_MESSAGE)
  })

  it('requires an email', () => {
    expect(validateEmail('')).toBe('Email is required')
    expect(validateEmail('   ')).toBe('Email is required')
  })

  it('normalises case and surrounding whitespace', () => {
    expect(normalizeEmail('  Jane.Doe@Example.COM  ')).toBe('jane.doe@example.com')
  })
})

describe('password validation', () => {
  it('requires a password', () => {
    expect(validatePassword('')).toBe('Password is required')
  })

  it('applies the registration minimum only when requested', () => {
    expect(validatePassword('short', 8)).toBe('Password must be at least 8 characters')
    expect(validatePassword('short')).toBeNull()
    expect(validatePassword('longenough', 8)).toBeNull()
  })

  it('rejects passwords over the character and UTF-8 byte limits', () => {
    expect(validatePassword('a'.repeat(73), 8)).toBe('Password must be at most 72 characters')
    expect(validatePassword('é'.repeat(40), 8)).toBe(
      'Password must be at most 72 bytes when UTF-8 encoded',
    )
  })
})

describe('full name validation', () => {
  it.each(['Jane Doe', "O'Brien", 'Jean-Luc Picard', 'María José', '李小龙', 'Jane Doe Jr.', 'A'])(
    'accepts %s',
    (name) => {
      expect(validateFullName(name)).toBeNull()
    },
  )

  it.each(["<script>alert('xss')</script>", 'Jane\nDoe', 'Jane 😀 Doe', '...', '12345', 'Jane_Doe', 'Jane@Doe'])(
    'rejects %s',
    (name) => {
      expect(validateFullName(name)).toBe(FULL_NAME_FORMAT_MESSAGE)
    },
  )

  it('requires a full name', () => {
    expect(validateFullName('   ')).toBe('Full name is required')
  })
})

describe('phone validation', () => {
  it.each(['+1 234 567 8900', '1234567', '123-456-7890', '123456789012345', '+44 (0)20 7946 0958'])(
    'accepts %s',
    (phone) => {
      expect(validatePhone(phone)).toBeNull()
    },
  )

  it.each(['-------', '123456', '1234567890123456', '++123456789', '+ 123456789', 'call-me-now', '123 456'])(
    'rejects %s',
    (phone) => {
      expect(validatePhone(phone)).toBe(PHONE_FORMAT_MESSAGE)
    },
  )

  it('allows an empty optional phone', () => {
    expect(validatePhone('')).toBeNull()
    expect(validatePhone('   ')).toBeNull()
  })
})
