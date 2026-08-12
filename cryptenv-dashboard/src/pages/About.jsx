import { Github, Mail, ExternalLink, Code2, Shield } from 'lucide-react'
import { Link } from 'react-router-dom'

export function About() {
  return (
    <div className="max-w-3xl space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">About</h1>
        <p className="text-muted-foreground mt-1">The person behind CryptEnv</p>
      </div>

      <section className="glass-panel rounded-3xl p-6 sm:p-8 surface-glow">
        <div className="flex flex-col sm:flex-row gap-6 sm:gap-8 items-start">
          <img
            src="/developer.jpg"
            alt="Mahesh Shinde"
            className="h-36 w-36 sm:h-40 sm:w-40 rounded-2xl object-cover border border-border/70 shadow-lg"
          />
          <div className="space-y-3 flex-1 min-w-0">
            <div>
              <h2 className="text-2xl font-bold">Mahesh Shinde</h2>
              <p className="text-sm text-primary font-medium mt-0.5">Creator · Full-stack engineer</p>
            </div>
            <p className="text-sm text-muted-foreground leading-relaxed">
              Building CryptEnv — an encrypted secrets platform with Spring Boot, React, CLI, VS Code, and SDKs —
              so teams can ship without leaking credentials into git or local <code className="text-xs bg-muted px-1 rounded">.env</code> files.
            </p>
            <div className="flex flex-wrap gap-2 pt-1">
              <a
                href="https://github.com/maheshshinde9100"
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-2 rounded-lg border border-border/70 px-3 py-2 text-sm hover:bg-muted/50"
              >
                <Github className="h-4 w-4" />
                GitHub
                <ExternalLink className="h-3 w-3 text-muted-foreground" />
              </a>
              <a
                href="mailto:maheshshinde9100@gmail.com"
                className="inline-flex items-center gap-2 rounded-lg border border-border/70 px-3 py-2 text-sm hover:bg-muted/50"
              >
                <Mail className="h-4 w-4" />
                Email
              </a>
              <a
                href="https://github.com/maheshshinde9100/CryptEnv"
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-2 rounded-lg border border-border/70 px-3 py-2 text-sm hover:bg-muted/50"
              >
                <Code2 className="h-4 w-4" />
                CryptEnv repo
              </a>
            </div>
          </div>
        </div>
      </section>

      <section className="glass-panel rounded-2xl p-6 space-y-3">
        <div className="flex items-center gap-2">
          <Shield className="h-5 w-5 text-primary" />
          <h3 className="font-semibold">About CryptEnv</h3>
        </div>
        <p className="text-sm text-muted-foreground leading-relaxed">
          CryptEnv encrypts secrets per workspace, delivers them via dashboard / CLI / VS Code / SDKs, and keeps
          plaintext out of your repositories. Open docs for setup guides and API references.
        </p>
        <Link to="/docs/getting-started" className="text-sm text-primary font-medium hover:underline">
          Open documentation →
        </Link>
      </section>
    </div>
  )
}
