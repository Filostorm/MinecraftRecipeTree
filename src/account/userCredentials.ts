const UNSAFE_TEXT_PATTERN = /[\u0000-\u001f\u007f-\u009f\u061c\u200b-\u200f\u202a-\u202e\u2060-\u2069\ufeff]/u;

export function validEmail(email: string): string {
  const normalized = email.trim().toLowerCase();
  if (
    normalized.length < 3 ||
    normalized.length > 254 ||
    !/^[^\s@]+@[^\s@]+\.[^\s@]+$/u.test(normalized) ||
    UNSAFE_TEXT_PATTERN.test(normalized)
  ) {
    throw new Error('Enter a valid email address.');
  }
  return normalized;
}

export function validPassword(password: string): string {
  if (password.length < 8 || password.length > 128 || UNSAFE_TEXT_PATTERN.test(password)) {
    throw new Error('Password must be 8–128 characters.');
  }
  return password;
}

export function validDisplayName(displayName: string): string {
  const normalized = displayName.trim().replace(/\s+/gu, ' ');
  if (
    normalized.length < 2 ||
    normalized.length > 32 ||
    UNSAFE_TEXT_PATTERN.test(displayName)
  ) {
    throw new Error('Username must be 2–32 characters without control characters.');
  }
  return normalized;
}
