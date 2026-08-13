import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowRight,
  Check,
  Command,
  Github,
  Lock,
  Moon,
  Shield,
  Sun,
  Terminal,
  KeyRound,
  Boxes,
  FileText,
  Zap,
  EyeOff,
  Layers,
} from 'lucide-react'
import { BrandLogo, BrandWordmark } from '../components/BrandLogo'
import { applyTheme, isDarkTheme } from '../lib/theme'

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
    body: 'Projects and environments (DEVELOPMENT, STAGING, PRODUCTION) stay isolated. Access is scoped to owners and members.',
  },
  {
    icon: FileText,
    title: 'Audit trail',
    body: 'Logins, secret access, and admin actions are recorded so you can answer who touched what, and when.',
  },
  {
    icon: KeyRound,
    title: 'JWT + API keys',
    body: 'Short-lived JWTs for the dashboard. ce_live_ API keys for CLI, CI, and SDKs. Rotate keys from Settings anytime.',
  },
  {
    icon: Shield,
    title: 'Production hardening',
    body: 'Per-IP rate limits, env-only credentials, and health checks that stay up without exposing secrets.',
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
    body: 'Add keys in the dashboard, CLI, or VS Code. The server encrypts; you never commit values.',
  },
  {
    n: '03',
    title: 'Run anywhere',
    body: 'Use cryptenv run locally, CRYPTENV_API_KEY in CI, or the Node/Java SDKs in your app.',
  },
]

export function Landing() {
  const [dark, setDark] = useState(true)
  const [authed, setAuthed] = useState(false)

  useEffect(() => {
    const next = isDarkTheme()
    setDark(next)
    applyTheme(next)
    setAuthed(Boolean(localStorage.getItem('token')))
  }, [])

  const toggleTheme = () => {
    const next = !dark
    setDark(next)
    applyTheme(next)
  }

  return (
    <div className="min-h-screen relative overflow-x-hidden">
      <div className="pointer-events-none absolute inset-0 landing-grid opacity-70" />
      <div className="pointer-events-none absolute -top-32 left-1/2 h-[28rem] w-[48rem] -translate-x-1/2 rounded-full bg-cyan-400/15 blur-3xl dark:bg-cyan-400/10" />
      <div className="pointer-events-none absolute top-40 -right-24 h-80 w-80 rounded-full bg-violet-500/15 blur-3xl" />

      <header className="sticky top-0 z-40 border-b border-border/60 bg-background/75 backdrop-blur-xl">
        <div className="mx-auto max-w-6xl px-4 h-16 flex items-center justify-between gap-3">
          <Link to="/" className="flex items-center">
            <BrandWordmark size="sm" textClassName="text-lg" />
          </Link>
          <nav className="hidden md:flex items-center gap-6 text-sm text-muted-foreground">
            <a href="#features" className="hover:text-foreground">Features</a>
            <a href="#how" className="hover:text-foreground">How it works</a>
            <a href="#tools" className="hover:text-foreground">Tools</a>
            <a href="#security" className="hover:text-foreground">Security</a>
          </nav>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={toggleTheme}
              className="h-9 w-9 inline-flex items-center justify-center rounded-lg border border-border/70 hover:bg-muted/60"
              title="Toggle theme"
            >
              {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
            </button>
            {authed ? (
              <Link
                to="/overview"
                className="h-9 inline-flex items-center rounded-lg px-3 text-sm font-medium brand-gradient text-white"
              >
                Open dashboard
              </Link>
            ) : (
              <>
                <Link to="/login" className="hidden sm:inline-flex h-9 items-center px-3 text-sm font-medium hover:text-primary">
                  Sign in
                </Link>
                <Link
                  to="/register"
                  className="h-9 inline-flex items-center rounded-lg px-3 text-sm font-medium brand-gradient text-white"
                >
                  Get started
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      <main>
        <section className="relative mx-auto max-w-6xl px-4 pt-16 pb-10 sm:pt-24 sm:pb-16">
          <div className="max-w-3xl mx-auto text-center space-y-6">
            <p className="inline-flex items-center gap-2 rounded-full border border-border/70 bg-card/60 px-3 py-1 text-xs font-medium text-muted-foreground">
              <span className="h-1.5 w-1.5 rounded-full bg-cyan-400" />
              Encrypted secrets. Zero local .env files.
            </p>
            <h1 className="text-4xl sm:text-6xl font-bold tracking-tight leading-[1.08]">
              Build without{' '}
              <span className="brand-text">secrets leaking</span>
            </h1>
            <p className="text-base sm:text-lg text-muted-foreground max-w-2xl mx-auto leading-relaxed">
              CryptEnv is a secrets platform for teams. Store API keys and database URLs encrypted in the cloud,
              then inject them at runtime through the dashboard, CLI, VS Code, or SDKs.
            </p>
            <div className="flex flex-wrap items-center justify-center gap-3">
              <Link
                to="/register"
                className="inline-flex h-11 items-center gap-2 rounded-xl px-5 text-sm font-semibold brand-gradient text-white shadow-lg shadow-cyan-500/20"
              >
                Create a free account
                <ArrowRight className="h-4 w-4" />
              </Link>
              <a
                href="https://www.npmjs.com/package/cryptenv-cli"
                target="_blank"
                rel="noreferrer"
                className="inline-flex h-11 items-center gap-2 rounded-xl px-5 text-sm font-medium border border-border/80 bg-card/70 hover:bg-muted/50"
              >
                <Terminal className="h-4 w-4" />
                npm i -g cryptenv-cli
              </a>
            </div>
            <div className="flex flex-wrap justify-center gap-2 pt-2 text-xs text-muted-foreground">
              {['AES-256-GCM', 'Workspace isolation', 'Audit trail', 'CI-ready'].map((t) => (
                <span key={t} className="rounded-full border border-border/70 bg-card/50 px-2.5 py-1">
                  {t}
                </span>
              ))}
            </div>
          </div>

          <div className="mt-14 mx-auto max-w-4xl">
            <div className="glass-panel surface-glow rounded-3xl p-2 sm:p-3 overflow-hidden">
              <div className="rounded-2xl border border-border/60 bg-background/80 overflow-hidden">
                <div className="flex items-center gap-2 px-4 py-3 border-b border-border/60 bg-muted/30">
                  <span className="h-2.5 w-2.5 rounded-full bg-red-400/80" />
                  <span className="h-2.5 w-2.5 rounded-full bg-amber-400/80" />
                  <span className="h-2.5 w-2.5 rounded-full bg-emerald-400/80" />
                  <span className="ml-3 text-xs font-mono text-muted-foreground">cryptenv run — npm start</span>
                </div>
                <div className="grid sm:grid-cols-[13rem_1fr]">
                  <aside className="hidden sm:block border-r border-border/60 p-4 space-y-2 bg-card/40">
                    {['Overview', 'Workspaces', 'Secrets', 'Audit Logs'].map((item, i) => (
                      <div
                        key={item}
                        className={`rounded-lg px-3 py-2 text-sm ${
                          i === 2 ? 'brand-gradient text-white' : 'text-muted-foreground'
                        }`}
                      >
                        {item}
                      </div>
                    ))}
                  </aside>
                  <div className="p-4 sm:p-6 space-y-3">
                    {[
                      ['DATABASE_URL', 'PRODUCTION'],
                      ['STRIPE_SECRET_KEY', 'STAGING'],
                      ['JWT_SECRET', 'DEVELOPMENT'],
                    ].map(([key, env]) => (
                      <div
                        key={key}
                        className="flex items-center justify-between gap-3 rounded-xl border border-border/70 px-4 py-3 bg-card/50"
                      >
                        <div className="min-w-0">
                          <p className="font-mono text-sm truncate">{key}</p>
                          <p className="text-[11px] text-muted-foreground">{env}</p>
                        </div>
                        <span className="font-mono text-xs text-muted-foreground tracking-widest">••••••••••••</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section id="features" className="mx-auto max-w-6xl px-4 py-16 sm:py-20">
          <div className="max-w-2xl mb-10">
            <h2 className="text-3xl font-bold tracking-tight">Everything a secrets vault needs</h2>
            <p className="mt-2 text-muted-foreground">
              One backend, four clients — dashboard, CLI, editor, and SDKs — talking to the same encrypted API.
            </p>
          </div>
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {FEATURES.map((f) => {
              const Icon = f.icon
              return (
                <article key={f.title} className="glass-panel rounded-2xl p-5 space-y-3">
                  <div className="h-10 w-10 rounded-xl brand-gradient text-white inline-flex items-center justify-center">
                    <Icon className="h-5 w-5" />
                  </div>
                  <h3 className="font-semibold">{f.title}</h3>
                  <p className="text-sm text-muted-foreground leading-relaxed">{f.body}</p>
                </article>
              )
            })}
          </div>
        </section>

        <section id="how" className="mx-auto max-w-6xl px-4 py-16 sm:py-20">
          <h2 className="text-3xl font-bold tracking-tight mb-10">How it works</h2>
          <div className="grid md:grid-cols-3 gap-4">
            {STEPS.map((s) => (
              <article key={s.n} className="rounded-2xl border border-border/70 bg-card/50 p-6 space-y-3">
                <p className="text-xs font-mono text-primary">{s.n}</p>
                <h3 className="text-lg font-semibold">{s.title}</h3>
                <p className="text-sm text-muted-foreground leading-relaxed">{s.body}</p>
              </article>
            ))}
          </div>
          <div className="mt-8 rounded-2xl border border-border/70 bg-muted/30 p-5 font-mono text-sm overflow-x-auto">
            <p className="text-muted-foreground">$ npm install -g cryptenv-cli</p>
            <p className="text-muted-foreground">$ cryptenv login</p>
            <p>$ cryptenv run -- npm start</p>
            <p className="text-emerald-500 dark:text-emerald-400"># secrets injected into the child process only</p>
          </div>
        </section>

        <section id="tools" className="mx-auto max-w-6xl px-4 py-16 sm:py-20">
          <h2 className="text-3xl font-bold tracking-tight mb-3">Use CryptEnv where you already work</h2>
          <p className="text-muted-foreground mb-8 max-w-2xl">
            Same vault. Same encryption. Pick the client that fits the job.
          </p>
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {[
              {
                title: 'Dashboard',
                href: 'https://cryptenv-dashboard.vercel.app/',
                icon: Boxes,
                body: 'Workspaces, secrets, API keys, audit logs.',
              },
              {
                title: 'CLI 1.3.0',
                href: 'https://www.npmjs.com/package/cryptenv-cli',
                icon: Terminal,
                body: 'workspaces, secrets, and cryptenv run.',
              },
              {
                title: 'VS Code 1.2.0',
                href: 'https://marketplace.visualstudio.com/items?itemName=maheshshinde9100.cryptenv',
                icon: Command,
                body: 'Secrets Explorer and insert at cursor.',
              },
              {
                title: 'SDKs',
                href: 'https://github.com/maheshshinde9100/CryptEnv',
                icon: Zap,
                body: 'Node @cryptenv/sdk and Java cryptenv-sdk.',
              },
            ].map((t) => {
              const Icon = t.icon
              return (
                <a
                  key={t.title}
                  href={t.href}
                  target="_blank"
                  rel="noreferrer"
                  className="glass-panel rounded-2xl p-5 hover:border-primary/40 transition-colors group"
                >
                  <Icon className="h-5 w-5 text-primary mb-3" />
                  <p className="font-semibold group-hover:text-primary">{t.title}</p>
                  <p className="text-sm text-muted-foreground mt-1">{t.body}</p>
                </a>
              )
            })}
          </div>
        </section>

        <section id="security" className="mx-auto max-w-6xl px-4 py-16 sm:py-20">
          <div className="glass-panel rounded-3xl p-6 sm:p-10 grid md:grid-cols-2 gap-8 items-center">
            <div>
              <h2 className="text-3xl font-bold tracking-tight">Designed so a stolen laptop is not a stolen vault</h2>
              <p className="mt-3 text-muted-foreground leading-relaxed">
                Ciphertext lives in PostgreSQL. Workspace keys are wrapped by MASTER_ENCRYPTION_KEY. CLI tokens sit in
                the OS keychain. VS Code uses SecretStorage. Rate limiting protects auth and API routes in production.
              </p>
              <ul className="mt-5 space-y-2 text-sm">
                {[
                  'No plaintext secrets in git or local .env files',
                  'Owner/member isolation on every secret query',
                  '429 + Retry-After when a client floods the API',
                ].map((line) => (
                  <li key={line} className="flex items-start gap-2">
                    <Check className="h-4 w-4 text-primary mt-0.5 shrink-0" />
                    <span>{line}</span>
                  </li>
                ))}
              </ul>
            </div>
            <div className="rounded-2xl border border-border/70 bg-background/70 p-5 font-mono text-xs leading-6 text-muted-foreground">
              <p>Master key (env) ──wraps──► Workspace key</p>
              <p className="pl-4">└── AES-GCM ──encrypts──► Secret ciphertext</p>
              <p className="mt-4">Dashboard / CLI / SDK ──auth──► JWT or ce_live_*</p>
              <p>cryptenv run ──injects──► child process env only</p>
            </div>
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-4 pb-20">
          <div className="relative overflow-hidden rounded-3xl brand-gradient p-8 sm:p-12 text-white text-center">
            <h2 className="text-3xl sm:text-4xl font-bold tracking-tight">Ship the app. Keep the secrets.</h2>
            <p className="mt-3 text-white/85 max-w-xl mx-auto">
              Create a workspace in minutes. Inject secrets on the next run.
            </p>
            <div className="mt-6 flex flex-wrap justify-center gap-3">
              <Link
                to="/register"
                className="inline-flex h-11 items-center rounded-xl bg-white px-5 text-sm font-semibold text-slate-900"
              >
                Get started
              </Link>
              <Link
                to="/login"
                className="inline-flex h-11 items-center rounded-xl border border-white/40 px-5 text-sm font-semibold text-white hover:bg-white/10"
              >
                Sign in
              </Link>
            </div>
          </div>
        </section>
      </main>

      <footer className="border-t border-border/60">
        <div className="mx-auto max-w-6xl px-4 py-10 flex flex-col sm:flex-row gap-6 sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <BrandLogo size="sm" />
            <div>
              <p className="font-semibold">CryptEnv</p>
              <p className="text-xs text-muted-foreground">Encrypted environment secrets · MIT License</p>
            </div>
          </div>
          <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
            <a href="https://github.com/maheshshinde9100/CryptEnv" target="_blank" rel="noreferrer" className="inline-flex items-center gap-1.5 hover:text-foreground">
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
        <div className="mx-auto max-w-6xl px-4 pb-8 text-xs text-muted-foreground">
          Built by{' '}
          <a href="https://github.com/maheshshinde9100" className="text-foreground hover:underline" target="_blank" rel="noreferrer">
            Mahesh Shinde
          </a>
        </div>
      </footer>
    </div>
  )
}
