import * as React from "react"
import { cn } from "@/lib/utils"

const Button = React.forwardRef(({ className, variant = "default", size = "default", ...props }, ref) => {
  const variants = {
    default: "bg-primary text-primary-foreground hover:bg-white/90 border border-transparent",
    destructive:
      "border border-destructive/40 bg-transparent text-destructive hover:bg-destructive/10",
    outline:
      "border border-border bg-transparent text-foreground hover:bg-white/[0.06]",
    secondary:
      "border border-transparent bg-white/[0.08] text-foreground hover:bg-white/[0.12]",
    ghost: "border border-transparent bg-transparent text-ink hover:bg-white/[0.06] hover:text-foreground",
    link: "text-tertiary underline-offset-4 hover:underline",
  }

  const sizes = {
    default: "h-11 px-5",
    sm: "h-9 rounded-sm px-3 text-[13px]",
    lg: "h-[53px] px-7 text-[15px]",
    icon: "h-9 w-9",
  }

  return (
    <button
      className={cn(
        "inline-flex select-none items-center justify-center gap-2 rounded-md text-sm font-medium transition-colors disabled:pointer-events-none disabled:opacity-40",
        variants[variant],
        sizes[size],
        className
      )}
      ref={ref}
      {...props}
    />
  )
})
Button.displayName = "Button"

export { Button }
