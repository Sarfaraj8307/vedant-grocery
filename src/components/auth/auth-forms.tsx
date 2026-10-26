"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { zodResolver } from "@hookform/resolvers/zod";
import { Controller, useForm, useWatch } from "react-hook-form";
import { Check, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/menu";
import { Field, Input } from "@/components/ui/input";
import { OrDivider } from "@/components/auth/auth-shell";
import { authInputClass, authSubmitClass } from "@/components/auth/auth-card";
import { PasswordInput } from "@/components/auth/password-input";
import {
  forgotPasswordSchema,
  otpTokenSchema,
  resetPasswordSchema,
  signInSchema,
  signUpSchema,
  usernameSchema,
  type ForgotPasswordInput,
  type ResetPasswordInput,
  type SignInInput,
  type SignUpInput,
} from "@/lib/validation";
import {
  checkUsername,
  isEmailUnconfirmed,
  resendConfirmation,
  sendRecoveryOtp,
  sendSignupOtp,
  signInWithGoogle,
  signInWithPassword,
  updatePassword,
  verifyRecoveryOtp,
  verifySignupOtp,
} from "@/services/auth-service";

/**
 * Auth forms.
 *
 * Every form is a real Supabase Auth call — there is no simulated success path.
 * Provider errors are mapped to actionable copy by `friendlyAuthError`, and an
 * unavailable provider (e.g. Google not yet enabled on the project) produces an
 * explicit message rather than a silent no-op.
 */

/** Shared Google button. */
function GoogleButton({ next, label }: { next: string; label: string }) {
  const [working, setWorking] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  return (
    <div>
      <Button
        type="button"
        variant="outline"
        block
        loading={working}
        loadingLabel="Opening Google…"
        onClick={async () => {
          setWorking(true);
          setError(null);
          const result = await signInWithGoogle(next);
          if (!result.ok) {
            setError(result.error);
            setWorking(false);
          }
          // On success the browser is already navigating to Google.
        }}
      >
        <GoogleGlyph />
        {label}
      </Button>
      {error ? (
        <p role="alert" className="mt-2 text-small text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
}

function GoogleGlyph() {
  return (
    <svg viewBox="0 0 18 18" className="size-[18px]" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.7-1.57 2.68-3.88 2.68-6.62Z"
      />
      <path
        fill="#34A853"
        d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.8.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.7H.96v2.33A9 9 0 0 0 9 18Z"
      />
      <path
        fill="#FBBC05"
        d="M3.97 10.72a5.4 5.4 0 0 1 0-3.44V4.95H.96a9 9 0 0 0 0 8.1l3.01-2.33Z"
      />
      <path
        fill="#EA4335"
        d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.46.9 11.43 0 9 0A9 9 0 0 0 .96 4.95l3.01 2.33C4.68 5.16 6.66 3.58 9 3.58Z"
      />
    </svg>
  );
}

/** Where to send the customer after a successful auth. */
function safeNext(raw: string | null): string {
  if (!raw) return "/";
  // Only same-origin relative paths — an open redirect via ?next= is a real,
  // commonly-exploited bug and costs nothing to prevent.
  if (!raw.startsWith("/") || raw.startsWith("//")) return "/";
  return raw;
}

/* ========================================================================== */
/* Sign in                                                                    */
/* ========================================================================== */

export function LoginForm({ googleEnabled = false }: { googleEnabled?: boolean }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeNext(searchParams.get("next"));
  const confirmed = searchParams.get("confirmed") === "1";

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<SignInInput>({ resolver: zodResolver(signInSchema) });

  /** Set only when the provider rejected the sign-in for an unconfirmed address. */
  const [unconfirmedEmail, setUnconfirmedEmail] = React.useState<string | null>(null);
  const [resending, setResending] = React.useState(false);
  const [resendNote, setResendNote] = React.useState<string | null>(null);

  async function onSubmit(values: SignInInput) {
    setUnconfirmedEmail(null);
    setResendNote(null);

    const result = await signInWithPassword(values);
    if (!result.ok) {
      setError("root", { message: result.error });
      if (isEmailUnconfirmed(result.code, result.error)) {
        setUnconfirmedEmail(values.email);
      }
      return;
    }
    router.push(next);
    router.refresh();
  }

  async function resend() {
    if (!unconfirmedEmail) return;
    setResending(true);
    setResendNote(null);

    const result = await resendConfirmation(unconfirmedEmail);
    setResending(false);
    setResendNote(
      result.ok
        ? "Sent. Check your inbox — and the spam folder, just in case."
        : result.error,
    );
  }

  return (
    <div>
      {confirmed ? (
        <p className="mb-4 flex items-start gap-2 rounded-md border border-success-bg bg-success-bg px-3 py-2.5 text-small text-success">
          <Check className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          Your email is confirmed. Please sign in to continue.
        </p>
      ) : null}

      {/* Rendered only when the Supabase project actually has Google enabled
          (probed server-side). A sign-in button that always errors would be a
          fake control, which this project does not ship. */}
      {googleEnabled ? (
        <>
          <GoogleButton next={next} label="Log in with Google" />
          <OrDivider />
        </>
      ) : null}

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        {/* Suppressed when the dedicated unconfirmed panel below is shown —
            two copies of the same message reads as a bug. */}
        {errors.root && !unconfirmedEmail ? (
          <p
            role="alert"
            className="mb-4 rounded-md border border-danger-bg bg-danger-bg px-3 py-2.5 text-small text-danger"
          >
            {errors.root.message}
          </p>
        ) : null}

        <Field label="Email" htmlFor="email" error={errors.email?.message} required>
          <Input
            id="email"
            type="email"
            inputMode="email"
            autoComplete="email"
            autoFocus
            placeholder="you@example.com"
            invalid={Boolean(errors.email)}
            className={authInputClass}
            {...register("email")}
          />
        </Field>

        <Field
          label="Password"
          htmlFor="password"
          error={errors.password?.message}
          required
        >
          <PasswordInput
            id="password"
            autoComplete="current-password"
            placeholder="Your password"
            invalid={Boolean(errors.password)}
            className={authInputClass}
            {...register("password")}
          />
        </Field>

        <Button
          type="submit"
          block
          size="lg"
          className={authSubmitClass}
          loading={isSubmitting}
          loadingLabel="Signing in…"
        >
          Sign in
        </Button>
      </form>

      {/*
        The requirement: an unconfirmed customer must be told to confirm, not
        left with a generic failure. Offering the resend here is the difference
        between a dead end and a fix — the original email may have expired or
        never arrived.
      */}
      {unconfirmedEmail ? (
        <div
          role="alert"
          className="mt-4 rounded-md border border-marigold-200 bg-marigold-50 px-3 py-3"
        >
          <p className="text-small font-medium text-ink">Please confirm your email first</p>
          <p className="mt-1 text-small text-ink-muted">
            We sent a confirmation link to{" "}
            <span className="font-medium text-ink">{unconfirmedEmail}</span>. Open it
            and you will be signed in automatically. If it has not arrived, we can
            send another.
          </p>
          {resendNote ? (
            <p className="mt-2 text-small text-ink-muted">{resendNote}</p>
          ) : null}
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="mt-2.5"
            loading={resending}
            loadingLabel="Sending…"
            onClick={() => void resend()}
          >
            Resend confirmation email
          </Button>
        </div>
      ) : null}

      <Link
        href="/forgot-password"
        className="mt-4 block text-center text-small text-ink-muted underline-offset-2 hover:text-ink hover:underline"
      >
        Request a New Password
      </Link>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* OTP step — shared by signup and password recovery                          */
/* -------------------------------------------------------------------------- */

function OtpStep({
  email,
  title = "Enter the code we sent you",
  buttonLabel = "Verify & continue",
  onSubmit,
  onResend,
  onBack,
  error,
  verifying = false,
  resending = false,
}: {
  email: string;
  title?: string;
  buttonLabel?: string;
  onSubmit: (token: string) => void;
  onResend: () => void;
  onBack: () => void;
  error?: string | null;
  verifying?: boolean;
  resending?: boolean;
}) {
  const [token, setToken] = React.useState("");
  const tokenError = otpTokenSchema.safeParse(token).success ? null : "Enter the 6-digit code";

  return (
    <div>
      <div className="rounded-md border border-line bg-surface-muted px-3.5 py-3 text-small">
        <p className="text-ink-muted">We sent a code to</p>
        <p className="mt-0.5 font-medium text-ink">{email}</p>
      </div>

      {error ? (
        <p
          role="alert"
          className="mb-3 mt-3 rounded-md border border-danger-bg bg-danger-bg px-3 py-2.5 text-small text-danger"
        >
          {error}
        </p>
      ) : null}

      <form
        className="mt-4"
        onSubmit={(event) => {
          event.preventDefault();
          if (otpTokenSchema.safeParse(token).success) onSubmit(token.trim());
        }}
        noValidate
      >
        <Field label="One-time code" htmlFor="otp-code" error={token ?? tokenError}>
          <Input
            id="otp-code"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            placeholder="123456"
            value={token}
            onChange={(event) => setToken(event.target.value.replace(/\D/g, "").slice(0, 6))}
            className="text-center text-body-lg tracking-[0.3em]"
          />
        </Field>

        <Button
          type="submit"
          block
          size="lg"
          className={authSubmitClass + " mt-2"}
          loading={verifying}
          loadingLabel="Verifying…"
          disabled={!otpTokenSchema.safeParse(token).success}
        >
          {buttonLabel}
        </Button>
      </form>

      <div className="mt-4 flex items-center justify-between gap-2 text-small">
        <button
          type="button"
          onClick={onBack}
          className="text-ink-muted underline-offset-2 hover:text-ink hover:underline"
        >
          Back
        </button>
        <button
          type="button"
          onClick={onResend}
          disabled={resending}
          className="font-medium text-leaf-700 underline-offset-2 hover:underline disabled:cursor-not-allowed disabled:text-ink-subtle"
        >
          {resending ? "Sending…" : "Resend the code"}
        </button>
      </div>
    </div>
  );
}

/* ========================================================================== */
/* Sign up                                                                    */
/* ========================================================================== */

export function SignUpForm({
  googleEnabled = false,
  nextPath,
}: {
  googleEnabled?: boolean;
  /** Resolved server-side so the form does not depend on the query string. */
  nextPath?: string;
}) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeNext(nextPath ?? searchParams.get("next"));

  /** Result of the last completed lookup, tagged with the name it was for. */
  const [lookup, setLookup] = React.useState<{
    for: string;
    available: boolean;
  } | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<SignUpInput>({
    resolver: zodResolver(signUpSchema),
    // `acceptedTerms` is a `z.literal(true)`, so it must start unset — the form
    // cannot be submitted without an explicit tick.
    defaultValues: { acceptedTerms: false as unknown as true },
  });

  // `useWatch` rather than `form.watch()`: the latter returns an unmemoizable
  // function, which makes the React Compiler skip this whole component.
  const usernameWatch = useWatch({ control, name: "username" });
  const usernameValue = (usernameWatch ?? "").trim().toLowerCase();
  const usernameSyntaxValid =
    usernameValue.length > 0 && usernameSchema.safeParse(usernameValue).success;

  /**
   * Live username availability via `check_username_available()`.
   *
   * Debounced to one request per 400ms of typing, and only fired once the name is
   * syntactically valid — there is no point asking the server about `"a"`.
   * The in-flight state is *derived* rather than stored, so nothing sets state
   * synchronously during a render or effect body; only the resolved promise does.
   */
  React.useEffect(() => {
    if (!usernameSyntaxValid) return;

    let cancelled = false;
    const timer = window.setTimeout(() => {
      void checkUsername(usernameValue).then((available) => {
        if (cancelled) return;
        setLookup({ for: usernameValue, available });
      });
    }, 400);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [usernameValue, usernameSyntaxValid]);

  const usernameState: "idle" | "checking" | "free" | "taken" | "invalid" =
    usernameValue.length === 0
      ? "idle"
      : !usernameSyntaxValid
        ? "invalid"
        : lookup?.for === usernameValue
          ? lookup.available
            ? "free"
            : "taken"
          : "checking";

  // --- Email OTP sign-up state ---
  // Step 1: profile + password form. Step 2: enter the emailed code. Only after
  // the code is verified do we apply the chosen password to the new account.
  const [step, setStep] = React.useState<"form" | "otp">("form");
  const [otpEmail, setOtpEmail] = React.useState("");
  const [otpPassword, setOtpPassword] = React.useState("");
  const [otpMeta, setOtpMeta] = React.useState({
    firstName: "",
    lastName: "",
    username: "",
  });
  const [otpError, setOtpError] = React.useState<string | null>(null);
  const [sendingOtp, setSendingOtp] = React.useState(false);
  const [verifyingOtp, setVerifyingOtp] = React.useState(false);
  const [resending, setResending] = React.useState(false);

  async function onSubmit(values: SignUpInput) {
    // Re-check server truth at submit time to close the race between the last
    // debounced lookup and the submit click. If the name was taken in the
    // meantime, block the submit with an actionable message rather than letting
    // the database 23505 bubble as a generic error.
    const submitName = values.username.trim().toLowerCase();
    if (usernameState === "taken") {
      const stillTaken = !(await checkUsername(submitName));
      if (stillTaken) {
        setError("username", { message: "That username is already taken" });
        return;
      }
    } else if (usernameState === "checking") {
      const available = await checkUsername(submitName);
      if (!available) {
        setError("username", { message: "That username is already taken" });
        return;
      }
    }

    setSendingOtp(true);
    setOtpError(null);
    const result = await sendSignupOtp({
      email: values.email,
      firstName: values.firstName,
      lastName: values.lastName ?? "",
      username: values.username,
    });
    setSendingOtp(false);

    if (!result.ok) {
      setError("root", { message: result.error });
      return;
    }

    setOtpEmail(values.email);
    setOtpPassword(values.password);
    setOtpMeta({
      firstName: values.firstName,
      lastName: values.lastName ?? "",
      username: values.username,
    });
    setStep("otp");
  }

  async function confirmOtp(token: string) {
    setVerifyingOtp(true);
    setOtpError(null);

    const result = await verifySignupOtp({ email: otpEmail, token });
    if (!result.ok) {
      setOtpError(result.error);
      setVerifyingOtp(false);
      return;
    }

    // The OTP confirmed the account and created a session. Now set the password
    // the new customer chose on the form.
    const pw = await updatePassword(otpPassword);
    if (!pw.ok) {
      setOtpError(pw.error);
      setVerifyingOtp(false);
      return;
    }

    router.push(next);
    router.refresh();
  }

  async function resendOtp() {
    setResending(true);
    setOtpError(null);
    const result = await sendSignupOtp({ email: otpEmail, ...otpMeta });
    setResending(false);
    if (!result.ok) setOtpError(result.error);
  }

  if (step === "otp") {
    return (
      <OtpStep
        email={otpEmail}
        onSubmit={confirmOtp}
        onResend={resendOtp}
        onBack={() => setStep("form")}
        error={otpError}
        verifying={verifyingOtp}
        resending={resending}
      />
    );
  }

  return (
    <div>
      {googleEnabled ? (
        <>
          <GoogleButton next={next} label="Sign up with Google" />
          <OrDivider />
        </>
      ) : null}

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        {errors.root ? (
          <p
            role="alert"
            className="mb-4 rounded-md border border-danger-bg bg-danger-bg px-3 py-2.5 text-small text-danger"
          >
            {errors.root.message}
          </p>
        ) : null}

        <div className="grid gap-x-3 sm:grid-cols-2">
          <Field
            label="First name"
            htmlFor="firstName"
            error={errors.firstName?.message}
            required
          >
            <Input
              id="firstName"
              autoComplete="given-name"
              autoFocus
              placeholder="Asha"
              invalid={Boolean(errors.firstName)}
              className={authInputClass}
              {...register("firstName")}
            />
          </Field>
          <Field label="Last name" htmlFor="lastName" error={errors.lastName?.message}>
            <Input
              id="lastName"
              autoComplete="family-name"
              placeholder="Kulkarni"
              invalid={Boolean(errors.lastName)}
              className={authInputClass}
              {...register("lastName")}
            />
          </Field>
        </div>

        <Field label="Email" htmlFor="signup-email" error={errors.email?.message} required>
          <Input
            id="signup-email"
            type="email"
            inputMode="email"
            autoComplete="email"
            placeholder="you@example.com"
            invalid={Boolean(errors.email)}
            className={authInputClass}
            {...register("email")}
          />
        </Field>

        <Field
          label="Username"
          htmlFor="username"
          error={errors.username?.message}
          hint="Lowercase letters, numbers, underscores."
          required
          className="mb-2"
        >
          <div className="relative">
            <Input
              id="username"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              placeholder="asha_k"
              invalid={Boolean(errors.username) || usernameState === "taken"}
              className={authInputClass}
              {...register("username")}
            />
            <UsernameStatus state={usernameState} />
          </div>
        </Field>

        <Field
          label="Password"
          htmlFor="signup-password"
          error={errors.password?.message}
          hint="At least 8 characters."
          required
          className="mb-2"
        >
          <PasswordInput
            id="signup-password"
            autoComplete="new-password"
            placeholder="Choose a password"
            invalid={Boolean(errors.password)}
            className={authInputClass}
            {...register("password")}
          />
        </Field>

        <div className="mb-4">
          <label className="flex cursor-pointer items-start gap-2.5">
            {/* Radix Checkbox is not a native input, so it is wired through
                react-hook-form's Controller rather than `register`. */}
            <Controller
              name="acceptedTerms"
              control={control}
              render={({ field }) => (
                <Checkbox
                  className="mt-0.5"
                  aria-describedby="terms-note"
                  checked={field.value === true}
                  onCheckedChange={(checked) => field.onChange(checked === true)}
                  onBlur={field.onBlur}
                  ref={field.ref}
                />
              )}
            />
            <span id="terms-note" className="text-small text-ink-muted">
              I agree to the{" "}
              <Link href="/terms" className="text-leaf-700 underline underline-offset-2">
                terms
              </Link>{" "}
              and{" "}
              <Link href="/privacy" className="text-leaf-700 underline underline-offset-2">
                privacy policy
              </Link>
              .
            </span>
          </label>
          {errors.acceptedTerms ? (
            <p role="alert" className="mt-1 text-small text-danger">
              {errors.acceptedTerms.message}
            </p>
          ) : null}
        </div>

        <Button
          type="submit"
          block
          size="lg"
          className={authSubmitClass}
          loading={isSubmitting || sendingOtp}
          loadingLabel="Sending code…"
        >
          Create account
        </Button>
      </form>
    </div>
  );
}

function UsernameStatus({
  state,
}: {
  state: "idle" | "checking" | "free" | "taken" | "invalid";
}) {
  if (state === "idle") return null;

  const config = {
    checking: { text: "Checking…", className: "text-ink-subtle", Icon: null },
    free: {
      text: "Available",
      className: "text-success",
      Icon: Check,
    },
    taken: { text: "Already taken", className: "text-danger", Icon: X },
    invalid: { text: "3–24 chars: a–z, 0–9, _", className: "text-ink-subtle", Icon: null },
  }[state];

  const Icon = config.Icon;

  return (
    <span
      aria-live="polite"
      className={`pointer-events-none absolute right-3 top-1/2 flex -translate-y-1/2 items-center gap-1 text-small ${config.className}`}
    >
      {Icon ? <Icon className="size-3.5" strokeWidth={3} aria-hidden="true" /> : null}
      {config.text}
    </span>
  );
}

/* ========================================================================== */
/* Forgot password                                                            */
/* ========================================================================== */

export function ForgotPasswordForm() {
  const router = useRouter();
  const [step, setStep] = React.useState<"form" | "otp" | "new-password">("form");
  const [email, setEmail] = React.useState("");
  const [otpError, setOtpError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);
  const [resending, setResending] = React.useState(false);
  const [done, setDone] = React.useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<ForgotPasswordInput>({ resolver: zodResolver(forgotPasswordSchema) });

  const resetForm = useForm<ResetPasswordInput>({ resolver: zodResolver(resetPasswordSchema) });

  async function requestCode(values: ForgotPasswordInput) {
    setBusy(true);
    setOtpError(null);
    const result = await sendRecoveryOtp(values.email);
    setBusy(false);
    if (!result.ok) {
      setError("root", { message: result.error });
      return;
    }
    setEmail(values.email);
    setStep("otp");
  }

  async function confirmOtp(token: string) {
    setBusy(true);
    setOtpError(null);
    const result = await verifyRecoveryOtp({ email, token });
    setBusy(false);
    if (!result.ok) {
      setOtpError(result.error);
      return;
    }
    setStep("new-password");
  }

  async function resend() {
    setResending(true);
    setOtpError(null);
    const result = await sendRecoveryOtp(email);
    setResending(false);
    if (!result.ok) setOtpError(result.error);
  }

  async function submitNewPassword(values: ResetPasswordInput) {
    setBusy(true);
    const result = await updatePassword(values.password);
    setBusy(false);
    if (!result.ok) {
      resetForm.setError("root", { message: result.error });
      return;
    }
    setDone(true);
    window.setTimeout(() => router.push("/login"), 1500);
  }

  if (step === "otp") {
    return (
      <OtpStep
        email={email}
        onSubmit={confirmOtp}
        onResend={resend}
        onBack={() => setStep("form")}
        error={otpError}
        verifying={busy}
        resending={resending}
      />
    );
  }

  if (step === "new-password") {
    if (done) {
      return (
        <p
          role="status"
          className="rounded-md border border-success-bg bg-success-bg px-3 py-2.5 text-small text-success"
        >
          Your password has been updated. Taking you to sign in…
        </p>
      );
    }

    return (
      <form onSubmit={resetForm.handleSubmit(submitNewPassword)} noValidate>
        {resetForm.formState.errors.root ? (
          <p
            role="alert"
            className="mb-4 rounded-md border border-danger-bg bg-danger-bg px-3 py-2.5 text-small text-danger"
          >
            {resetForm.formState.errors.root.message}
          </p>
        ) : null}

        <p className="mb-4 text-small text-ink-muted">
          We have verified your email. Choose a new password.
        </p>

        <Field
          label="New password"
          htmlFor="fp-new-password"
          error={resetForm.formState.errors.password?.message}
          hint="At least 8 characters."
          required
        >
          <Input
            id="fp-new-password"
            type="password"
            autoComplete="new-password"
            autoFocus
            invalid={Boolean(resetForm.formState.errors.password)}
            {...resetForm.register("password")}
          />
        </Field>

        <Field
          label="Confirm new password"
          htmlFor="fp-confirm-password"
          error={resetForm.formState.errors.confirmPassword?.message}
          required
        >
          <Input
            id="fp-confirm-password"
            type="password"
            autoComplete="new-password"
            invalid={Boolean(resetForm.formState.errors.confirmPassword)}
            {...resetForm.register("confirmPassword")}
          />
        </Field>

        <Button
          type="submit"
          block
          size="lg"
          loading={busy}
          loadingLabel="Updating…"
        >
          Update password
        </Button>
      </form>
    );
  }

  return (
    <form onSubmit={handleSubmit(requestCode)} noValidate>
      {errors.root ? (
        <p
          role="alert"
          className="mb-4 rounded-md border border-danger-bg bg-danger-bg px-3 py-2.5 text-small text-danger"
        >
          {errors.root.message}
        </p>
      ) : null}

      <Field label="Email" htmlFor="reset-email" error={errors.email?.message} required>
        <Input
          id="reset-email"
          type="email"
          inputMode="email"
          autoComplete="email"
          autoFocus
          invalid={Boolean(errors.email)}
          {...register("email")}
        />
      </Field>

      <Button type="submit" block size="lg" loading={isSubmitting || busy} loadingLabel="Sending code…">
        Send code
      </Button>
    </form>
  );
}

/* ========================================================================== */
/* Reset password (post-recovery)                                             */
/* ========================================================================== */

export function ResetPasswordForm() {
  const router = useRouter();
  const [done, setDone] = React.useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<ResetPasswordInput>({ resolver: zodResolver(resetPasswordSchema) });

  async function onSubmit(values: ResetPasswordInput) {
    const result = await updatePassword(values.password);
    if (!result.ok) {
      setError("root", { message: result.error });
      return;
    }
    setDone(true);
    window.setTimeout(() => {
      router.push("/account");
      router.refresh();
    }, 1500);
  }

  if (done) {
    return (
      <p
        role="status"
        className="rounded-md border border-success-bg bg-success-bg px-3 py-2.5 text-small text-success"
      >
        Your password has been updated. Taking you to your account…
      </p>
    );
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      {errors.root ? (
        <p
          role="alert"
          className="mb-4 rounded-md border border-danger-bg bg-danger-bg px-3 py-2.5 text-small text-danger"
        >
          {errors.root.message}
        </p>
      ) : null}

      <Field
        label="New password"
        htmlFor="new-password"
        error={errors.password?.message}
        hint="At least 8 characters."
        required
      >
        <Input
          id="new-password"
          type="password"
          autoComplete="new-password"
          autoFocus
          invalid={Boolean(errors.password)}
          {...register("password")}
        />
      </Field>

      <Field
        label="Confirm new password"
        htmlFor="confirm-password"
        error={errors.confirmPassword?.message}
        required
      >
        <Input
          id="confirm-password"
          type="password"
          autoComplete="new-password"
          invalid={Boolean(errors.confirmPassword)}
          {...register("confirmPassword")}
        />
      </Field>

      <Button type="submit" block size="lg" loading={isSubmitting} loadingLabel="Updating…">
        Update password
      </Button>
    </form>
  );
}
