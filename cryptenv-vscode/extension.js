const vscode = require('vscode');
const CryptEnvAPI = require('./api');
const {
    SecretTreeProvider,
    WorkspaceItem,
    EnvironmentItem,
    SecretItem
} = require('./SecretTreeProvider');

function requireAuth(api) {
    return api.isAuthenticated().then(function(ok) {
        if (!ok) {
            throw new Error('Not signed in. Use Sign in, Set API Key, or Create account first.');
        }
        return true;
    });
}

function makeStatusBarItem() {
    const item = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 200);
    item.name = 'CryptEnv';
    item.text = 'CryptEnv';
    item.tooltip = 'CryptEnv - open Secrets Explorer';
    item.command = 'workbench.view.extension.cryptenv-explorer';
    item.show();
    return item;
}

async function pickWorkspace(api, title) {
    await requireAuth(api);
    const workspaces = await api.listWorkspaces();
    if (!workspaces || workspaces.length === 0) {
        const create = await vscode.window.showWarningMessage(
            'No workspaces found. Create one now?',
            'Create Workspace'
        );
        if (create === 'Create Workspace') {
            await vscode.commands.executeCommand('cryptenv-vscode.createWorkspace');
        }
        throw new Error('No workspaces available');
    }
    const items = workspaces.map(function(w) {
        return {
            label: w.name,
            description: w.ownerUsername ? 'owner: ' + w.ownerUsername : '',
            detail: w.description || null,
            workspace: w
        };
    });
    const picked = await vscode.window.showQuickPick(items, {
        title: title || 'Select a Workspace',
        ignoreFocusOut: true,
        canPickMany: false,
        matchOnDetail: true,
        matchOnDescription: true
    });
    if (!picked) {
        throw new Error('Workspace selection cancelled');
    }
    return picked.workspace;
}

async function pickEnvironment(api, workspaceId, title) {
    const environments = await api.listEnvironments(workspaceId);
    if (!environments || environments.length === 0) {
        const create = await vscode.window.showWarningMessage(
            'This workspace has no environments. Create one now?',
            'Create Environment'
        );
        if (create === 'Create Environment') {
            await vscode.commands.executeCommand('cryptenv-vscode.createEnvironment');
        }
        throw new Error('No environments available');
    }
    const items = environments.map(function(e) {
        const name = String(e.name || 'DEVELOPMENT');
        return {
            label: name,
            description: e.isActive === false ? 'inactive' : 'active',
            environment: e
        };
    });
    const picked = await vscode.window.showQuickPick(items, {
        title: title || 'Select an Environment',
        ignoreFocusOut: true,
        canPickMany: false
    });
    if (!picked) {
        throw new Error('Environment selection cancelled');
    }
    return picked.environment;
}

async function resolveWorkspaceAndEnv(api, wsItem, envItem) {
    let workspace = wsItem && wsItem.workspace ? wsItem.workspace : null;
    let environment = envItem && envItem.environment ? envItem.environment : null;

    if (!workspace) {
        workspace = await pickWorkspace(api, 'Workspace for this action');
    }
    if (!environment) {
        environment = await pickEnvironment(api, workspace.id, 'Environment for this action');
    }
    return { workspace: workspace, environment: environment };
}

async function confirmModal(message, confirmLabel) {
    const result = await vscode.window.showWarningMessage(
        message,
        { modal: true },
        confirmLabel,
        'Cancel'
    );
    return result === confirmLabel;
}

function activate(context) {
    const api = new CryptEnvAPI(context);
    const secretTreeProvider = new SecretTreeProvider(api, context);
    const treeView = vscode.window.createTreeView('cryptenv-secrets-view', {
        treeDataProvider: secretTreeProvider,
        showCollapseAll: true,
        canSelectMany: false
    });
    const statusItem = makeStatusBarItem();

    function refresh() {
        secretTreeProvider.refresh();
    }

    function handleError(err) {
        const msg = (err && err.message) ? err.message : String(err);
        if (String(msg).toLowerCase().indexOf('cancelled') >= 0 || String(msg).toLowerCase().indexOf('cancel') >= 0) {
            return;
        }
        vscode.window.showErrorMessage(msg);
    }

    const subscriptions = [];

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.login', function() {
        return Promise.resolve().then(async function() {
            const email = await vscode.window.showInputBox({
                title: 'CryptEnv Sign In',
                prompt: 'Email',
                ignoreFocusOut: true,
                placeHolder: 'you@example.com',
                validateInput: function(v) { return !v ? 'Email is required' : null; }
            });
            if (!email) return;
            const password = await vscode.window.showInputBox({
                title: 'CryptEnv Sign In',
                prompt: 'Password',
                password: true,
                ignoreFocusOut: true,
                validateInput: function(v) { return !v ? 'Password is required' : null; }
            });
            if (!password) return;
            const resp = await vscode.window.withProgress(
                { location: vscode.ProgressLocation.Notification, title: 'Signing in to CryptEnv...' },
                function() { return api.login(email, password); }
            );
            await api.setJwt(resp.token);
            const greeting = 'Welcome, ' + (resp.username || resp.email || email);
            vscode.window.showInformationMessage(greeting);
            refresh();
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.register', function() {
        return Promise.resolve().then(async function() {
            const email = await vscode.window.showInputBox({
                title: 'Create CryptEnv Account',
                prompt: 'Email',
                ignoreFocusOut: true,
                validateInput: function(v) { return !v ? 'Email is required' : null; }
            });
            if (!email) return;
            const username = await vscode.window.showInputBox({
                title: 'Create CryptEnv Account',
                prompt: 'Username',
                ignoreFocusOut: true,
                validateInput: function(v) { return !v ? 'Username is required' : null; }
            });
            if (!username) return;
            const password = await vscode.window.showInputBox({
                title: 'Create CryptEnv Account',
                prompt: 'Password (at least 8 characters)',
                password: true,
                ignoreFocusOut: true,
                validateInput: function(v) { return (!v || v.length < 8) ? 'Password must be at least 8 characters' : null; }
            });
            if (!password) return;
            const firstName = await vscode.window.showInputBox({
                title: 'Create CryptEnv Account',
                prompt: 'First name (optional)',
                ignoreFocusOut: true
            }) || '';
            const lastName = await vscode.window.showInputBox({
                title: 'Create CryptEnv Account',
                prompt: 'Last name (optional)',
                ignoreFocusOut: true
            }) || '';
            await vscode.window.withProgress(
                { location: vscode.ProgressLocation.Notification, title: 'Creating account...' },
                function() { return api.register(email, username, password, firstName, lastName); }
            );
            const choice = await vscode.window.showInformationMessage(
                'Account created successfully. Sign in now?',
                'Sign in'
            );
            if (choice === 'Sign in') {
                await vscode.commands.executeCommand('cryptenv-vscode.login');
            }
            refresh();
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.logout', function() {
        return Promise.resolve().then(async function() {
            await api.clearAuth();
            vscode.window.showInformationMessage('Signed out of CryptEnv.');
            refresh();
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.profile', function() {
        return Promise.resolve().then(async function() {
            await requireAuth(api);
            const profile = await vscode.window.withProgress(
                { location: vscode.ProgressLocation.Window, title: 'Fetching profile...' },
                function() { return api.getProfile(); }
            );
            const apiPreview = profile.apiKey ? profile.apiKey.slice(0, 14) + '\u2026' : '(none)';
            const lines = [
                'Email: ' + (profile.email || '-'),
                'Username: ' + (profile.username || '-'),
                'First Name: ' + (profile.firstName || '-'),
                'Last Name: ' + (profile.lastName || '-'),
                'API Key: ' + apiPreview,
                'Created At: ' + (profile.createdAt ? new Date(profile.createdAt).toLocaleString() : '-')
            ];
            const choice = await vscode.window.showInformationMessage(
                'CryptEnv Profile',
                { modal: true, detail: lines.join('\n') },
                'Regenerate API Key'
            );
            if (choice === 'Regenerate API Key') {
                const updated = await api.regenerateApiKey();
                const copyChoice = await vscode.window.showInformationMessage(
                    'New API Key: ' + updated.apiKey,
                    { modal: true },
                    'Copy and Use This Key'
                );
                if (copyChoice === 'Copy and Use This Key') {
                    await vscode.env.clipboard.writeText(updated.apiKey);
                    await api.setApiKey(updated.apiKey);
                    vscode.window.showInformationMessage('Copied new API key and applied it for this session.');
                    refresh();
                }
            }
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.setApiKey', function() {
        return Promise.resolve().then(async function() {
            const current = await api.getApiKey() || '';
            const key = await vscode.window.showInputBox({
                title: 'CryptEnv API Key',
                prompt: 'Paste your CryptEnv API key',
                password: true,
                ignoreFocusOut: true,
                value: current,
                placeHolder: 'ce_live_xxxxxxxxxxxxxxxxxxxxxxxxxxxx',
                validateInput: function(v) { return !v ? 'API Key is required' : null; }
            });
            if (!key) return;
            await api.setApiKey(key.trim());
            vscode.window.showInformationMessage('API Key stored securely.');
            refresh();
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.setBaseUrl', function() {
        return Promise.resolve().then(async function() {
            const current = await api.getBaseUrl();
            const url = await vscode.window.showInputBox({
                title: 'CryptEnv Backend URL',
                prompt: 'Base URL for the CryptEnv API',
                value: current,
                ignoreFocusOut: true,
                placeHolder: 'https://<your-cryptenv>/api',
                validateInput: function(v) { return !v ? 'URL is required' : null; }
            });
            if (!url) return;
            await api.setBaseUrl(url);
            vscode.window.showInformationMessage('Backend URL saved: ' + url);
            refresh();
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.refreshSecrets', function() {
        refresh();
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.createWorkspace', function() {
        return Promise.resolve().then(async function() {
            await requireAuth(api);
            const name = await vscode.window.showInputBox({
                title: 'New Workspace',
                prompt: 'Workspace name',
                ignoreFocusOut: true,
                validateInput: function(v) { return !v ? 'Name is required' : (v.length > 255 ? 'Name too long' : null); }
            });
            if (!name) return;
            const description = await vscode.window.showInputBox({
                title: 'New Workspace',
                prompt: 'Description (optional)',
                ignoreFocusOut: true,
                validateInput: function(v) { return v && v.length > 1000 ? 'Too long' : null; }
            }) || '';
            const workspaceEncryptionKey = await vscode.window.showInputBox({
                title: 'Workspace Encryption Key',
                prompt: 'AES workspace key (min 16 chars). Leave empty to set later in the dashboard.',
                password: true,
                ignoreFocusOut: true,
                validateInput: function(v) {
                    if (!v) return null;
                    return v.length < 16 ? 'Key must be at least 16 characters' : null;
                }
            });
            const ws = await vscode.window.withProgress(
                { location: vscode.ProgressLocation.Notification, title: 'Creating workspace...' },
                function() { return api.createWorkspace(name, description, workspaceEncryptionKey || undefined); }
            );
            const keyNote = ws.hasEncryptionKey
                ? ' Encryption key stored (wrapped).'
                : ' Reminder: set an encryption key before creating secrets.';
            vscode.window.showInformationMessage('Workspace "' + ws.name + '" created.' + keyNote);
            refresh();
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.deleteWorkspace', function(item) {
        return Promise.resolve().then(async function() {
            await requireAuth(api);
            let workspace = item && item.workspace ? item.workspace : null;
            if (!workspace) {
                workspace = await pickWorkspace(api, 'Workspace to delete');
            }
            const ok = await confirmModal(
                'Delete workspace "' + workspace.name + '" and all of its environments and secrets? This cannot be undone.',
                'Delete Workspace'
            );
            if (!ok) return;
            await api.deleteWorkspace(workspace.id);
            vscode.window.showInformationMessage('Workspace "' + workspace.name + '" deleted.');
            refresh();
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.createEnvironment', function(item) {
        return Promise.resolve().then(async function() {
            await requireAuth(api);
            let workspace = (item && item.workspace) ? item.workspace : (item && item.id ? item : null);
            if (!workspace) {
                workspace = await pickWorkspace(api, 'Workspace for the new environment');
            }
            const envTypes = ['DEVELOPMENT', 'TEST', 'STAGING', 'PRODUCTION'];
            const envPicked = await vscode.window.showQuickPick(
                envTypes.map(function(t) { return { label: t }; }),
                { title: 'Environment Type', ignoreFocusOut: true, canPickMany: false }
            );
            if (!envPicked) return;
            const env = await vscode.window.withProgress(
                { location: vscode.ProgressLocation.Notification, title: 'Creating environment...' },
                function() { return api.createEnvironment(workspace.id, envPicked.label); }
            );
            vscode.window.showInformationMessage(
                'Environment "' + String(env.name || envPicked.label) + '" created in "' + workspace.name + '".'
            );
            refresh();
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.addSecret', function(wsItem, envItem) {
        return Promise.resolve().then(async function() {
            await requireAuth(api);
            const resolved = await resolveWorkspaceAndEnv(api, wsItem, envItem);
            const workspace = resolved.workspace;
            const environment = resolved.environment;
            const key = await vscode.window.showInputBox({
                title: 'New Secret',
                prompt: 'Secret key (e.g. DATABASE_URL)',
                ignoreFocusOut: true,
                validateInput: function(v) {
                    if (!v) return 'Key is required';
                    if (v.length > 255) return 'Key too long (max 255)';
                    return null;
                }
            });
            if (!key) return;
            const value = await vscode.window.showInputBox({
                title: 'New Secret: ' + key,
                prompt: 'Secret value',
                password: true,
                ignoreFocusOut: true,
                validateInput: function(v) {
                    if (v === undefined || v === '') return 'Value is required';
                    return null;
                }
            });
            if (value === undefined || value === '') return;
            const description = await vscode.window.showInputBox({
                title: 'New Secret: ' + key,
                prompt: 'Description (optional)',
                ignoreFocusOut: true,
                validateInput: function(v) { return v && v.length > 500 ? 'Too long' : null; }
            });
            await vscode.window.withProgress(
                { location: vscode.ProgressLocation.Notification, title: 'Saving secret...' },
                function() { return api.createSecret(key, value, environment.id, description, true); }
            );
            vscode.window.showInformationMessage(
                'Secret "' + key + '" added to ' + workspace.name + ' / ' + String(environment.name || 'DEVELOPMENT')
            );
            refresh();
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.editSecret', function(item) {
        return Promise.resolve().then(async function() {
            await requireAuth(api);
            if (!item || !item.secret) {
                throw new Error('Right-click a secret and choose Edit.');
            }
            const newValue = await vscode.window.showInputBox({
                title: 'Edit Secret Value',
                prompt: 'New value for ' + item.secret.key,
                password: true,
                ignoreFocusOut: true,
                value: ''
            });
            if (newValue === undefined || newValue === '') {
                const skip = await vscode.window.showWarningMessage(
                    'Update description only, without changing the value?',
                    'Yes',
                    'Cancel'
                );
                if (skip !== 'Yes') return;
            }
            const description = await vscode.window.showInputBox({
                title: 'Edit Secret Description',
                prompt: 'Description (optional)',
                ignoreFocusOut: true,
                value: item.secret.description || '',
                validateInput: function(v) { return v && v.length > 500 ? 'Too long' : null; }
            });
            const payloadDescription = description !== undefined ? description : undefined;
            const payloadValue = (newValue !== undefined && newValue !== '') ? newValue : undefined;
            if (payloadValue === undefined && payloadDescription === undefined) {
                return;
            }
            await vscode.window.withProgress(
                { location: vscode.ProgressLocation.Notification, title: 'Updating secret...' },
                function() { return api.updateSecret(item.environment.id, item.secret.key, payloadValue, payloadDescription); }
            );
            vscode.window.showInformationMessage('Secret "' + item.secret.key + '" updated.');
            refresh();
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.deleteSecret', function(item) {
        return Promise.resolve().then(async function() {
            await requireAuth(api);
            let key = null;
            if (item && item.secret) {
                key = item.secret.key;
            } else {
                key = await vscode.window.showInputBox({
                    title: 'Delete a Secret',
                    prompt: 'Secret key to delete',
                    ignoreFocusOut: true
                });
                if (!key) return;
            }
            const ok = await confirmModal(
                'Delete secret "' + key + '"? This cannot be undone.',
                'Delete Secret'
            );
            if (!ok) return;
            await api.deleteSecret(key);
            vscode.window.showInformationMessage('Secret "' + key + '" deleted.');
            refresh();
        }).catch(handleError);
    }));

    async function getPlainValue(item) {
        if (!item || !item.secret) throw new Error('No secret selected.');
        let value = item.secret.value;
        try {
            const fresh = await api.getSecret(item.secret.key);
            if (fresh && fresh.value) value = fresh.value;
        } catch (_) { }
        return value || '';
    }

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.copyValue', function(item) {
        return Promise.resolve().then(async function() {
            const value = await getPlainValue(item);
            await vscode.env.clipboard.writeText(value);
            vscode.window.showInformationMessage('Copied value for ' + item.secret.key);
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.previewSecret', function(item) {
        return Promise.resolve().then(async function() {
            if (!item || !item.secret) return;
            const value = await getPlainValue(item);
            const choice = await vscode.window.showInformationMessage(
                item.secret.key,
                { modal: true, detail: value },
                'Copy Value'
            );
            if (choice === 'Copy Value') {
                await vscode.env.clipboard.writeText(value);
                vscode.window.showInformationMessage('Copied value for ' + item.secret.key);
            }
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.insertKey', function(item) {
        if (!item || !item.secret) {
            vscode.window.showWarningMessage('Right-click a secret to insert its key.');
            return;
        }
        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            vscode.window.showWarningMessage('Open a text editor first.');
            return;
        }
        editor.edit(function(eb) {
            eb.replace(editor.selection, item.secret.key);
        });
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.insertValue', function(item) {
        return Promise.resolve().then(async function() {
            if (!item || !item.secret) {
                vscode.window.showWarningMessage('Right-click a secret to insert its value.');
                return;
            }
            const editor = vscode.window.activeTextEditor;
            if (!editor) {
                vscode.window.showWarningMessage('Open a text editor first.');
                return;
            }
            const value = await getPlainValue(item);
            await editor.edit(function(eb) {
                eb.replace(editor.selection, value);
            });
        }).catch(handleError);
    }));

    context.subscriptions.push(treeView, statusItem, ...subscriptions);
}

function deactivate() { }

module.exports = { activate: activate, deactivate: deactivate };
