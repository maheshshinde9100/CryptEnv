import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { Copy } from 'lucide-react'

function Code({ children }) {
  const text = String(children).trim()
  return (
    <div className="relative group rounded-xl border border-border/70 bg-muted/40 overflow-hidden">
      <button
        type="button"
        className="absolute right-2 top-2 opacity-0 group-hover:opacity-100 transition-opacity p-1.5 rounded-md bg-background/80 border text-muted-foreground hover:text-foreground"
        onClick={() => {
          navigator.clipboard.writeText(text)
          toast.success('Copied')
        }}
      >
        <Copy className="h-3.5 w-3.5" />
      </button>
      <pre className="p-4 text-xs sm:text-sm font-mono overflow-x-auto whitespace-pre-wrap">{text}</pre>
    </div>
  )
}

function Note({ children }) {
  return (
    <div className="rounded-xl border border-cyan-500/30 bg-cyan-500/10 px-4 py-3 text-sm text-foreground/90">
      {children}
    </div>
  )
}

function Tip({ children }) {
  return (
    <div className="rounded-xl border border-violet-500/30 bg-violet-500/10 px-4 py-3 text-sm text-foreground/90">
      <span className="font-semibold text-violet-300">Tip — </span>
      {children}
    </div>
  )
}

function Step({ n, title, children }) {
  return (
    <section className="space-y-3">
      <div className="flex items-center gap-3">
        <span className="h-8 w-8 rounded-full brand-gradient text-white text-sm font-bold flex items-center justify-center">
          {n}
        </span>
        <h3 className="text-lg font-semibold">{title}</h3>
      </div>
      <div className="pl-11 space-y-3">{children}</div>
    </section>
  )
}

function Toc({ items }) {
  return (
    <aside className="hidden xl:block w-48 shrink-0 sticky top-24 self-start">
      <p className="text-[11px] uppercase tracking-wider text-muted-foreground mb-3">On this page</p>
      <ul className="space-y-2 text-sm">
        {items.map((item) => (
          <li key={item}>
            <a href={`#${item.toLowerCase().replace(/\s+/g, '-')}`} className="text-muted-foreground hover:text-primary">
              {item}
            </a>
          </li>
        ))}
      </ul>
    </aside>
  )
}

export function GettingStartedDoc() {
  return (
    <div className="flex gap-10">
      <article className="flex-1 min-w-0 space-y-8">
        <header className="space-y-3">
          <h1 className="text-3xl font-bold tracking-tight" id="introduction">
            Getting Started
          </h1>
          <p className="text-lg text-muted-foreground">Build without secrets leaking</p>
          <p className="text-muted-foreground leading-relaxed">
            CryptEnv is a secrets management platform. It replaces insecure local <code className="text-xs bg-muted px-1 rounded">.env</code> files
            with encrypted vault storage and runtime injection across the dashboard, CLI, VS Code extension, and SDKs.
          </p>
        </header>

        <Note>
          <p className="font-semibold mb-1">New to secrets management?</p>
          CryptEnv keeps API keys and DB URLs encrypted in the cloud. They are injected into your app only when you run it.
          If your laptop is stolen or source leaks, plaintext secrets are not sitting in the repo.
          <div className="mt-3 flex flex-wrap gap-2 text-xs">
            <span className="rounded-full bg-background/60 px-2.5 py-1 border">AES-GCM encrypted</span>
            <span className="rounded-full bg-background/60 px-2.5 py-1 border">Workspace isolation</span>
            <span className="rounded-full bg-background/60 px-2.5 py-1 border">Audit trail</span>
          </div>
        </Note>

        <Step n={0} title="Get the VS Code Extension">
          <p className="text-sm text-muted-foreground">
            Install the official extension for the Secrets Explorer, cursor insert, and API-key auth.
          </p>
          <Code>{`# VS Code → Extensions → search "CryptEnv"
# or Install from VSIX: cryptenv-1.2.0.vsix`}</Code>
        </Step>

        <Step n={1} title="Install the CLI">
          <p className="text-sm text-muted-foreground">Primary interface for local and CI secret injection.</p>
          <Code>{`npm install -g cryptenv-cli
cryptenv --version
# cryptenv-cli 1.3.0`}</Code>
        </Step>

        <Step n={2} title="Authenticate">
          <p className="text-sm text-muted-foreground">Connect your machine with login (local) or an API key (CI).</p>
          <Code>{`cryptenv register
cryptenv login`}</Code>
          <Tip>
            Use dashboard <strong>Settings → API Keys</strong> (`ce_live_…`) for GitHub Actions and Docker. Prefer login on laptops.
          </Tip>
        </Step>

        <Step n={3} title="Initialize your project">
          <p className="text-sm text-muted-foreground">
            Creates <code className="text-xs bg-muted px-1 rounded">.cryptenv.json</code> (API URL + workspace name only — safe to commit).
          </p>
          <Code>{`cd my-app
cryptenv init`}</Code>
          <Tip>
            After init, create a workspace with an encryption key in the dashboard, then run{' '}
            <code className="text-xs bg-muted px-1 rounded">cryptenv secrets ls</code>.
          </Tip>
        </Step>

        <div className="rounded-xl border border-border/70 bg-card/50 p-4 text-sm text-muted-foreground" id="reference">
          <p className="font-semibold text-foreground mb-1">Reference</p>
          Secrets live only in the child process environment when you use{' '}
          <code className="text-xs bg-muted px-1 rounded">cryptenv run</code> — never written to disk by the CLI.
        </div>

        <div className="flex justify-between pt-4 border-t border-border/60">
          <span />
          <Link to="/docs/workspaces" className="text-primary font-medium hover:underline">
            Next: Workspace Management →
          </Link>
        </div>
      </article>
      <Toc items={['Introduction', 'Install', 'Authenticate', 'Initialize', 'Reference']} />
    </div>
  )
}

export function WorkspacesDoc() {
  return (
    <article className="space-y-6 max-w-3xl">
      <h1 className="text-3xl font-bold">Workspace Management</h1>
      <p className="text-muted-foreground">
        A workspace isolates a project. Each workspace has an encryption key that wraps secret ciphertext. Environments
        split DEVELOPMENT / STAGING / PRODUCTION.
      </p>
      <h2 className="text-xl font-semibold">Create in the dashboard</h2>
      <ol className="list-decimal list-inside text-sm text-muted-foreground space-y-2">
        <li>Open <strong className="text-foreground">Workspaces</strong> → New workspace</li>
        <li>Generate or paste a workspace encryption key (min 16 characters)</li>
        <li>Store that key offline — also set <code className="text-xs bg-muted px-1 rounded">CRYPTENV_WORKSPACE_ENCRYPTION_KEY</code> for the Node SDK</li>
        <li>Add an environment (DEVELOPMENT recommended first)</li>
      </ol>
      <h2 className="text-xl font-semibold">CLI</h2>
      <Code>{`cryptenv workspaces ls
cryptenv workspaces create my-api -k "$(openssl rand -base64 32)" -d "API vault"`}</Code>
      <Note>
        After rotating the platform <strong>MASTER_ENCRYPTION_KEY</strong>, re-set each workspace encryption key, then
        re-save secrets if decrypt fails.
      </Note>
      <div className="flex justify-between pt-4 border-t">
        <Link to="/docs/getting-started" className="text-muted-foreground hover:text-primary">
          ← Getting Started
        </Link>
        <Link to="/docs/workflow" className="text-primary font-medium hover:underline">
          Next: Development Workflow →
        </Link>
      </div>
    </article>
  )
}

export function WorkflowDoc() {
  return (
    <article className="space-y-6 max-w-3xl">
      <h1 className="text-3xl font-bold">Development Workflow</h1>
      <p className="text-muted-foreground">Typical day-to-day loop with CryptEnv.</p>
      <Code>{`# Add / update secrets (dashboard or CLI)
cryptenv secrets set DATABASE_URL "postgresql://localhost/app"
cryptenv secrets set STRIPE_KEY "sk_test_..."

# List (masked) / fetch one value
cryptenv secrets ls
cryptenv secrets get DATABASE_URL

# Run any command with secrets injected
cryptenv run -- npm run dev
cryptenv run -- python manage.py runserver
cryptenv run -- mvn spring-boot:run`}</Code>
      <Tip>
        Prefer revealing values in the dashboard with the eye icon only when needed. Avoid pasting secrets into chat or tickets.
      </Tip>
      <div className="flex justify-between pt-4 border-t">
        <Link to="/docs/workspaces" className="text-muted-foreground hover:text-primary">
          ← Workspaces
        </Link>
        <Link to="/docs/cicd" className="text-primary font-medium hover:underline">
          Next: CI/CD →
        </Link>
      </div>
    </article>
  )
}

export function CicdDoc() {
  return (
    <article className="space-y-6 max-w-3xl">
      <h1 className="text-3xl font-bold">CI/CD Integration</h1>
      <p className="text-muted-foreground">Use API keys — never commit passwords or JWT tokens.</p>
      <h2 className="text-xl font-semibold">GitHub Actions</h2>
      <Code>{`# .github/workflows/ci.yml
- name: Install CryptEnv CLI
  run: npm install -g cryptenv-cli
- name: Run tests with secrets
  env:
    CRYPTENV_API_KEY: \${{ secrets.CRYPTENV_API_KEY }}
    CRYPTENV_API_URL: https://cryptenv-backend.onrender.com/api
  run: cryptenv run -- npm test`}</Code>
      <h2 className="text-xl font-semibold">Docker</h2>
      <Code>{`ENV CRYPTENV_API_KEY=ce_live_xxxxx
RUN npm install -g cryptenv-cli
CMD ["cryptenv", "run", "--", "node", "server.js"]`}</Code>
      <Tip>Store <code className="text-xs bg-muted px-1 rounded">CRYPTENV_API_KEY</code> in your CI secret store, not in the Dockerfile.</Tip>
      <div className="flex justify-between pt-4 border-t">
        <Link to="/docs/workflow" className="text-muted-foreground hover:text-primary">
          ← Workflow
        </Link>
        <Link to="/docs/cli" className="text-primary font-medium hover:underline">
          Next: CLI Reference →
        </Link>
      </div>
    </article>
  )
}

export function CliDoc() {
  return (
    <article className="space-y-6 max-w-3xl">
      <h1 className="text-3xl font-bold">CLI Reference</h1>
      <p className="text-muted-foreground">cryptenv-cli <strong className="text-foreground">1.3.0</strong></p>
      <Code>{`npm install -g cryptenv-cli

cryptenv register | login | logout | profile
cryptenv init
cryptenv workspaces ls
cryptenv workspaces create <name> [-k <key>] [-d <desc>]
cryptenv secrets ls | get <KEY> | set <KEY> <VALUE> | delete <KEY>
cryptenv run -- <command> [args...]`}</Code>
      <h2 className="text-xl font-semibold">Environment variables</h2>
      <ul className="text-sm text-muted-foreground space-y-1 list-disc list-inside">
        <li><code className="text-xs bg-muted px-1 rounded">CRYPTENV_API_URL</code> — backend base (…/api)</li>
        <li><code className="text-xs bg-muted px-1 rounded">CRYPTENV_API_KEY</code> — CI auth</li>
      </ul>
      <div className="flex justify-between pt-4 border-t">
        <Link to="/docs/cicd" className="text-muted-foreground hover:text-primary">
          ← CI/CD
        </Link>
        <Link to="/docs/vscode" className="text-primary font-medium hover:underline">
          Next: VS Code →
        </Link>
      </div>
    </article>
  )
}

export function VscodeDoc() {
  return (
    <article className="space-y-6 max-w-3xl">
      <h1 className="text-3xl font-bold">VS Code Extension</h1>
      <p className="text-muted-foreground">CryptEnv extension <strong className="text-foreground">1.2.0</strong></p>
      <ul className="text-sm text-muted-foreground space-y-2 list-disc list-inside">
        <li>Sign In / API Key / Create Account</li>
        <li>Secrets Explorer: workspaces → environments → secrets</li>
        <li>Create workspace with encryption key</li>
        <li>Preview, copy, edit, delete; insert key/value at cursor</li>
        <li>Set Backend URL for self-hosted APIs</li>
      </ul>
      <Code>{`# Command Palette
CryptEnv: Sign In
CryptEnv: Create Workspace
CryptEnv: Add Secret
CryptEnv: Insert Secret Value at Cursor`}</Code>
      <div className="flex justify-between pt-4 border-t">
        <Link to="/docs/cli" className="text-muted-foreground hover:text-primary">
          ← CLI
        </Link>
        <Link to="/docs/security" className="text-primary font-medium hover:underline">
          Next: Security →
        </Link>
      </div>
    </article>
  )
}

export function SecurityDoc() {
  return (
    <article className="space-y-6 max-w-3xl">
      <h1 className="text-3xl font-bold">Security Features</h1>
      <ul className="space-y-3 text-sm text-muted-foreground">
        <li><strong className="text-foreground">AES-256-GCM</strong> — secrets encrypted with the workspace key</li>
        <li><strong className="text-foreground">Master key</strong> — wraps workspace keys at rest (env: MASTER_ENCRYPTION_KEY)</li>
        <li><strong className="text-foreground">JWT + API keys</strong> — dashboard sessions and machine access</li>
        <li><strong className="text-foreground">Workspace isolation</strong> — owner/member scoped queries</li>
        <li><strong className="text-foreground">Rate limiting</strong> — per-IP buckets on API & auth routes (429 + Retry-After)</li>
        <li><strong className="text-foreground">Audit logs</strong> — track access and admin actions</li>
      </ul>
      <Note>Never commit <code className="text-xs bg-muted px-1 rounded">application.properties</code>, <code className="text-xs bg-muted px-1 rounded">.env*</code>, or live API keys.</Note>
      <div className="flex justify-between pt-4 border-t">
        <Link to="/docs/vscode" className="text-muted-foreground hover:text-primary">
          ← VS Code
        </Link>
        <Link to="/docs/integrations" className="text-primary font-medium hover:underline">
          Next: Integrations →
        </Link>
      </div>
    </article>
  )
}

export function IntegrationsDoc() {
  return (
    <article className="space-y-6 max-w-3xl">
      <h1 className="text-3xl font-bold">Integrations</h1>
      <div className="grid gap-3 sm:grid-cols-2">
        {[
          ['Dashboard', 'Web vault UI — workspaces, secrets, API keys, audit'],
          ['CLI', 'cryptenv-cli — run-time injection'],
          ['VS Code', 'Explorer + editor insert'],
          ['Node SDK', '@cryptenv/sdk — client-side decrypt option'],
          ['Java SDK', 'com.maheshshinde:cryptenv-sdk'],
          ['REST / OpenAPI', 'https://cryptenv-backend.onrender.com/swagger-ui.html'],
        ].map(([t, d]) => (
          <div key={t} className="rounded-xl border border-border/70 p-4 bg-card/40">
            <p className="font-semibold">{t}</p>
            <p className="text-sm text-muted-foreground mt-1">{d}</p>
          </div>
        ))}
      </div>
      <div className="flex justify-between pt-4 border-t">
        <Link to="/docs/security" className="text-muted-foreground hover:text-primary">
          ← Security
        </Link>
        <Link to="/docs/sdks" className="text-primary font-medium hover:underline">
          Next: SDKs →
        </Link>
      </div>
    </article>
  )
}

export function SdksDoc() {
  return (
    <article className="space-y-6 max-w-3xl">
      <h1 className="text-3xl font-bold">SDKs</h1>
      <h2 className="text-xl font-semibold">Node (@cryptenv/sdk 1.1+)</h2>
      <Code>{`npm install @cryptenv/sdk
# .env: CRYPTENV_EMAIL, CRYPTENV_PASSWORD, CRYPTENV_WORKSPACE_ENCRYPTION_KEY
const CryptEnv = require('@cryptenv/sdk')
await CryptEnv.init()
const db = CryptEnv.get('DATABASE_URL')`}</Code>
      <h2 className="text-xl font-semibold">Java (cryptenv-sdk 1.1+)</h2>
      <Code>{`CryptEnvClient client = new CryptEnvClient(apiKey);
// or new CryptEnvClient("https://cryptenv-backend.onrender.com", apiKey);
String value = client.getSecret("DATABASE_URL");`}</Code>
      <div className="flex justify-between pt-4 border-t">
        <Link to="/docs/integrations" className="text-muted-foreground hover:text-primary">
          ← Integrations
        </Link>
        <Link to="/docs/tutorials" className="text-primary font-medium hover:underline">
          Next: Tutorials →
        </Link>
      </div>
    </article>
  )
}

export function TutorialsDoc() {
  return (
    <article className="space-y-6 max-w-3xl">
      <h1 className="text-3xl font-bold flex items-center gap-3">
        Tutorials <span className="text-xs font-medium rounded-full bg-muted px-2 py-1 text-muted-foreground">Soon</span>
      </h1>
      <p className="text-muted-foreground">
        Step-by-step guides (Spring Boot + CLI, Next.js + Node SDK, GitHub Actions) are on the roadmap. Use Getting Started and
        CLI Reference for now.
      </p>
      <div className="flex justify-between pt-4 border-t">
        <Link to="/docs/sdks" className="text-muted-foreground hover:text-primary">
          ← SDKs
        </Link>
        <Link to="/overview" className="text-primary font-medium hover:underline">
          Back to Dashboard →
        </Link>
      </div>
    </article>
  )
}
