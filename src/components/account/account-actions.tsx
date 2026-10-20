"use client";

import { useRouter } from "next/navigation";
import { toast } from "sonner";

import { DangerZone } from "@/components/account/profile-form";
import { useCartSession } from "@/components/cart/cart-provider";
import { clearPersonalData } from "@/services/auth-service";

/**
 * Wires the destructive account actions to their services.
 * Split from the presentational `DangerZone` so the visual component stays
 * free of data access, per the brief's separation-of-layers requirement.
 */
export function AccountDangerZone() {
  const router = useRouter();
  const { clear } = useCartSession();

  return (
    <DangerZone
      onClearData={async () => {
        const result = await clearPersonalData();
        if (!result.ok) {
          toast.error(result.error);
          return;
        }
        // Clear the local basket copy too, otherwise it would be pushed back
        // up to the server on the next navigation.
        clear();
        toast.success("Your saved addresses and basket have been erased");
        router.refresh();
      }}
    />
  );
}
