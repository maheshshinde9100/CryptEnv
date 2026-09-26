const vscode = require('vscode');

class WorkspaceItem extends vscode.TreeItem {
    constructor(workspace) {
        super(workspace.name, vscode.TreeItemCollapsibleState.Expanded);
        this.workspace = workspace;
        this.contextValue = 'workspaceItem';
        const hasKey = workspace.hasEncryptionKey === true;
        const parts = [];
        if (workspace.description) {
            parts.push(workspace.description);
        }
        if (workspace.ownerUsername) {
            parts.push('owner: ' + workspace.ownerUsername);
        }
        if (!hasKey) {
            parts.push('needs encryption key');
        }
        this.tooltip = [
            'Workspace: ' + workspace.name,
            workspace.description ? 'Description: ' + workspace.description : null,
            workspace.ownerUsername ? 'Owner: ' + workspace.ownerUsername : null,
            'Encryption Key: ' + (hasKey ? 'configured' : 'NOT SET'),
            workspace.id ? 'ID: ' + workspace.id : null,
            workspace.createdAt ? 'Created: ' + new Date(workspace.createdAt).toLocaleString() : null
        ].filter(Boolean).join('\n');
        this.description = parts.length ? parts.join(' \u00b7 ') : '';
        if (hasKey) {
            this.iconPath = new vscode.ThemeIcon('folder-library', new vscode.ThemeColor('charts.blue'));
        } else {
            this.iconPath = new vscode.ThemeIcon('folder-library', new vscode.ThemeColor('charts.orange'));
            this.contextValue = 'workspaceItemNeedsKey';
        }
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
        let color;
        if (envName === 'PRODUCTION') {
            iconName = 'shield';
            color = new vscode.ThemeColor('testing.iconPassed');
        } else if (envName === 'STAGING') {
            iconName = 'beaker';
            color = new vscode.ThemeColor('charts.yellow');
        } else if (envName === 'DEVELOPMENT') {
            iconName = 'debug';
            color = new vscode.ThemeColor('charts.blue');
        } else if (envName === 'TEST') {
            iconName = 'test-view-icon';
            color = new vscode.ThemeColor('charts.purple');
        }
        this.iconPath = new vscode.ThemeIcon(iconName, color);
        const status = environment.isActive === false ? 'inactive' : '';
        this.description = status;
        this.tooltip = [
            'Environment: ' + envName,
            'Workspace: ' + workspace.name,
            status ? 'Status: ' + status : 'Status: active',
            environment.id ? 'ID: ' + environment.id : null,
            environment.createdAt ? 'Created: ' + new Date(environment.createdAt).toLocaleString() : null
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
            '**Key:** ' + secret.key,
            '**Value:** ' + masked,
            '**Workspace:** ' + workspace.name,
            '**Environment:** ' + envName,
            secret.description ? '**Description:** ' + secret.description : null,
            '**Encrypted:** ' + (secret.encrypted ? 'yes' : 'no'),
            '**Version:** ' + (secret.version || secret.currentVersion || 1),
            secret.createdAt ? '**Created:** ' + new Date(secret.createdAt).toLocaleString() : null,
            secret.updatedAt ? '**Updated:** ' + new Date(secret.updatedAt).toLocaleString() : null
        ].filter(Boolean);
        this.tooltip = new vscode.MarkdownString('### ' + secret.key + '\n\n' + tooltipLines.join('  \n'));
        this.description = (secret.description ? secret.description + ' \u00b7 ' : '') + masked;
        this.iconPath = new vscode.ThemeIcon('lock', new vscode.ThemeColor('charts.purple'));
        this.command = {
            command: 'cryptenv-vscode.previewSecret',
            title: 'Preview Secret',
            arguments: [this]
        };
        this.id = 'sec-' + String(workspace.id) + '-' + String(environment.id) + '-' + String(secret.id || secret.key);
        this.accessibilityInformation = {
            label: secret.key
        };
        this.resourceUri = vscode.Uri.parse('cryptenv-secret:///' + encodeURIComponent(workspace.name) + '/' + encodeURIComponent(envName) + '/' + encodeURIComponent(secret.key));
    }
}

class ActionItem extends vscode.TreeItem {
    constructor(label, description, iconName, command, detail, contextValue) {
        super(label, vscode.TreeItemCollapsibleState.None);
        this.description = description || '';
        this.tooltip = detail || label;
        if (iconName) {
            this.iconPath = new vscode.ThemeIcon(iconName);
        }
        if (command) {
            this.command = command;
        }
        this.contextValue = contextValue || 'emptyItem';
    }
}

class StatusItem extends vscode.TreeItem {
    constructor(label, description, iconName, color) {
        super(label, vscode.TreeItemCollapsibleState.None);
        this.description = description || '';
        this.tooltip = label + (description ? ' - ' + description : '');
        this.iconPath = color ? new vscode.ThemeIcon(iconName || 'info', color) : new vscode.ThemeIcon(iconName || 'info');
        this.contextValue = 'statusItem';
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

    async _getAuthStatusItems() {
        const items = [];
        const jwt = await this.api.getJwt();
        const apiKey = await this.api.getApiKey();
        const baseUrl = await this.api.getBaseUrl();
        const authMethod = jwt ? 'JWT (email sign-in)' : (apiKey ? 'API Key' : 'not authenticated');
        items.push(new StatusItem(
            'Authentication: ' + authMethod,
            '',
            jwt || apiKey ? 'pass' : 'circle-slash',
            jwt || apiKey ? new vscode.ThemeColor('testing.iconPassed') : new vscode.ThemeColor('testing.iconUnset')
        ));
        items.push(new StatusItem(
            'Backend: ' + baseUrl,
            '',
            'server',
            new vscode.ThemeColor('charts.blue')
        ));
        items.push(new ActionItem(
            'Sign in with email and password',
            'Click to sign in',
            'account',
            { command: 'cryptenv-vscode.login', title: 'Sign in' },
            'Authenticate to CryptEnv using email and password.'
        ));
        items.push(new ActionItem(
            'Use an API key',
            'Click to set an API key',
            'key',
            { command: 'cryptenv-vscode.setApiKey', title: 'Set API Key' },
            'Authenticate to CryptEnv using a long-lived API key.'
        ));
        items.push(new ActionItem(
            'Create a new account',
            'Click to register',
            'add',
            { command: 'cryptenv-vscode.register', title: 'Register' },
            'Register a new CryptEnv account from VS Code.'
        ));
        items.push(new ActionItem(
            'Configure backend URL',
            'Click to set URL',
            'server-environment',
            { command: 'cryptenv-vscode.setBaseUrl', title: 'Set Backend URL' },
            'Point the extension at a custom CryptEnv backend.'
        ));
        return items;
    }

    async getChildren(element) {
        const authed = await this.api.isAuthenticated();
        if (!authed) {
            return this._getAuthStatusItems();
        }

        if (!element) {
            try {
                const workspaces = await this.api.listWorkspaces();
                if (!workspaces || workspaces.length === 0) {
                    return [
                        new ActionItem(
                            'No workspaces found',
                            'Click to create one',
                            'new-folder',
                            { command: 'cryptenv-vscode.createWorkspace', title: 'Create Workspace' },
                            'Create your first encrypted workspace to get started.'
                        )
                    ];
                }
                const hasMissingKey = workspaces.some(function(w) { return w.hasEncryptionKey !== true; });
                const result = [];
                if (hasMissingKey) {
                    result.push(new StatusItem(
                        'Workspaces (' + workspaces.length + ') - one or more need an encryption key',
                        '',
                        'warning',
                        new vscode.ThemeColor('charts.orange')
                    ));
                }
                const sorted = workspaces.slice().sort(function(a, b) {
                    const ka = (a.hasEncryptionKey === true ? 0 : 1);
                    const kb = (b.hasEncryptionKey === true ? 0 : 1);
                    if (ka !== kb) return ka - kb;
                    return String(a.name || '').localeCompare(String(b.name || ''));
                });
                return result.concat(sorted.map(function(ws) { return new WorkspaceItem(ws); }));
            } catch (err) {
                return [
                    new ActionItem(
                        'Could not load workspaces',
                        err.message ? String(err.message).substring(0, 80) : 'Click to set URL',
                        'error',
                        { command: 'cryptenv-vscode.setBaseUrl', title: 'Set Backend URL' },
                        'Verify your network and backend URL configuration. Error: ' + (err.message || String(err))
                    ),
                    new ActionItem(
                        'Retry loading',
                        'Click to refresh',
                        'refresh',
                        { command: 'cryptenv-vscode.refreshSecrets', title: 'Refresh' }
                    )
                ];
            }
        }

        if (element instanceof WorkspaceItem) {
            try {
                if (element.workspace.hasEncryptionKey !== true) {
                    return [
                        new ActionItem(
                            'Encryption key not configured',
                            'Click to set encryption key',
                            'lock-small',
                            {
                                command: 'cryptenv-vscode.setEncryptionKey',
                                title: 'Set Workspace Encryption Key',
                                arguments: [element.workspace]
                            },
                            'This workspace needs a 16-512 character encryption key before secrets can be stored.',
                            'needsKeyItem'
                        )
                    ];
                }
                const environments = await this.api.listEnvironments(element.workspace.id);
                if (!environments || environments.length === 0) {
                    return [
                        new ActionItem(
                            'No environments yet',
                            'Click to add DEVELOPMENT / STAGING / PRODUCTION',
                            'add',
                            {
                                command: 'cryptenv-vscode.createEnvironment',
                                title: 'Create Environment',
                                arguments: [element]
                            },
                            'Create an environment to start storing secrets.'
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
                        err.message ? String(err.message).substring(0, 80) : '',
                        'error',
                        undefined,
                        'Error: ' + (err.message || String(err))
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
                            'No secrets yet',
                            'Click to add a key-value pair',
                            'add',
                            {
                                command: 'cryptenv-vscode.addSecret',
                                title: 'Add Secret',
                                arguments: [element.workspace, element.environment]
                            },
                            'Add your first secret to this environment.'
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
                        err.message ? String(err.message).substring(0, 80) : '',
                        'error',
                        undefined,
                        'Error: ' + (err.message || String(err))
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
    ActionItem: ActionItem,
    StatusItem: StatusItem
};
