import { Skeleton, SkeletonProductGrid } from "@/components/ui/skeleton";

/**
 * Root loading UI.
 *
 * Mirrors the shape of a catalogue page — heading, chip row, product grid — so a
 * slow navigation shows the *structure* of what is coming rather than a blank
 * screen (brief §30). Skeleton geometry matches the real components, so there is
 * no shift when the content arrives.
 */
export default function Loading() {
  return (
    <div className="shell flex flex-col gap-6 py-6 sm:py-8">
      <div className="flex flex-col gap-3">
        <Skeleton className="h-8 w-64 rounded-md" />
        <Skeleton className="h-4 w-80 rounded-full" />
      </div>

      <div className="flex flex-wrap gap-2">
        {Array.from({ length: 5 }).map((_, index) => (
          <Skeleton key={index} className="h-8 w-24 rounded-pill" />
        ))}
      </div>

      <SkeletonProductGrid count={8} />
    </div>
  );
}
