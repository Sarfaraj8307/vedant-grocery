import { z } from "zod";

/**
 * Client-side validation schemas.
 *
 * Every rule here **mirrors a real constraint in the live database**
 * (docs/BACKEND_CONTRACT.md §2). That is a deliberate invariant: the client
 * should reject exactly what Postgres would reject, so a customer never
 * experiences "the form said fine, then the server said no".
 *
 * These schemas are a UX affordance, not the security boundary — the database
 * checks remain authoritative.
 */

const trimmed = (max: number) =>
  z
    .string()
    .trim()
    .min(1, "This is required")
    .max(max, `Please keep this under ${max} characters`);

/* --- profiles ------------------------------------------------------------- */

export const profileUpdateSchema = z.object({
  firstName: z.string().trim().max(60, "Please keep this under 60 characters"),
  lastName: z.string().trim().max(60, "Please keep this under 60 characters"),
  phone: z
    .string()
    .trim()
    .refine(
      (value) => value === "" || /^[0-9+\s-]{10,15}$/.test(value),
      "Enter a valid phone number (10–15 digits)",
    ),
});

export type ProfileUpdateInput = z.infer<typeof profileUpdateSchema>;

/* --- auth ----------------------------------------------------------------- */

export const signInSchema = z.object({
  email: z.string().trim().toLowerCase().email("Enter a valid email address"),
  password: z.string().min(1, "Enter your password"),
});

export type SignInInput = z.infer<typeof signInSchema>;

/**
 * Username rules copied from `profiles_username_check` and
 * `check_username_available()` — `^[a-z0-9_]{3,24}$`.
 *
 * Exported as a plain regex as well as a zod schema because non-form callers
 * need the *rule* without a zod parse: `auth-service` has to decide whether a
 * Google handle (`john.doe`) is safe to use as a username. A second hand-copied
 * regex is how the address-label mismatch happened (docs/KNOWN_LIMITATIONS.md),
 * so this stays the single source.
 */
export const USERNAME_PATTERN = /^[a-z0-9_]{3,24}$/;

export const usernameSchema = z
  .string()
  .trim()
  .toLowerCase()
  .regex(
    USERNAME_PATTERN,
    "Use 3–24 characters: lowercase letters, numbers and underscores",
  );

export const signUpSchema = z.object({
  firstName: trimmed(60),
  // `.optional()` rather than `.default("")`: a default makes zod's input type
  // differ from its output type, which makes react-hook-form's resolver generic
  // unsound. Callers coalesce with `?? ""` at the submit boundary instead.
  lastName: z.string().trim().max(60).optional(),
  email: z.string().trim().toLowerCase().email("Enter a valid email address"),
  username: usernameSchema,
  password: z
    .string()
    .min(8, "Use at least 8 characters")
    .max(72, "Passwords cannot be longer than 72 characters"),
  acceptedTerms: z.literal(true, {
    message: "Please accept the terms to continue",
  }),
});

export type SignUpInput = z.infer<typeof signUpSchema>;

export const forgotPasswordSchema = z.object({
  email: z.string().trim().toLowerCase().email("Enter a valid email address"),
});

export type ForgotPasswordInput = z.infer<typeof forgotPasswordSchema>;

// The 6-digit code is validated by `isCompleteOtp` in `lib/otp`, which the OTP
// boxes already use to decide when the submit button is enabled. A second
// schema here would be a second definition of "complete" to keep in step.

export const resetPasswordSchema = z
  .object({
    password: z
      .string()
      .min(8, "Use at least 8 characters")
      .max(72, "Passwords cannot be longer than 72 characters"),
    confirmPassword: z.string().min(1, "Confirm your new password"),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Both passwords must match",
    path: ["confirmPassword"],
  });

export type ResetPasswordInput = z.infer<typeof resetPasswordSchema>;

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "Enter your current password"),
    newPassword: z
      .string()
      .min(8, "Use at least 8 characters")
      .max(72, "Passwords cannot be longer than 72 characters"),
    confirmPassword: z.string().min(1, "Confirm your new password"),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Both passwords must match",
    path: ["confirmPassword"],
  });

export type ChangePasswordInput = z.infer<typeof changePasswordSchema>;

/* --- addresses ------------------------------------------------------------ */

/**
 * Mirrors every CHECK on `public.addresses`:
 *   label 1..40, full_name 1..120, phone 10..15, city 1..80, state 1..80,
 *   postal_code ~ ^[0-9]{5,10}$
 */
export const addressSchema = z.object({
  label: z
    .string()
    .trim()
    .min(1, "Give this address a name")
    .max(40, "Please keep this under 40 characters"),
  full_name: trimmed(120),
  phone: z
    .string()
    .trim()
    .regex(/^[0-9+\s-]{10,15}$/, "Enter a valid 10–15 digit phone number"),
  house_number: z.string().trim().max(80).optional(),
  street: trimmed(80),
  landmark: z.string().trim().max(80).optional(),
  city: trimmed(80),
  state: trimmed(80),
  postal_code: z
    .string()
    .trim()
    .regex(/^[0-9]{5,10}$/, "Enter a valid 5–10 digit pincode"),
  latitude: z.number().min(-90).max(90).nullable().optional(),
  longitude: z.number().min(-180).max(180).nullable().optional(),
});

export type AddressInput = z.infer<typeof addressSchema>;

/* --- support -------------------------------------------------------------- */

/** Mirrors `complaints`: subject 1..160, description 1..10000. */
export const complaintSchema = z.object({
  subject: z
    .string()
    .trim()
    .min(1, "Give your message a subject")
    .max(160, "Please keep this under 160 characters"),
  description: z
    .string()
    .trim()
    .min(10, "Please tell us a little more (at least 10 characters)")
    .max(10_000, "Please keep this under 10,000 characters"),
  orderId: z.string().uuid().optional().or(z.literal("")),
});

export type ComplaintInput = z.infer<typeof complaintSchema>;
