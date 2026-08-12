export const DOCS_NAV = [
  { id: 'getting-started', label: 'Getting Started', group: 'Start' },
  { id: 'workspaces', label: 'Workspace Management', group: 'Start' },
  { id: 'workflow', label: 'Development Workflow', group: 'Start' },
  { id: 'cicd', label: 'CI/CD Integration', group: 'Integrate' },
  { id: 'cli', label: 'CLI Reference', group: 'Integrate' },
  { id: 'vscode', label: 'VS Code Extension', group: 'Integrate' },
  { id: 'security', label: 'Security Features', group: 'Platform' },
  { id: 'integrations', label: 'Integrations', group: 'Platform' },
  { id: 'sdks', label: 'SDKs', group: 'Platform' },
  { id: 'tutorials', label: 'Tutorials', group: 'Platform', soon: true },
]

export function nextDocId(currentId) {
  const ids = DOCS_NAV.filter((d) => !d.soon).map((d) => d.id)
  const i = ids.indexOf(currentId)
  return i >= 0 && i < ids.length - 1 ? ids[i + 1] : null
}

export function prevDocId(currentId) {
  const ids = DOCS_NAV.filter((d) => !d.soon).map((d) => d.id)
  const i = ids.indexOf(currentId)
  return i > 0 ? ids[i - 1] : null
}

export function docLabel(id) {
  return DOCS_NAV.find((d) => d.id === id)?.label || id
}
