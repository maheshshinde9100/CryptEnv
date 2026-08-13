import { cn } from '../lib/utils'

const sizes = {
  sm: 'h-8 w-8 rounded-lg',
  md: 'h-10 w-10 rounded-xl',
  lg: 'h-14 w-14 rounded-2xl',
  xl: 'h-16 w-16 rounded-2xl',
}

/** White mark sits on the brand gradient so it stays visible in light and dark themes. */
export function BrandLogo({ size = 'md', className, alt = 'CryptEnv' }) {
  return (
    <span
      className={cn(
        'inline-flex items-center justify-center brand-gradient shadow-md shadow-cyan-500/20 shrink-0',
        sizes[size] || sizes.md,
        className
      )}
    >
      <img src="/logo.svg" alt={alt} className="h-[68%] w-[68%] object-contain select-none" />
    </span>
  )
}

export function BrandWordmark({ size = 'md', textClassName }) {
  return (
    <span className="inline-flex items-center gap-2.5">
      <BrandLogo size={size} />
      <span className={cn('font-bold tracking-tight brand-text', textClassName)}>CryptEnv</span>
    </span>
  )
}
