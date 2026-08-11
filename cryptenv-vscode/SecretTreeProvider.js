const vscode = require('vscode');

class WorkspaceItem extends vscode.TreeItem {
    constructor(workspace) {
        super(workspace.name, vscode.TreeItemCollapsibleState.Expanded);
        this.workspace = workspace;
        this.contextValue = 'workspaceItem';
        const parts = [];
        if (workspace.description) {
            parts.push(workspace.description);
        }
        if (workspace.ownerUsername) {
            parts.push('owner: ' + workspace.ownerUsername);
        }
        this.tooltip = [
            'Workspace: ' + workspace.name,
            workspace.description ? 'Description: ' + workspace.description : null,
            workspace.ownerUsername ? 'Owner: ' + workspace.ownerUsername : null,
            workspace.id ? 'ID: ' + workspace.id : null
        ].filter(Boolean).join('\n');
        this.description = parts.length ? parts.join(' \u00b7 ') : '';
        this.iconPath = new vscode.ThemeIcon('folder-library');
        this.id = 'ws-' + String(workspace.id);
        this.resourceUri = vscode.Uri.parse('cryptenv-workspace:///' + encodeURIComponent(workspace.name));
    }
}

class EnvironmentItem extends vscode.TreeItem {
    constructor(environment, workspace) {
        const envName = String(environment.name || 'DEVELOPMENT');
        super(envName, vscode.TreeItemCollapsibleState.Expanded);
        this.environment = environment;
        this.workspace = workspace;
        this.contextValue = 'environmentItem';
        let iconName = 'debug';
        if (envName === 'PRODUCTION') {
            iconName = 'shield';
        } else if (envName === 'STAGING') {
            iconName = 'beaker';
        } else if (envName === 'DEVELOPMENT') {
            iconName = 'debug';
        } else if (envName === 'TEST') {
            iconName = 'test-view-icon';
        }
        this.iconPath = new vscode.ThemeIcon(iconName);
        const status = environment.isActive === false ? 'inactive' : '';
        this.description = status;
        this.tooltip = [
            'Environment: ' + envName,
            'Workspace: ' + workspace.name,
            status ? 'Status: ' + status : 'Status: active',
            environment.id ? 'ID: ' + environment.id : null
        ].filter(Boolean).join('\n');
        this.id = 'env-' + String(workspace.id) + '-' + String(environment.id);
        this.resourceUri = vscode.Uri.parse('cryptenv-environment:///' + encodeURIComponent(workspace.name) + '/' + encodeURIComponent(envName));
    }
}

class SecretItem extends vscode.TreeItem {
    constructor(secret, environment, workspace) {
        super(secret.key, vscode.TreeItemCollapsibleState.None);
        this.secret = secret;
        this.environment = environment;
        this.workspace = workspace;
        this.contextValue = 'secretItem';
        const rawLen = (secret.value && typeof secret.value === 'string') ? secret.value.length : 0;
        const masked = rawLen > 0 ? '\u2022'.repeat(Math.min(24, Math.max(4, Math.min(rawLen, 12)))) : '(empty)';
        const envName = String((environment && environment.name) ? environment.name : 'DEVELOPMENT');
        const tooltipLines = [
            'Key: ' + secret.key,
            'Value: ' + masked,
            'Workspace: ' + workspace.name,
            'Environment: ' + envName,
            secret.description ? 'Description: ' + secret.description : null,
            'Encrypted: ' + (secret.encrypted ? 'yes' : 'no'),
            'Version: ' + (secret.version || secret.currentVersion || 1),
            secret.createdAt ? 'Created: ' + new Date(secret.createdAt).toLocaleString() : null,
            secret.updatedAt || secret.updatedAt ? 'Updated: ' + new Date(secret.updatedAt || secret.updatedAt).toLocaleString() : null
        ].filter(Boolean);
        this.tooltip = new vscode.MarkdownString('### ' + secret.key + '\n\n' + tooltipLines.join('  \n'));
        this.description = masked;
        this.iconPath = new vscode.ThemeIcon('lock');
        this.command = {
            command: 'cryptenv-vscode.previewSecret',
            title: 'Preview Secret',
            arguments: [this]
        };
        this.id = 'sec-' + String(workspace.id) + '-' + String(environment.id) + '-' + String(secret.id);
        this.accessibilityInformation = {
            label: secret.key
        };
        this.resourceUri = vscode.Uri.parse('cryptenv-secret:///' + encodeURIComponent(workspace.name) + '/' + encodeURIComponent(envName) + '/' + encodeURIComponent(secret.key));
    }
}

class ActionItem extends vscode.TreeItem {
    constructor(label, description, iconName, command, detail) {
        super(label, vscode.TreeItemCollapsibleState.None);
        this.description = description || '';
        this.tooltip = detail || label;
        if (iconName) {
            this.iconPath = new vscode.ThemeIcon(iconName);
        }
        if (command) {
            this.command = command;
        }
        this.contextValue = 'emptyItem';
    }
}

class SecretTreeProvider {
    constructor(api, context) {
        this.api = api;
        this.context = context;
        this._onDidChangeTreeData = new vscode.EventEmitter();
        this.onDidChangeTreeData = this._onDidChangeTreeData.event;
    }

    refresh(element) {
        if (element) {
            this._onDidChangeTreeData.fire(element);
        } else {
            this._onDidChangeTreeData.fire(undefined);
        }
    }

    getTreeItem(element) {
        return element;
    }

    getParent(element) {
        if (element instanceof EnvironmentItem) {
            return new WorkspaceItem(element.workspace);
        }
        if (element instanceof SecretItem) {
            return new EnvironmentItem(element.environment, element.workspace);
        }
        return undefined;
    }

    async getChildren(element) {
        const authed = await this.api.isAuthenticated();
        if (!authed) {
            return [
                new ActionItem(
                    'Sign in with email and password',
                    'Click to sign in',
                    'account',
                    { command: 'cryptenv-vscode.login', title: 'Sign in' },
                    'Authenticate to CryptEnv using email and password.'
                ),
                new ActionItem(
                    'Use an API key',
                    'Click to set an API key',
                    'key',
                    { command: 'cryptenv-vscode.setApiKey', title: 'Set API Key' },
                    'Authenticate to CryptEnv using a long-lived API key.'
                ),
                new ActionItem(
                    'Create a new account',
                    'Click to register',
                    'add',
                    { command: 'cryptenv-vscode.register', title: 'Register' },
                    'Register a new CryptEnv account from VS Code.'
                ),
                new ActionItem(
                    'Configure backend URL',
                    'Click to set URL',
                    'server',
                    { command: 'cryptenv-vscode.setBaseUrl', title: 'Set Backend URL' },
                    'Point the extension at a custom CryptEnv backend.'
                )
            ];
        }

        if (!element) {
            try {
                const workspaces = await this.api.listWorkspaces();
                if (!workspaces || workspaces.length === 0) {
                    return [
                        new ActionItem(
                            'No workspaces',
                            'Click to create one',
                            'new-folder',
                            { command: 'cryptenv-vscode.createWorkspace', title: 'Create Workspace' }
                        )
                    ];
                }
                const sorted = workspaces.slice().sort((a, b) => String(a.name || '').localeCompare(String(b.name || '')));
                return sorted.map(function(ws) { return new WorkspaceItem(ws); });
            } catch (err) {
                return [
                    new ActionItem(
                        'Could not load workspaces',
                        err.message ? String(err.message) : 'Click to set URL',
                        'error',
                        { command: 'cryptenv-vscode.setBaseUrl', title: 'Set Backend URL' },
                        'Verify your network and backend URL configuration.'
                    )
                ];
            }
        }

        if (element instanceof WorkspaceItem) {
            try {
                const environments = await this.api.listEnvironments(element.workspace.id);
                if (!environments || environments.length === 0) {
                    return [
                        new ActionItem(
                            'No environments',
                            'Click to add one',
                            'add',
                            {
                                command: 'cryptenv-vscode.createEnvironment',
                                title: 'Create Environment',
                                arguments: [element.workspace]
                            }
                        )
                    ];
                }
                const order = { DEVELOPMENT: 0, TEST: 1, STAGING: 2, PRODUCTION: 3 };
                const sorted = environments.slice().sort(function(a, b) {
                    const an = String(a.name || '');
                    const bn = String(b.name || '');
                    const ao = Object.prototype.hasOwnProperty.call(order, an) ? order[an] : 99;
                    const bo = Object.prototype.hasOwnProperty.call(order, bn) ? order[bn] : 99;
                    if (ao !== bo) return ao - bo;
                    return an.localeCompare(bn);
                });
                return sorted.map(function(env) { return new EnvironmentItem(env, element.workspace); });
            } catch (err) {
                return [
                    new ActionItem(
                        'Could not load environments',
                        err.message ? String(err.message) : '',
                        'error'
                    )
                ];
            }
        }

        if (element instanceof EnvironmentItem) {
            try {
                const secrets = await this.api.listSecretsByEnvironment(element.environment.id);
                if (!secrets || secrets.length === 0) {
                    return [
                        new ActionItem(
                            'No secrets',
                            'Click to add one',
                            'add',
                            {
                                command: 'cryptenv-vscode.addSecret',
                                title: 'Add Secret',
                                arguments: [element.workspace, element.environment]
                            }
                        )
                    ];
                }
                const sorted = secrets.slice().sort(function(a, b) {
                    return String(a.key || '').localeCompare(String(b.key || ''));
                });
                return sorted.map(function(s) { return new SecretItem(s, element.environment, element.workspace); });
            } catch (err) {
                return [
                    new ActionItem(
                        'Could not load secrets',
                        err.message ? String(err.message) : '',
                        'error'
                    )
                ];
            }
        }

        return [];
    }
}

module.exports = {
    SecretTreeProvider: SecretTreeProvider,
    WorkspaceItem: WorkspaceItem,
    EnvironmentItem: EnvironmentItem,
    SecretItem: SecretItem,
    ActionItem: ActionItem
};
