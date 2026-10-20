import {
  Bike,
  CheckCheck,
  CircleX,
  Inbox,
  PackageCheck,
  ShoppingBasket,
  ThumbsUp,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { ORDER_STATUS_META } from "@/lib/format";
import type { OrderStatus } from "@/types/database";

/**
 * Order status badge — DESIGN_SYSTEM §9.
 *
 * Status is communicated three ways: a **word**, a **distinct icon shape**, and
 * a colour. Colour alone would fail WCAG 1.4.1 and is genuinely unusable for a
 * customer who cannot distinguish the marigold "packing" from the green
 * "confirmed" state.
 */
const STATUS_STYLES: Record<
  OrderStatus,
  { variant: "info" | "leaf" | "marigold" | "success" | "danger"; Icon: typeof Inbox }
> = {
  PENDING: { variant: "info", Icon: Inbox },
  CONFIRMED: { variant: "leaf", Icon: ThumbsUp },
  PROCESSING: { variant: "marigold", Icon: ShoppingBasket },
  READY_FOR_DELIVERY: { variant: "marigold", Icon: PackageCheck },
  OUT_FOR_DELIVERY: { variant: "leaf", Icon: Bike },
  DELIVERED: { variant: "success", Icon: CheckCheck },
  CANCELLED: { variant: "danger", Icon: CircleX },
};

export function OrderStatusBadge({
  status,
  size = "md",
}: {
  status: OrderStatus;
  size?: "sm" | "md" | "lg";
}) {
  const style = STATUS_STYLES[status] ?? STATUS_STYLES.PENDING;
  const { Icon } = style;

  return (
    <Badge variant={style.variant} size={size}>
      <Icon className="size-3.5" aria-hidden="true" />
      {ORDER_STATUS_META[status]?.label ?? status}
    </Badge>
  );
}
