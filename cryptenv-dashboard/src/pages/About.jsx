import { Github, Mail, ExternalLink, Code2, Shield } from 'lucide-react'
import { Link } from 'react-router-dom'

export function About() {
  return (
    <div className="max-w-3xl space-y-8">
      <header>
        <p className="eyebrow">Project</p>
        <h1 className="page-title mt-3">About</h1>
        <p className="page-subtitle">The person behind CryptEnv</p>
      </header>

      <section className="rounded-lg border border-border p-6 sm:p-8">
        <div className="flex flex-col items-start gap-6 sm:flex-row sm:gap-8">
          <img
            src="/developer.jpg"
            alt="Mahesh Shinde"
            className="h-32 w-32 rounded-lg border border-border object-cover sm:h-36 sm:w-36"
          />
          <div className="min-w-0 flex-1 space-y-3">
            <div>
              <h2 className="text-2xl font-light tracking-[-0.02em]">Mahesh Shinde</h2>
              <p className="mt-1 text-xs text-muted-foreground">Creator · Full-stack engineer</p>
            </div>
            <p className="text-sm leading-relaxed text-muted-foreground">
              Building CryptEnv — an encrypted secrets platform with Spring Boot, React, CLI, VS Code, and SDKs —
              so teams can ship without leaking credentials into git or local{' '}
              <code className="rounded bg-surface px-1 py-0.5 font-mono text-xs">.env</code> files.
            </p>
            <div className="flex flex-wrap gap-2 pt-1">
              <a
                href="https://github.com/maheshshinde9100"
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-2 rounded-sm border border-border px-3 py-2 text-sm text-foreground hover:bg-white/[0.04]"
              >
                <Github className="h-4 w-4" />
                GitHub
                <ExternalLink className="h-3 w-3 text-muted-foreground" />
              </a>
              <a
                href="mailto:maheshshinde9100@gmail.com"
                className="inline-flex items-center gap-2 rounded-sm border border-border px-3 py-2 text-sm text-foreground hover:bg-white/[0.04]"
              >
                <Mail className="h-4 w-4" />
                Email
              </a>
              <a
                href="https://github.com/maheshshinde9100/CryptEnv"
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-2 rounded-sm border border-border px-3 py-2 text-sm text-foreground hover:bg-white/[0.04]"
              >
                <Code2 className="h-4 w-4" />
                CryptEnv repo
              </a>
            </div>
          </div>
        </div>
      </section>

      <section className="space-y-3 rounded-lg border border-border p-6">
        <div className="flex items-center gap-2">
          <Shield className="h-4 w-4 text-muted-foreground" />
          <h2 className="text-base">About CryptEnv</h2>
        </div>
        <p className="text-sm leading-relaxed text-muted-foreground">
          CryptEnv encrypts secrets per workspace, delivers them via dashboard / CLI / VS Code / SDKs, and keeps
          plaintext out of your repositories. Open docs for setup guides and API references.
        </p>
        <Link to="/docs/getting-started" className="inline-block text-sm text-foreground hover:underline">
          Open documentation →
        </Link>
      </section>
    </div>
  )
}
