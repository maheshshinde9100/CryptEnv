import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Button } from '../components/ui/button'
import { 
  Code2, Database, Github, Webhook, Globe, 
  Terminal, Package, Zap, ExternalLink, CheckCircle2
} from 'lucide-react'

const integrations = [
  {
    id: 'sdk-java',
    name: 'Java SDK',
    description: 'Use CryptEnv in your Spring Boot, Quarkus, or any JVM application',
    icon: Code2,
    category: 'SDK',
    status: 'available',
    docs: 'https://github.com/maheshshinde9100/CryptEnv/tree/main/cryptenv-sdk',
  },
  {
    id: 'cli',
    name: 'CLI Tool',
    description: 'Command-line interface for secret management and runtime injection',
    icon: Terminal,
    category: 'CLI',
    status: 'available',
    docs: 'https://github.com/maheshshinde9100/CryptEnv/tree/main/cryptenv-cli',
  },
  {
    id: 'rest-api',
    name: 'REST API',
    description: 'Direct HTTP API access with JWT or API key authentication',
    icon: Globe,
    category: 'API',
    status: 'available',
    docs: 'https://cryptenv-backend.onrender.com/swagger-ui.html',
  },
  {
    id: 'github-actions',
    name: 'GitHub Actions',
    description: 'Inject secrets into CI/CD pipelines using the CryptEnv CLI',
    icon: Github,
    category: 'CI/CD',
    status: 'coming-soon',
    docs: '#',
  },
  {
    id: 'docker',
    name: 'Docker / Compose',
    description: 'Pull secrets at container startup via the CLI run command',
    icon: Package,
    category: 'Container',
    status: 'available',
    docs: 'https://github.com/maheshshinde9100/CryptEnv',
  },
  {
    id: 'webhooks',
    name: 'Webhooks',
    description: 'Receive real-time notifications when secrets are accessed or rotated',
    icon: Webhook,
    category: 'Automation',
    status: 'coming-soon',
    docs: '#',
  },
  {
    id: 'postgresql',
    name: 'PostgreSQL',
    description: 'Built-in PostgreSQL backend with Flyway migrations and JPA validation',
    icon: Database,
    category: 'Database',
    status: 'available',
    docs: '#',
  },
  {
    id: 'node',
    name: 'Node.js / NPM',
    description: 'Use the CLI or REST API to inject secrets into Node.js apps',
    icon: Zap,
    category: 'Runtime',
    status: 'available',
    docs: '#',
  },
]

const categoryColors = {
  SDK: 'bg-blue-500/10 text-blue-600',
  CLI: 'bg-green-500/10 text-green-600',
  API: 'bg-purple-500/10 text-purple-600',
  'CI/CD': 'bg-orange-500/10 text-orange-600',
  Container: 'bg-teal-500/10 text-teal-600',
  Automation: 'bg-pink-500/10 text-pink-600',
  Database: 'bg-yellow-500/10 text-yellow-600',
  Runtime: 'bg-indigo-500/10 text-indigo-600',
}

export function Integrations() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Integrations</h1>
        <p className="text-muted-foreground">Connect CryptEnv with your favourite tools, runtimes, and CI/CD pipelines</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {integrations.map((integration) => {
          const Icon = integration.icon
          return (
            <Card key={integration.id} className="hover:border-primary/50 transition-colors group">
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between">
                  <div className="p-2.5 rounded-xl bg-primary/10 group-hover:bg-primary/20 transition-colors">
                    <Icon className="h-5 w-5 text-primary" />
                  </div>
                  {integration.status === 'available' ? (
                    <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-green-500/10 text-green-600 flex items-center gap-1">
                      <CheckCircle2 className="h-3 w-3" />
                      Available
                    </span>
                  ) : (
                    <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-gray-500/10 text-gray-500">
                      Coming Soon
                    </span>
                  )}
                </div>
                <CardTitle className="text-base mt-2">{integration.name}</CardTitle>
                <span className={`text-xs font-medium px-2 py-0.5 rounded-full w-fit ${categoryColors[integration.category]}`}>
                  {integration.category}
                </span>
              </CardHeader>
              <CardContent className="space-y-3">
                <CardDescription className="text-xs leading-relaxed">{integration.description}</CardDescription>
                <Button
                  variant="outline"
                  size="sm"
                  className="w-full"
                  disabled={integration.status !== 'available'}
                  onClick={() => integration.docs !== '#' && window.open(integration.docs, '_blank')}
                >
                  <ExternalLink className="h-3.5 w-3.5 mr-1.5" />
                  View Docs
                </Button>
              </CardContent>
            </Card>
          )
        })}
      </div>
    </div>
  )
}
