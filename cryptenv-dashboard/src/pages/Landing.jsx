import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowRight,
  Check,
  Command,
  Github,
  Lock,
  Shield,
  Terminal,
  KeyRound,
  Boxes,
  FileText,
  Zap,
  EyeOff,
  Layers,
} from 'lucide-react'
import { BrandLogo, BrandWordmark } from '../components/BrandLogo'

const FEATURES = [
  {
    icon: Lock,
    title: 'AES-256-GCM at rest',
    body: 'Every secret is encrypted with a workspace key. The platform master key only wraps those keys — plaintext never sits in the database.',
  },
  {
    icon: EyeOff,
    title: 'Runtime injection',
    body: 'cryptenv run injects secrets into the child process only. Nothing is written to a local .env file or committed to git.',
  },
  {
    icon: Layers,
    title: 'Workspace isolation',
    body: 'Projects and environments stay isolated. Access is scoped to owners and members with explicit roles.',
  },
  {
    icon: FileText,
    title: 'Hash-chained audit trail',
    body: 'Logins, secret access and admin actions are recorded so you can answer who touched what, and when.',
  },
  {
    icon: KeyRound,
    title: 'JWT, API keys and MFA',
    body: 'Short-lived JWTs for the dashboard, API keys for CLI and SDKs, and TOTP step-up for sensitive actions.',
  },
  {
    icon: Shield,
    title: 'Approvals and JIT access',
    body: 'Destructive actions need four-eyes approval. Protected environments open only through time-bound grants.',
  },
]

const STEPS = [
  {
    n: '01',
    title: 'Create a workspace',
    body: 'Generate a workspace encryption key. Secrets cannot be stored until that key exists.',
  },
  {
    n: '02',
    title: 'Store secrets once',
    body: 'Add keys in the dashboard, CLI or editor. The server encrypts; you never commit values.',
  },
  {
    n: '03',
    title: 'Run anywhere',
    body: 'Use cryptenv run locally, CRYPTENV_API_KEY in CI, or the SDKs in your app.',
  },
]

function useLandingMotion() {
  useEffect(() => {
    const root = document.documentElement
    root.classList.add('scroll-smooth')

    const nodes = Array.from(document.querySelectorAll('.reveal'))
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches

    if (reduced || !('IntersectionObserver' in window)) {
      nodes.forEach((node) => node.setAttribute('data-visible', 'true'))
      return () => root.classList.remove('scroll-smooth')
    }

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.setAttribute('data-visible', 'true')
            observer.unobserve(entry.target)
          }
        }
      },
      { rootMargin: '0px 0px -8% 0px', threshold: 0.12 }
    )

    nodes.forEach((node) => {
      if (node.getBoundingClientRect().top < window.innerHeight * 0.92) {
        node.setAttribute('data-visible', 'true')
      } else {
        observer.observe(node)
      }
    })

    return () => {
      observer.disconnect()
      root.classList.remove('scroll-smooth')
    }
  }, [])
}

const TOOLS = [
  {
    title: 'Dashboard',
    href: 'https://cryptenv-dashboard.vercel.app/',
    icon: Boxes,
    body: 'Workspaces, secrets, approvals, audit log.',
  },
  {
    title: 'CLI',
    href: 'https://www.npmjs.com/package/cryptenv-cli',
    icon: Terminal,
    body: 'Workspaces, secrets and cryptenv run.',
  },
  {
    title: 'VS Code',
    href: 'https://marketplace.visualstudio.com/items?itemName=maheshshinde9100.cryptenv',
    icon: Command,
    body: 'Secrets explorer and insert at cursor.',
  },
  {
    title: 'SDKs',
    href: 'https://github.com/maheshshinde9100/CryptEnv',
    icon: Zap,
    body: 'Node @cryptenv/sdk and the Java SDK.',
  },
]

export function Landing() {
  const [authed, setAuthed] = useState(false)

  useEffect(() => {
    document.documentElement.classList.add('dark')
    setAuthed(Boolean(localStorage.getItem('token')))
  }, [])

  useLandingMotion()

  return (
    <div className="min-h-screen bg-background">
      <header className="sticky top-0 z-40 border-b border-border bg-background">
        <div className="mx-auto flex h-16 max-w-content items-center justify-between gap-3 px-4 sm:px-6">
          <Link to="/" className="flex items-center">
            <BrandWordmark size="sm" />
          </Link>
          <nav className="hidden items-center gap-6 text-sm text-muted-foreground md:flex">
            <a href="#features" className="relative transition-colors after:absolute after:-bottom-1 after:left-0 after:h-px after:w-full after:origin-left after:scale-x-0 after:bg-tertiary after:transition-transform after:duration-300 hover:text-foreground hover:after:scale-x-100">
              Features
            </a>
            <a href="#how" className="relative transition-colors after:absolute after:-bottom-1 after:left-0 after:h-px after:w-full after:origin-left after:scale-x-0 after:bg-tertiary after:transition-transform after:duration-300 hover:text-foreground hover:after:scale-x-100">
              How it works
            </a>
            <a href="#tools" className="relative transition-colors after:absolute after:-bottom-1 after:left-0 after:h-px after:w-full after:origin-left after:scale-x-0 after:bg-tertiary after:transition-transform after:duration-300 hover:text-foreground hover:after:scale-x-100">
              Tools
            </a>
            <a href="#security" className="relative transition-colors after:absolute after:-bottom-1 after:left-0 after:h-px after:w-full after:origin-left after:scale-x-0 after:bg-tertiary after:transition-transform after:duration-300 hover:text-foreground hover:after:scale-x-100">
              Security
            </a>
          </nav>
          <div className="flex items-center gap-2">
            {authed ? (
              <Link to="/overview" className="inline-flex h-9 items-center rounded-md bg-primary px-3 text-sm font-medium text-primary-foreground">
                Open dashboard
              </Link>
            ) : (
              <>
                <Link to="/login" className="hidden h-9 items-center px-3 text-sm text-ink hover:text-foreground sm:inline-flex">
                  Sign in
                </Link>
                <Link
                  to="/register"
                  className="inline-flex h-9 items-center rounded-md bg-primary px-3 text-sm font-medium text-primary-foreground"
                >
                  Get started
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      <main>
        <section className="mx-auto max-w-content px-4 pb-16 pt-20 sm:px-6 sm:pt-28">
          <div className="mx-auto max-w-3xl space-y-7 text-center">
            <p
              className="hero-rise inline-flex items-center gap-2 rounded-full border border-border px-3 py-1 text-xs text-muted-foreground"
              style={{ '--rise-delay': '0ms' }}
            >
              <span className="h-1.5 w-1.5 rounded-full bg-tertiary" />
              Encrypted secrets. Zero local .env files.
            </p>
            <h1
              className="hero-rise text-4xl font-light leading-[1.1] tracking-[-0.03em] sm:text-6xl"
              style={{ '--rise-delay': '70ms' }}
            >
              Ship software without leaking secrets
            </h1>
            <p
              className="hero-rise mx-auto max-w-2xl text-base leading-relaxed text-muted-foreground sm:text-lg"
              style={{ '--rise-delay': '150ms' }}
            >
              CryptEnv is the encrypted vault for team credentials. API keys, database URLs and tokens stay
              encrypted in the cloud and are injected only where they are needed — from the dashboard, CLI,
              editor or SDKs. No .env files, nothing in git.
            </p>
            <div className="hero-rise flex flex-wrap items-center justify-center gap-3" style={{ '--rise-delay': '230ms' }}>
              <Link
                to="/register"
                className="group inline-flex h-[53px] items-center gap-2 rounded-md bg-primary px-6 text-sm font-medium text-primary-foreground transition-colors hover:bg-white/90"
              >
                Create a free account
                <ArrowRight className="arrow-slide h-4 w-4" />
              </Link>
              <a
                href="https://www.npmjs.com/package/cryptenv-cli"
                target="_blank"
                rel="noreferrer"
                className="inline-flex h-[53px] items-center gap-2 rounded-sm border border-border px-6 text-sm text-foreground transition-colors hover:bg-white/[0.04]"
              >
                <Terminal className="h-4 w-4" />
                npm i -g cryptenv-cli
              </a>
            </div>
            <div
              className="hero-rise flex flex-wrap justify-center gap-2 pt-1 text-xs text-muted-foreground"
              style={{ '--rise-delay': '310ms' }}
            >
              {['AES-256-GCM', 'Workspace isolation', 'Four-eyes approvals', 'CI-ready'].map((tag) => (
                <span key={tag} className="chip">
                  {tag}
                </span>
              ))}
            </div>
          </div>

          <div
            className="hero-rise mx-auto mt-16 max-w-4xl overflow-hidden rounded-lg border border-border"
            style={{ '--rise-delay': '400ms' }}
          >
            <div className="flex items-center gap-2 border-b border-border px-4 py-3">
              <span className="h-2 w-2 rounded-full bg-white/20" />
              <span className="h-2 w-2 rounded-full bg-white/20" />
              <span className="h-2 w-2 rounded-full bg-white/20" />
              <span className="ml-3 font-mono text-xs text-muted-foreground">cryptenv run — npm start</span>
            </div>
            <div className="grid sm:grid-cols-[13rem_1fr]">
              <aside className="hidden space-y-1 border-r border-border p-4 sm:block">
                {['Overview', 'Workspaces', 'Secrets', 'Audit log'].map((item, i) => (
                  <div
                    key={item}
                    className={`rounded-sm px-3 py-2 text-sm ${
                      i === 2 ? 'bg-white/[0.07] text-foreground' : 'text-muted-foreground'
                    }`}
                  >
                    {item}
                  </div>
                ))}
              </aside>
              <div className="space-y-2 p-4 sm:p-6">
                {[
                  ['DATABASE_URL', 'Production'],
                  ['STRIPE_SECRET_KEY', 'Staging'],
                  ['JWT_SECRET', 'Development'],
                ].map(([key, env]) => (
                  <div key={key} className="flex items-center justify-between gap-3 rounded-md border border-border px-4 py-3">
                    <div className="min-w-0">
                      <p className="truncate font-mono text-sm">{key}</p>
                      <p className="text-[11px] text-muted-foreground">{env}</p>
                    </div>
                    <span className="font-mono text-xs tracking-widest text-muted-foreground">••••••••••••</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </section>

        <section id="features" className="mx-auto max-w-content px-4 py-16 sm:px-6 sm:py-20">
          <div className="reveal mb-10 max-w-2xl">
            <h2 className="text-3xl font-light tracking-[-0.02em]">Everything a secrets vault needs</h2>
            <p className="mt-3 text-muted-foreground">
              One backend, four clients — dashboard, CLI, editor and SDKs — talking to the same encrypted API.
            </p>
          </div>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map((feature, index) => {
              const Icon = feature.icon
              return (
                <article
                  key={feature.title}
                  className="reveal lift group space-y-3 rounded-lg border border-border p-5 hover:border-white/[0.16]"
                  style={{ '--reveal-delay': `${index * 55}ms` }}
                >
                  <span className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-border bg-surface text-ink transition-colors group-hover:text-tertiary">
                    <Icon className="h-4 w-4" />
                  </span>
                  <h3 className="text-sm font-medium">{feature.title}</h3>
                  <p className="text-sm leading-relaxed text-muted-foreground">{feature.body}</p>
                </article>
              )
            })}
          </div>
        </section>

        <section id="how" className="mx-auto max-w-content px-4 py-16 sm:px-6 sm:py-20">
          <h2 className="reveal mb-10 text-3xl font-light tracking-[-0.02em]">How it works</h2>
          <div className="grid gap-4 md:grid-cols-3">
            {STEPS.map((step, index) => (
              <article
                key={step.n}
                className="reveal lift space-y-3 rounded-lg border border-border p-6 hover:border-white/[0.16]"
                style={{ '--reveal-delay': `${index * 70}ms` }}
              >
                <p className="font-mono text-xs text-tertiary">{step.n}</p>
                <h3 className="text-base">{step.title}</h3>
                <p className="text-sm leading-relaxed text-muted-foreground">{step.body}</p>
              </article>
            ))}
          </div>
          <div className="reveal mt-6 overflow-x-auto rounded-lg border border-border bg-surface p-5 font-mono text-sm" style={{ '--reveal-delay': '220ms' }}>
            <p className="text-muted-foreground">$ npm install -g cryptenv-cli</p>
            <p className="text-muted-foreground">$ cryptenv login</p>
            <p>$ cryptenv run -- npm start</p>
            <p className="text-tertiary"># secrets injected into the child process only</p>
          </div>
        </section>

        <section id="tools" className="mx-auto max-w-content px-4 py-16 sm:px-6 sm:py-20">
          <h2 className="reveal mb-3 text-3xl font-light tracking-[-0.02em]">Use CryptEnv where you already work</h2>
          <p className="reveal mb-8 max-w-2xl text-muted-foreground" style={{ '--reveal-delay': '60ms' }}>
            Same vault. Same encryption. Pick the client that fits the job.
          </p>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {TOOLS.map((tool, index) => {
              const Icon = tool.icon
              return (
                <a
                  key={tool.title}
                  href={tool.href}
                  target="_blank"
                  rel="noreferrer"
                  className="reveal lift group rounded-lg border border-border p-5 hover:border-white/[0.16] hover:bg-white/[0.02]"
                  style={{ '--reveal-delay': `${index * 55}ms` }}
                >
                  <Icon className="mb-3 h-5 w-5 text-muted-foreground transition-colors group-hover:text-tertiary" />
                  <p className="text-sm">{tool.title}</p>
                  <p className="mt-1 text-sm text-muted-foreground">{tool.body}</p>
                </a>
              )
            })}
          </div>
        </section>

        <section id="security" className="mx-auto max-w-content px-4 py-16 sm:px-6 sm:py-20">
          <div className="reveal grid items-center gap-8 rounded-lg border border-border p-6 sm:p-10 md:grid-cols-2">
            <div>
              <h2 className="text-3xl font-light tracking-[-0.02em]">
                Designed so a stolen laptop is not a stolen vault
              </h2>
              <p className="mt-4 leading-relaxed text-muted-foreground">
                Ciphertext lives in PostgreSQL. Workspace keys are wrapped by MASTER_ENCRYPTION_KEY. CLI tokens sit
                in the OS keychain. VS Code uses SecretStorage. Rate limiting protects auth and API routes.
              </p>
              <ul className="mt-5 space-y-2 text-sm">
                {[
                  'No plaintext secrets in git or local .env files',
                  'Owner and member isolation on every secret query',
                  'Reveals are audited and expire after 30 seconds',
                ].map((line) => (
                  <li key={line} className="flex items-start gap-2">
                    <Check className="mt-0.5 h-4 w-4 shrink-0 text-tertiary" />
                    <span>{line}</span>
                  </li>
                ))}
              </ul>
            </div>
            <div className="rounded-lg border border-border bg-surface p-5 font-mono text-xs leading-6 text-muted-foreground">
              <p>Master key (env) ──wraps──► Workspace key</p>
              <p className="pl-4">└── AES-GCM ──encrypts──► Secret ciphertext</p>
              <p className="mt-4">Dashboard / CLI / SDK ──auth──► JWT or ce_live_*</p>
              <p>cryptenv run ──injects──► child process env only</p>
            </div>
          </div>
        </section>

        <section className="mx-auto max-w-content px-4 pb-20 sm:px-6">
          <div className="reveal rounded-lg border border-border p-8 text-center sm:p-12">
            <h2 className="text-3xl font-light tracking-[-0.02em] sm:text-4xl">Ship the app. Keep the secrets.</h2>
            <p className="mx-auto mt-3 max-w-xl text-muted-foreground">
              Create a workspace in minutes. Inject secrets on the next run.
            </p>
            <div className="mt-6 flex flex-wrap justify-center gap-3">
              <Link
                to="/register"
                className="inline-flex h-[53px] items-center rounded-md bg-primary px-6 text-sm font-medium text-primary-foreground"
              >
                Get started
              </Link>
              <Link
                to="/login"
                className="inline-flex h-[53px] items-center rounded-sm border border-border px-6 text-sm text-foreground hover:bg-white/[0.04]"
              >
                Sign in
              </Link>
            </div>
          </div>
        </section>
      </main>

      <footer className="border-t border-border">
        <div className="mx-auto flex max-w-content flex-col gap-6 px-4 py-10 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div className="flex items-center gap-3">
            <BrandLogo size="sm" />
            <div>
              <p className="text-sm">CryptEnv</p>
              <p className="text-xs text-muted-foreground">Encrypted environment secrets · MIT License</p>
            </div>
          </div>
          <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
            <a
              href="https://github.com/maheshshinde9100/CryptEnv"
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1.5 hover:text-foreground"
            >
              <Github className="h-4 w-4" />
              GitHub
            </a>
            <a href="https://www.npmjs.com/package/cryptenv-cli" target="_blank" rel="noreferrer" className="hover:text-foreground">
              npm CLI
            </a>
            <a
              href="https://marketplace.visualstudio.com/items?itemName=maheshshinde9100.cryptenv"
              target="_blank"
              rel="noreferrer"
              className="hover:text-foreground"
            >
              VS Code
            </a>
            <Link to="/login" className="hover:text-foreground">
              Sign in
            </Link>
          </div>
        </div>
        <div className="mx-auto max-w-content px-4 pb-8 text-xs text-muted-foreground sm:px-6">
          Built by{' '}
          <a
            href="https://github.com/maheshshinde9100"
            className="text-foreground hover:underline"
            target="_blank"
            rel="noreferrer"
          >
            Mahesh Shinde
          </a>
        </div>
      </footer>
    </div>
  )
}
