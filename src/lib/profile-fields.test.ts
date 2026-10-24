import { describe, expect, it } from "vitest";

import { avatarUrlFromMetadata, profileFieldsFromMetadata } from "./profile-fields";

/**
 * These cases come from real provider payloads. The email/password shape is
 * written by `signUpWithPassword()`; the Google shapes are what Supabase
 * actually puts in `user_metadata` after an OAuth return.
 *
 * The regression being pinned: Google supplies **no** `first_name`, so a
 * customer who signed up with Gmail used to get an empty name in `profiles`
 * and a blank account page.
 */
describe("profileFieldsFromMetadata", () => {
  it("reads the fields written by the email/password signup form", () => {
    expect(
      profileFieldsFromMetadata({
        first_name: "Asha",
        last_name: "Kulkarni",
        username: "asha_k",
      }),
    ).toEqual({ firstName: "Asha", lastName: "Kulkarni", username: "asha_k" });
  });

  it("maps Google's given_name / family_name", () => {
    expect(
      profileFieldsFromMetadata({
        iss: "https://accounts.google.com",
        given_name: "Rohit",
        family_name: "Sharma",
        user_name: "rohit",
        email: "rohit@example.com",
      }),
    ).toEqual({ firstName: "Rohit", lastName: "Sharma", username: "rohit" });
  });

  it("splits a display name when Google sends no given/family name", () => {
    expect(profileFieldsFromMetadata({ full_name: "Meera Nair" })).toEqual({
      firstName: "Meera",
      lastName: "Nair",
      username: undefined,
    });
  });

  it("falls back to `name` when `full_name` is absent", () => {
    expect(profileFieldsFromMetadata({ name: "Arjun Patel" })).toEqual({
      firstName: "Arjun",
      lastName: "Patel",
      username: undefined,
    });
  });

  it("keeps a single-word display name as the first name", () => {
    expect(profileFieldsFromMetadata({ name: "Kiran" })).toEqual({
      firstName: "Kiran",
      lastName: "",
      username: undefined,
    });
  });

  it("rejects a Google handle that Postgres would reject", () => {
    // Google allows dots; `profiles_username_check` does not. Passing this
    // through would raise 23514 and leave the customer with no profile row.
    expect(profileFieldsFromMetadata({ user_name: "john.doe" }).username).toBeUndefined();
    expect(profileFieldsFromMetadata({ user_name: "ab" }).username).toBeUndefined();
    expect(
      profileFieldsFromMetadata({ user_name: "this_handle_is_far_too_long_for_the_rule" })
        .username,
    ).toBeUndefined();
  });

  it("lowercases a handle that is otherwise valid", () => {
    expect(profileFieldsFromMetadata({ user_name: "Rohit_Sharma" }).username).toBe(
      "rohit_sharma",
    );
  });

  it("prefers the explicit signup username over the provider handle", () => {
    expect(
      profileFieldsFromMetadata({ username: "chosen_name", user_name: "provider_name" })
        .username,
    ).toBe("chosen_name");
  });

  it("returns empty strings for an empty blob rather than undefined", () => {
    expect(profileFieldsFromMetadata({})).toEqual({
      firstName: "",
      lastName: "",
      username: undefined,
    });
  });

  it("ignores non-string values instead of throwing", () => {
    expect(
      profileFieldsFromMetadata({ first_name: 42, last_name: null, name: {} }),
    ).toEqual({ firstName: "", lastName: "", username: undefined });
  });
});

describe("avatarUrlFromMetadata", () => {
  it("returns Google's avatar_url", () => {
    expect(
      avatarUrlFromMetadata({
        avatar_url: "https://lh3.googleusercontent.com/a/abc123",
        picture: "https://lh3.googleusercontent.com/a/abc123",
      }),
    ).toBe("https://lh3.googleusercontent.com/a/abc123");
  });

  it("falls back to `picture`", () => {
    expect(avatarUrlFromMetadata({ picture: "https://example.com/me.png" })).toBe(
      "https://example.com/me.png",
    );
  });

  it("returns null when there is no picture", () => {
    expect(avatarUrlFromMetadata({ first_name: "Asha" })).toBeNull();
  });

  it("rejects anything that is not an absolute https URL", () => {
    // A relative or javascript: value must never reach an <img src>.
    expect(avatarUrlFromMetadata({ avatar_url: "/local.png" })).toBeNull();
    expect(avatarUrlFromMetadata({ avatar_url: "http://insecure.example/x.png" })).toBeNull();
    expect(avatarUrlFromMetadata({ avatar_url: "javascript:alert(1)" })).toBeNull();
    expect(avatarUrlFromMetadata({ avatar_url: 123 })).toBeNull();
  });
});
