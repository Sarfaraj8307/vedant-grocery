import { describe, expect, it } from "vitest";

import {
  buildComplaintAttachmentPath,
  validateComplaintAttachment,
} from "./support-service";

const ONE_MB = 1024 * 1024;
const FIVE_MB = 5 * ONE_MB;

function makeFile(name: string, size: number, type: string): File {
  return new File([new Uint8Array(size)], name, { type });
}

/**
 * `validateComplaintAttachment` mirrors the `complaint-images` bucket limits
 * (image/jpeg|png|webp, ≤5 MB). It gates what the form lets a customer select,
 * so these rules must stay in step with the storage policy.
 */
describe("validateComplaintAttachment", () => {
  it("accepts jpeg, png and webp within the 5 MB limit", () => {
    expect(validateComplaintAttachment(makeFile("a.jpg", 1, "image/jpeg"))).toBeNull();
    expect(
      validateComplaintAttachment(makeFile("b.png", ONE_MB, "image/png")),
    ).toBeNull();
    expect(
      validateComplaintAttachment(makeFile("c.webp", FIVE_MB, "image/webp")),
    ).toBeNull();
  });

  it("rejects disallowed content types", () => {
    expect(
      validateComplaintAttachment(makeFile("d.gif", 1, "image/gif")),
    ).not.toBeNull();
    expect(
      validateComplaintAttachment(makeFile("e.bmp", 1, "image/bmp")),
    ).not.toBeNull();
  });

  it("rejects images larger than 5 MB", () => {
    expect(
      validateComplaintAttachment(
        makeFile("big.jpg", FIVE_MB + 1, "image/jpeg"),
      ),
    ).not.toBeNull();
  });
});

/**
 * `buildComplaintAttachmentPath` must shape paths as `{uid}/{complaintId}/{uuid}-{name}`
 * because the `complaint_images_owner_insert` storage policy keys on the first
 * path segment being the caller's uid.
 */
describe("buildComplaintAttachmentPath", () => {
  it("puts the uid first, then complaintId, then a sanitized name", () => {
    const path = buildComplaintAttachmentPath("uid-123", "comp-456", "my photo!.jpg");
    expect(path.startsWith("uid-123/comp-456/")).toBe(true);
    expect(path).toContain("-my-photo-.jpg");
  });

  it("replaces unsafe characters and never leaves them in an path", () => {
    const path = buildComplaintAttachmentPath("uid", "comp", "a b?c#d");
    expect(path).toContain("a-b-c-d");
    expect(path).not.toMatch(/[^A-Za-z0-9._/-]/);
  });

  it("falls back to 'image' when the name sanitizes away to nothing", () => {
    const path = buildComplaintAttachmentPath("uid", "comp", "???");
    expect(path.endsWith("-image")).toBe(true);
  });
});
