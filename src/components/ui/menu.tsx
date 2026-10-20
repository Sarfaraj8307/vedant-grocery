"use client";

import * as React from "react";
import * as DropdownMenuPrimitive from "@radix-ui/react-dropdown-menu";
import * as SelectPrimitive from "@radix-ui/react-select";
import * as RadioGroupPrimitive from "@radix-ui/react-radio-group";
import * as CheckboxPrimitive from "@radix-ui/react-checkbox";
import * as TabsPrimitive from "@radix-ui/react-tabs";
import * as AccordionPrimitive from "@radix-ui/react-accordion";
import * as LabelPrimitive from "@radix-ui/react-label";
import { Check, ChevronDown, Minus, Plus } from "lucide-react";
import { cn } from "@/lib/utils";

/* ========================================================================== */
/* Label                                                                      */
/* ========================================================================== */

export function Label({
  className,
  ...props
}: React.ComponentProps<typeof LabelPrimitive.Root>) {
  return (
    <LabelPrimitive.Root
      className={cn("text-small font-medium text-ink", className)}
      {...props}
    />
  );
}

/* ========================================================================== */
/* Dropdown menu — account menu, row actions                                  */
/* ========================================================================== */

export const DropdownMenu = DropdownMenuPrimitive.Root;
export const DropdownMenuTrigger = DropdownMenuPrimitive.Trigger;
export const DropdownMenuGroup = DropdownMenuPrimitive.Group;
export const DropdownMenuSub = DropdownMenuPrimitive.Sub;

export function DropdownMenuContent({
  className,
  sideOffset = 8,
  align = "end",
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Content>) {
  return (
    <DropdownMenuPrimitive.Portal>
      <DropdownMenuPrimitive.Content
        align={align}
        sideOffset={sideOffset}
        className={cn(
          "ui-menu-content z-50 min-w-[13rem] overflow-hidden rounded-lg border border-line bg-surface p-1.5 shadow-md",
          className,
        )}
        {...props}
      />
    </DropdownMenuPrimitive.Portal>
  );
}

export function DropdownMenuItem({
  className,
  inset,
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Item> & {
  inset?: boolean;
}) {
  return (
    <DropdownMenuPrimitive.Item
      className={cn(
        "relative flex cursor-pointer select-none items-center gap-2.5 rounded-sm px-2.5 py-2 text-body text-ink outline-none",
        "transition-colors duration-[var(--motion-instant)]",
        "focus:bg-surface-muted data-[highlighted]:bg-surface-muted",
        "data-[disabled]:pointer-events-none data-[disabled]:text-ink-subtle",
        "[&_svg]:size-[17px] [&_svg]:text-ink-muted",
        inset && "pl-9",
        className,
      )}
      {...props}
    />
  );
}

export function DropdownMenuLabel({
  className,
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Label>) {
  return (
    <DropdownMenuPrimitive.Label
      className={cn("px-2.5 py-1.5 text-caption text-ink-subtle", className)}
      {...props}
    />
  );
}

export function DropdownMenuSeparator({
  className,
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Separator>) {
  return (
    <DropdownMenuPrimitive.Separator
      className={cn("-mx-1.5 my-1.5 h-px bg-line", className)}
      {...props}
    />
  );
}

/* ========================================================================== */
/* Select — native-feeling, keyboard complete                                 */
/* ========================================================================== */

export const Select = SelectPrimitive.Root;
export const SelectValue = SelectPrimitive.Value;

export function SelectTrigger({
  className,
  children,
  invalid,
  ...props
}: React.ComponentProps<typeof SelectPrimitive.Trigger> & { invalid?: boolean }) {
  return (
    <SelectPrimitive.Trigger
      aria-invalid={invalid || undefined}
      className={cn(
        "flex h-11 w-full items-center justify-between gap-2 rounded-sm border-[1.5px] border-line-strong bg-surface px-3.5",
        "text-body text-ink shadow-xs data-[placeholder]:text-ink-subtle",
        "transition-[border-color,box-shadow] duration-[var(--motion-fast)]",
        "focus:border-leaf-600 focus:outline-none focus-visible:outline-none focus:ring-2 focus:ring-leaf-600/20",
        "disabled:cursor-not-allowed disabled:bg-surface-muted disabled:text-ink-subtle",
        "sm:h-10",
        invalid && "border-danger focus:border-danger focus:ring-danger/20",
        className,
      )}
      {...props}
    >
      <span className="truncate text-left">{children}</span>
      <SelectPrimitive.Icon asChild>
        <ChevronDown className="size-4 shrink-0 text-ink-subtle" aria-hidden="true" />
      </SelectPrimitive.Icon>
    </SelectPrimitive.Trigger>
  );
}

export function SelectContent({
  className,
  children,
  position = "popper",
  ...props
}: React.ComponentProps<typeof SelectPrimitive.Content>) {
  return (
    <SelectPrimitive.Portal>
      <SelectPrimitive.Content
        position={position}
        sideOffset={6}
        className={cn(
          "ui-menu-content relative z-50 max-h-72 min-w-[var(--radix-select-trigger-width)] overflow-hidden rounded-lg border border-line bg-surface shadow-md",
          className,
        )}
        {...props}
      >
        <SelectPrimitive.Viewport className="p-1.5">
          {children}
        </SelectPrimitive.Viewport>
      </SelectPrimitive.Content>
    </SelectPrimitive.Portal>
  );
}

export function SelectItem({
  className,
  children,
  ...props
}: React.ComponentProps<typeof SelectPrimitive.Item>) {
  return (
    <SelectPrimitive.Item
      className={cn(
        "relative flex w-full cursor-pointer select-none items-center rounded-sm py-2 pl-2.5 pr-8 text-body text-ink outline-none",
        "data-[highlighted]:bg-surface-muted",
        "data-[disabled]:pointer-events-none data-[disabled]:text-ink-subtle",
        className,
      )}
      {...props}
    >
      <SelectPrimitive.ItemText>{children}</SelectPrimitive.ItemText>
      <SelectPrimitive.ItemIndicator className="absolute right-2.5">
        <Check className="size-4 text-leaf-600" aria-hidden="true" />
      </SelectPrimitive.ItemIndicator>
    </SelectPrimitive.Item>
  );
}

/* ========================================================================== */
/* Radio group — delivery slots, saved addresses                              */
/* ========================================================================== */

export const RadioGroup = RadioGroupPrimitive.Root;

export function RadioGroupItem({
  className,
  ...props
}: React.ComponentProps<typeof RadioGroupPrimitive.Item>) {
  return (
    <RadioGroupPrimitive.Item
      className={cn(
        "grid size-5 shrink-0 place-items-center rounded-full border-[1.5px] border-line-strong bg-surface",
        "transition-[border-color,background-color] duration-[var(--motion-fast)]",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-leaf-600/25",
        "data-[state=checked]:border-leaf-600 data-[state=checked]:bg-leaf-600",
        "disabled:cursor-not-allowed disabled:opacity-50",
        className,
      )}
      {...props}
    >
      <RadioGroupPrimitive.Indicator
        forceMount
        className="size-2 rounded-full bg-surface data-[state=unchecked]:opacity-0"
      />
    </RadioGroupPrimitive.Item>
  );
}

/* ========================================================================== */
/* Checkbox                                                                   */
/* ========================================================================== */

export function Checkbox({
  className,
  ...props
}: React.ComponentProps<typeof CheckboxPrimitive.Root>) {
  return (
    <CheckboxPrimitive.Root
      className={cn(
        "grid size-5 shrink-0 place-items-center rounded-xs border-[1.5px] border-line-strong bg-surface",
        "transition-colors duration-[var(--motion-fast)]",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-leaf-600/25",
        "data-[state=checked]:border-leaf-600 data-[state=checked]:bg-leaf-600",
        "disabled:cursor-not-allowed disabled:opacity-50",
        className,
      )}
      {...props}
    >
      <CheckboxPrimitive.Indicator className="text-white">
        <Check className="size-3.5" strokeWidth={3} aria-hidden="true" />
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  );
}

/* ========================================================================== */
/* Tabs                                                                      */
/* ========================================================================== */

export const Tabs = TabsPrimitive.Root;

export function TabsList({
  className,
  ...props
}: React.ComponentProps<typeof TabsPrimitive.List>) {
  return (
    <TabsPrimitive.List
      className={cn(
        "inline-flex items-center gap-1 rounded-pill border border-line bg-surface-muted p-1",
        className,
      )}
      {...props}
    />
  );
}

export function TabsTrigger({
  className,
  ...props
}: React.ComponentProps<typeof TabsPrimitive.Trigger>) {
  return (
    <TabsPrimitive.Trigger
      className={cn(
        "inline-flex items-center gap-1.5 rounded-pill px-3.5 py-1.5 text-small font-medium text-ink-muted",
        "transition-colors duration-[var(--motion-fast)]",
        "hover:text-ink focus-visible:outline-none",
        "data-[state=active]:bg-surface data-[state=active]:text-ink data-[state=active]:shadow-xs",
        className,
      )}
      {...props}
    />
  );
}

export function TabsContent({
  className,
  ...props
}: React.ComponentProps<typeof TabsPrimitive.Content>) {
  return (
    <TabsPrimitive.Content
      className={cn("mt-4 focus-visible:outline-none", className)}
      {...props}
    />
  );
}

/* ========================================================================== */
/* Accordion — policies, FAQs                                                 */
/* ========================================================================== */

export const Accordion = AccordionPrimitive.Root;

export function AccordionItem({
  className,
  ...props
}: React.ComponentProps<typeof AccordionPrimitive.Item>) {
  return (
    <AccordionPrimitive.Item
      className={cn("border-b border-line last:border-b-0", className)}
      {...props}
    />
  );
}

export function AccordionTrigger({
  className,
  children,
  ...props
}: React.ComponentProps<typeof AccordionPrimitive.Trigger>) {
  return (
    <AccordionPrimitive.Header className="flex">
      <AccordionPrimitive.Trigger
        className={cn(
          "group flex flex-1 items-center justify-between gap-4 py-4 text-left",
          "font-display text-h3 text-ink transition-colors hover:text-leaf-700",
          "focus-visible:outline-none",
          className,
        )}
        {...props}
      >
        {children}
        <span
          aria-hidden="true"
          className="grid size-7 shrink-0 place-items-center rounded-full border border-line bg-surface text-ink-muted transition-colors group-hover:border-leaf-600/40 group-hover:text-leaf-700"
        >
          <Plus className="size-3.5 group-data-[state=open]:hidden" />
          <Minus className="hidden size-3.5 group-data-[state=open]:block" />
        </span>
      </AccordionPrimitive.Trigger>
    </AccordionPrimitive.Header>
  );
}

export function AccordionContent({
  className,
  children,
  ...props
}: React.ComponentProps<typeof AccordionPrimitive.Content>) {
  return (
    <AccordionPrimitive.Content
      className={cn("overflow-hidden", className)}
      {...props}
    >
      <div className="pb-5 pr-10 text-body text-ink-muted">{children}</div>
    </AccordionPrimitive.Content>
  );
}
