import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Button } from '../components/ui/button'
import { BookOpen, Code2, Terminal, Key, Shield, ExternalLink, ArrowRight, Copy } from 'lucide-react'
import { toast } from 'sonner'

const copyCode = (text) => {
  navigator.clipboard.writeText(text)
  toast.success('Copied to clipboard!')
}

const sections = [
  {
    title: 'Quick Start',
    icon: Shield,
    content: [
      { label: '1. Register & get your API key', code: null, text: 'Go to Settings → API Keys to view your personal API key (starts with ce_live_).' },
      { label: '2. Initialize the CLI', code: 'npm install -g cryptenv-cli\ncryptenv init\ncryptenv login', text: null },
      { label: '3. Add your first secret', code: 'cryptenv secrets set DATABASE_URL "postgresql://localhost/mydb"', text: null },
      { label: '4. Inject secrets at runtime', code: 'cryptenv run -- node server.js', text: null },
    ],
  },
  {
    title: 'REST API',
    icon: Code2,
    content: [
      { label: 'Base URL', code: 'http://localhost:8080/api', text: null },
      { label: 'Authenticate with JWT', code: 'curl -X POST /api/auth/login \\\n  -H "Content-Type: application/json" \\\n  -d \'{"email":"user@example.com","password":"pass"}\'', text: null },
      { label: 'Authenticate with API Key', code: 'curl /api/secrets \\\n  -H "X-API-Key: ce_live_yourkey..."', text: null },
      { label: 'List secrets', code: 'GET /api/secrets\nAuthorization: Bearer <token>', text: null },
      { label: 'Create a secret', code: 'POST /api/secrets\n{\n  "key": "MY_SECRET",\n  "value": "my-value",\n  "environmentId": 1\n}', text: null },
    ],
  },
  {
    title: 'Java SDK',
    icon: Code2,
    content: [
      {
        label: 'Maven dependency',
        code: `<dependency>
  <groupId>com.maheshshinde</groupId>
  <artifactId>cryptenv-sdk</artifactId>
  <version>1.0.0</version>
</dependency>`,
        text: null,
      },
      {
        label: 'Usage',
        code: `CryptEnvClient client = new CryptEnvClient.Builder()
    .baseUrl("http://localhost:8080")
    .apiKey("ce_live_yourkey...")
    .build();

String dbUrl = client.getSecret("DATABASE_URL");`,
        text: null,
      },
    ],
  },
  {
    title: 'CLI Commands',
    icon: Terminal,
    content: [
      { label: 'All commands', code: 'cryptenv --help\ncryptenv login          # Authenticate\ncryptenv logout         # Logout\ncryptenv secrets ls     # List secrets\ncryptenv secrets get KEY\ncryptenv secrets set KEY VALUE\ncryptenv secrets delete KEY\ncryptenv run -- <cmd>   # Run with secrets injected\ncryptenv profile        # Show user profile', text: null },
    ],
  },
]

export function Docs() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Documentation</h1>
        <p className="text-muted-foreground">Everything you need to integrate CryptEnv into your applications</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {[
          { label: 'REST API Docs', href: 'http://localhost:8080/swagger-ui.html', icon: Code2 },
          { label: 'CLI Reference', href: '#cli', icon: Terminal },
          { label: 'Java SDK', href: '#sdk', icon: Code2 },
          { label: 'API Keys', href: '/settings', icon: Key },
        ].map((item) => {
          const Icon = item.icon
          return (
            <Card key={item.label} className="hover:border-primary/50 transition-colors cursor-pointer group">
              <CardContent className="pt-6">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="p-2 rounded-lg bg-primary/10 group-hover:bg-primary/20 transition-colors">
                      <Icon className="h-4 w-4 text-primary" />
                    </div>
                    <span className="text-sm font-medium">{item.label}</span>
                  </div>
                  <ArrowRight className="h-4 w-4 text-muted-foreground group-hover:text-primary transition-colors" />
                </div>
              </CardContent>
            </Card>
          )
        })}
      </div>

      {sections.map((section) => {
        const Icon = section.icon
        return (
          <Card key={section.title} id={section.title.toLowerCase().replace(/\s/g, '-')}>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Icon className="h-5 w-5 text-primary" />
                {section.title}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {section.content.map((item, i) => (
                <div key={i} className="space-y-2">
                  <p className="text-sm font-semibold text-foreground">{item.label}</p>
                  {item.text && <p className="text-sm text-muted-foreground">{item.text}</p>}
                  {item.code && (
                    <div className="relative group/code">
                      <pre className="bg-muted rounded-lg p-4 text-sm font-mono overflow-x-auto whitespace-pre-wrap">
                        {item.code}
                      </pre>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="absolute top-2 right-2 h-7 w-7 opacity-0 group-hover/code:opacity-100 transition-opacity"
                        onClick={() => copyCode(item.code)}
                      >
                        <Copy className="h-3.5 w-3.5" />
                      </Button>
                    </div>
                  )}
                </div>
              ))}
            </CardContent>
          </Card>
        )
      })}
    </div>
  )
}
