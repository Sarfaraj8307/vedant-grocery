/**
 * Thumbnail grid of the photos attached to a complaint thread.
 *
 * Server-friendly: it receives already-signed URLs (created in the thread page
 * with the user's session, so only the owner/admin can resolve them) and is
 * purely presentational. Each thumbnail links to its full-size image in a new
 * tab.
 */

export type SignedComplaintAttachment = {
  id: string;
  url: string;
  alt: string;
};

export function ComplaintAttachments({
  attachments,
}: {
  attachments: SignedComplaintAttachment[];
}) {
  if (attachments.length === 0) return null;

  return (
    <div className="mt-3">
      <p className="text-small font-medium text-ink">Photos you sent</p>
      <ul
        aria-label="Photos you sent"
        className="mt-1.5 grid grid-cols-3 gap-2 sm:grid-cols-4"
      >
        {attachments.map((attachment, index) => (
          <li
            key={attachment.id}
            className="aspect-square overflow-hidden rounded-md border border-line"
          >
            <a
              href={attachment.url}
              target="_blank"
              rel="noreferrer"
              aria-label={`Open photo ${index + 1} in a new tab`}
              className="block h-full w-full"
            >
              <img
                src={attachment.url}
                alt={attachment.alt}
                className="h-full w-full object-cover"
              />
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
}
