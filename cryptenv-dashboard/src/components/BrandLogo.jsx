import { cn } from '../lib/utils'

const sizes = {
  sm: 'h-8 w-8 rounded-md',
  md: 'h-10 w-10 rounded-md',
  lg: 'h-14 w-14 rounded-lg',
  xl: 'h-16 w-16 rounded-lg',
}

export function BrandLogo({ size = 'md', className, alt = 'CryptEnv' }) {
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center justify-center border border-border bg-surface',
        sizes[size] || sizes.md,
        className
      )}
    >
      <img src="/logo.svg" alt={alt} className="h-[62%] w-[62%] select-none object-contain" />
    </span>
  )
}

export function BrandWordmark({ size = 'md', textClassName }) {
  return (
    <span className="inline-flex items-center gap-2.5">
      <BrandLogo size={size} />
      <span className={cn('text-lg font-normal tracking-[-0.02em] text-foreground', textClassName)}>
        CryptEnv
      </span>
    </span>
  )
}
