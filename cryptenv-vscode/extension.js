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

async function updateStatusBarItem(item, api) {
    const authed = await api.isAuthenticated();
    const jwt = await api.getJwt();
    const apiKey = await api.getApiKey();
    const mode = jwt ? 'JWT' : (apiKey ? 'API' : 'OFF');
    item.text = '$(shield) CryptEnv ' + (authed ? ' \u00b7 ' + mode : '');
    item.tooltip = 'CryptEnv Vault \u2014 ' + (authed ? mode + ' authenticated. Open Secrets Explorer.' : 'Not signed in. Click to open Secrets Explorer.');
}

function makeStatusBarItem() {
    const item = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 200);
    item.name = 'CryptEnv';
    item.text = '$(shield) CryptEnv';
    item.tooltip = 'CryptEnv Vault \u2014 open Secrets Explorer';
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
        const needsKey = w.hasEncryptionKey !== true;
        return {
            label: w.name + (needsKey ? ' \u26a0 needs encryption key' : ''),
            description: w.ownerUsername ? 'owner: ' + w.ownerUsername : '',
            detail: w.description || null,
            workspace: w,
            needsKey: needsKey
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
    if (picked.needsKey) {
        await vscode.commands.executeCommand('cryptenv-vscode.setEncryptionKey', picked.workspace);
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

async function resolveWorkspaceAndEnv(api, firstArg, secondArg) {
    let workspace = null;
    let environment = null;

    if (firstArg instanceof EnvironmentItem) {
        workspace = firstArg.workspace ? firstArg.workspace : null;
        environment = firstArg.environment ? firstArg.environment : null;
    } else if (firstArg instanceof WorkspaceItem) {
        workspace = firstArg.workspace ? firstArg.workspace : null;
        if (secondArg instanceof EnvironmentItem) {
            environment = secondArg.environment ? secondArg.environment : null;
        }
    } else if (firstArg && typeof firstArg === 'object') {
        if (firstArg.workspace && !(firstArg.environment)) {
            workspace = firstArg.workspace;
        } else if (firstArg.workspace && firstArg.environment) {
            workspace = firstArg.workspace;
            environment = firstArg.environment;
        } else if (firstArg.environment) {
            environment = firstArg.environment;
            if (environment.workspace) {
                workspace = environment.workspace;
            }
        }
    }

    if (!workspace) {
        workspace = await pickWorkspace(api, 'Workspace for this action');
    } else if (workspace && workspace.hasEncryptionKey !== true) {
        await vscode.commands.executeCommand('cryptenv-vscode.setEncryptionKey', workspace);
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
    updateStatusBarItem(statusItem, api);

    function refresh() {
        secretTreeProvider.refresh();
        updateStatusBarItem(statusItem, api);
    }

    function handleError(err) {
        const msg = (err && err.message) ? err.message : String(err);
        if (!msg) return;
        const lower = String(msg).toLowerCase();
        if (lower.indexOf('cancelled') >= 0 || lower.indexOf('cancel') >= 0) {
            return;
        }
        const isAuthErr = lower.indexOf('authentication') >= 0 || lower.indexOf('401') >= 0 || lower.indexOf('403') >= 0 || lower.indexOf('expired') >= 0;
        if (isAuthErr) {
            vscode.window.showErrorMessage(msg, 'Sign in again', 'Use API Key').then(function(choice) {
                if (choice === 'Sign in again') {
                    vscode.commands.executeCommand('cryptenv-vscode.login');
                } else if (choice === 'Use API Key') {
                    vscode.commands.executeCommand('cryptenv-vscode.setApiKey');
                }
            });
        } else {
            vscode.window.showErrorMessage(msg);
        }
        refresh();
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
            if (!resp || !resp.token) {
                throw new Error('Sign in failed: no token returned by the server.');
            }
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
                prompt: 'Username (3-50 characters)',
                ignoreFocusOut: true,
                validateInput: function(v) {
                    if (!v) return 'Username is required';
                    if (v.length < 3) return 'Username must be at least 3 characters';
                    if (v.length > 50) return 'Username must not exceed 50 characters';
                    return null;
                }
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
                ignoreFocusOut: true,
                validateInput: function(v) { return v && v.length > 100 ? 'Too long (max 100 characters)' : null; }
            }) || '';
            const lastName = await vscode.window.showInputBox({
                title: 'Create CryptEnv Account',
                prompt: 'Last name (optional)',
                ignoreFocusOut: true,
                validateInput: function(v) { return v && v.length > 100 ? 'Too long (max 100 characters)' : null; }
            }) || '';
            await vscode.window.withProgress(
                { location: vscode.ProgressLocation.Notification, title: 'Creating account...' },
                function() { return api.register(email, username, password, firstName, lastName); }
            );
            const choice = await vscode.window.showInformationMessage(
                'Account created successfully. Sign in now?',
                { modal: true },
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
                const confirm = await confirmModal(
                    'Regenerating your API key will invalidate the current one. Continue?',
                    'Regenerate Key'
                );
                if (!confirm) return;
                const updated = await vscode.window.withProgress(
                    { location: vscode.ProgressLocation.Notification, title: 'Regenerating API key...' },
                    function() { return api.regenerateApiKey(); }
                );
                const copyChoice = await vscode.window.showInformationMessage(
                    'New API Key generated. Store it safely - it will only be shown once.',
                    { modal: true, detail: 'New API Key: ' + updated.apiKey },
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
                placeHolder: 'https://cryptenv-backend.onrender.com/',
                validateInput: function(v) {
                    return !/^https?:\/\//i.test(String(v || '').trim())
                        ? 'Enter a full http:// or https:// URL'
                        : null;
                }
            });
            if (!url) return;
            await api.setBaseUrl(url);
            vscode.window.showInformationMessage('Backend URL saved: ' + await api.getBaseUrl());
            refresh();
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.refreshSecrets', function() {
        refresh();
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.setEncryptionKey', function(item) {
        return Promise.resolve().then(async function() {
            await requireAuth(api);
            let workspace = null;
            if (item && typeof item === 'object') {
                workspace = (item instanceof WorkspaceItem) ? item.workspace : item;
            }
            if (!workspace || !workspace.id) {
                workspace = await pickWorkspace(api, 'Workspace to set encryption key for');
            }
            if (!workspace) return;
            const workspaceEncryptionKey = await vscode.window.showInputBox({
                title: 'Workspace Encryption Key: ' + workspace.name,
                prompt: 'Required AES workspace key (16\u2013512 characters). It encrypts every secret in this workspace. Keep this key safe.',
                password: true,
                ignoreFocusOut: true,
                validateInput: function(v) {
                    if (!v) return 'Workspace encryption key is required';
                    return v.length < 16 || v.length > 512 ? 'Key must be 16\u2013512 characters' : null;
                }
            });
            if (!workspaceEncryptionKey) return;
            await vscode.window.withProgress(
                { location: vscode.ProgressLocation.Notification, title: 'Setting encryption key...' },
                function() { return api.setWorkspaceEncryptionKey(workspace.id, workspaceEncryptionKey); }
            );
            workspace.hasEncryptionKey = true;
            vscode.window.showInformationMessage('Encryption key set for workspace "' + workspace.name + '". Keep the key safe - it cannot be recovered.');
            refresh();
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.createWorkspace', function() {
        return Promise.resolve().then(async function() {
            await requireAuth(api);
            const name = await vscode.window.showInputBox({
                title: 'New Workspace',
                prompt: 'Workspace name (3-100 characters)',
                ignoreFocusOut: true,
                validateInput: function(v) {
                    return !v || v.trim().length < 3
                        ? 'Name must be at least 3 characters'
                        : (v.length > 100 ? 'Name too long (max 100 characters)' : null);
                }
            });
            if (!name) return;
            const description = await vscode.window.showInputBox({
                title: 'New Workspace',
                prompt: 'Description (optional, max 500 characters)',
                ignoreFocusOut: true,
                validateInput: function(v) { return v && v.length > 500 ? 'Too long (max 500 characters)' : null; }
            }) || '';
            const workspaceEncryptionKey = await vscode.window.showInputBox({
                title: 'Workspace Encryption Key',
                prompt: 'Required AES workspace key (16\u2013512 characters). It encrypts every secret in this workspace. Keep this key safe - it cannot be recovered.',
                password: true,
                ignoreFocusOut: true,
                validateInput: function(v) {
                    if (!v) return 'Workspace encryption key is required';
                    return v.length < 16 || v.length > 512 ? 'Key must be 16\u2013512 characters' : null;
                }
            });
            if (!workspaceEncryptionKey) return;
            const ws = await vscode.window.withProgress(
                { location: vscode.ProgressLocation.Notification, title: 'Creating workspace...' },
                function() { return api.createWorkspace(name.trim(), description, workspaceEncryptionKey); }
            );
            vscode.window.showInformationMessage('Workspace "' + ws.name + '" created. Its encryption key is stored wrapped by the server.');
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
            await vscode.window.withProgress(
                { location: vscode.ProgressLocation.Notification, title: 'Deleting workspace...' },
                function() { return api.deleteWorkspace(workspace.id); }
            );
            vscode.window.showInformationMessage('Workspace "' + workspace.name + '" deleted.');
            refresh();
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.createEnvironment', function(item) {
        return Promise.resolve().then(async function() {
            await requireAuth(api);
            let workspace = (item && item.workspace) ? item.workspace : null;
            if (workspace && workspace.hasEncryptionKey !== true) {
                await vscode.commands.executeCommand('cryptenv-vscode.setEncryptionKey', workspace);
            }
            if (!workspace) {
                workspace = await pickWorkspace(api, 'Workspace for the new environment');
            }
            if (!workspace) return;
            const envTypes = ['DEVELOPMENT', 'STAGING', 'PRODUCTION'];
            const envPicked = await vscode.window.showQuickPick(
                envTypes.map(function(t) {
                    const icons = { DEVELOPMENT: '\u{1F4BB} DEVELOPMENT', STAGING: '\u{1F9EA} STAGING', PRODUCTION: '\u{1F6E1}\uFE0F PRODUCTION' };
                    return { label: icons[t] || t, description: t, envType: t };
                }),
                { title: 'Environment Type', ignoreFocusOut: true, canPickMany: false, placeHolder: 'Select environment type' }
            );
            if (!envPicked) return;
            const env = await vscode.window.withProgress(
                { location: vscode.ProgressLocation.Notification, title: 'Creating environment...' },
                function() { return api.createEnvironment(workspace.id, envPicked.envType); }
            );
            vscode.window.showInformationMessage(
                'Environment "' + String(env.name || envPicked.envType) + '" created in "' + workspace.name + '".'
            );
            refresh();
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.deleteEnvironment', function(item) {
        return Promise.resolve().then(async function() {
            await requireAuth(api);
            let environment = item && item.environment ? item.environment : null;
            let workspace = item && item.workspace ? item.workspace : null;
            if (!environment) {
                if (!workspace) {
                    workspace = await pickWorkspace(api, 'Workspace containing the environment');
                }
                environment = await pickEnvironment(api, workspace.id, 'Environment to delete');
            }
            const envName = String(environment.name || 'DEVELOPMENT');
            const ok = await confirmModal(
                'Delete environment "' + envName + '" and all of its secrets? This cannot be undone.',
                'Delete Environment'
            );
            if (!ok) return;
            await vscode.window.withProgress(
                { location: vscode.ProgressLocation.Notification, title: 'Deleting environment...' },
                function() { return api.deleteEnvironment(environment.id); }
            );
            vscode.window.showInformationMessage('Environment "' + envName + '" deleted.');
            refresh();
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.addSecret', function(firstArg, secondArg) {
        return Promise.resolve().then(async function() {
            await requireAuth(api);
            const resolved = await resolveWorkspaceAndEnv(api, firstArg, secondArg);
            const workspace = resolved.workspace;
            const environment = resolved.environment;
            const key = await vscode.window.showInputBox({
                title: 'New Secret',
                prompt: 'Secret key (e.g. DATABASE_URL, max 255 characters)',
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
                prompt: 'Description (optional, max 500 characters)',
                ignoreFocusOut: true,
                validateInput: function(v) { return v && v.length > 500 ? 'Too long (max 500 characters)' : null; }
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

    async function getPlainValue(item) {
        if (!item || !item.secret) throw new Error('No secret selected. Right-click a secret and try again.');
        if (!item.environment || !item.environment.id) throw new Error('Missing environment context for this secret.');
        let value = item.secret.value;
        try {
            const fresh = await api.getSecretByEnvironment(item.environment.id, item.secret.key);
            if (fresh && fresh.value !== undefined && fresh.value !== null) value = fresh.value;
        } catch (_) { }
        return value || '';
    }

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.editSecret', function(item) {
        return Promise.resolve().then(async function() {
            await requireAuth(api);
            if (!item || !item.secret) {
                throw new Error('Right-click a secret and choose Edit.');
            }
            const currentValue = await getPlainValue(item);
            const newValue = await vscode.window.showInputBox({
                title: 'Edit Secret Value',
                prompt: 'New value for ' + item.secret.key + ' (leave unchanged to keep current value)',
                password: true,
                ignoreFocusOut: true,
                value: currentValue
            });
            if (newValue === undefined) return;
            const description = await vscode.window.showInputBox({
                title: 'Edit Secret Description',
                prompt: 'Description (optional, max 500 characters)',
                ignoreFocusOut: true,
                value: item.secret.description || '',
                validateInput: function(v) { return v && v.length > 500 ? 'Too long (max 500 characters)' : null; }
            });
            await vscode.window.withProgress(
                { location: vscode.ProgressLocation.Notification, title: 'Updating secret...' },
                function() { return api.updateSecret(item.environment.id, item.secret.key, newValue, description); }
            );
            vscode.window.showInformationMessage('Secret "' + item.secret.key + '" updated.');
            refresh();
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.deleteSecret', function(item) {
        return Promise.resolve().then(async function() {
            await requireAuth(api);
            let key = null;
            let environmentId = item && item.environment ? item.environment.id : null;
            if (item && item.secret) {
                key = item.secret.key;
            } else {
                key = await vscode.window.showInputBox({
                    title: 'Delete a Secret',
                    prompt: 'Secret key to delete',
                    ignoreFocusOut: true
                });
                if (!key) return;
                const workspace = await pickWorkspace(api, 'Workspace containing the secret');
                const environment = await pickEnvironment(api, workspace.id, 'Environment containing the secret');
                environmentId = environment.id;
            }
            const ok = await confirmModal(
                'Delete secret "' + key + '"? This cannot be undone.',
                'Delete Secret'
            );
            if (!ok) return;
            await vscode.window.withProgress(
                { location: vscode.ProgressLocation.Notification, title: 'Deleting secret...' },
                function() { return api.deleteSecret(environmentId, key); }
            );
            vscode.window.showInformationMessage('Secret "' + key + '" deleted.');
            refresh();
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.copyValue', function(item) {
        return Promise.resolve().then(async function() {
            await requireAuth(api);
            const value = await getPlainValue(item);
            await vscode.env.clipboard.writeText(value);
            vscode.window.showInformationMessage('Copied value for ' + item.secret.key);
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.previewSecret', function(item) {
        return Promise.resolve().then(async function() {
            await requireAuth(api);
            if (!item || !item.secret) return;
            const value = await getPlainValue(item);
            const header = item.secret.key + ' \u00b7 ' + String(item.environment.name || 'DEVELOPMENT');
            const choice = await vscode.window.showInformationMessage(
                header,
                { modal: true, detail: value || '(empty value)' },
                'Copy Value',
                'Insert at Cursor'
            );
            if (choice === 'Copy Value') {
                await vscode.env.clipboard.writeText(value);
                vscode.window.showInformationMessage('Copied value for ' + item.secret.key);
            } else if (choice === 'Insert at Cursor') {
                const editor = vscode.window.activeTextEditor;
                if (!editor) {
                    vscode.window.showWarningMessage('Open a text editor first to insert the value.');
                    return;
                }
                await editor.edit(function(eb) {
                    eb.replace(editor.selection, value);
                });
            }
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.insertKey', function(item) {
        return Promise.resolve().then(async function() {
            if (!item || !item.secret) {
                vscode.window.showWarningMessage('Right-click a secret to insert its key.');
                return;
            }
            const editor = vscode.window.activeTextEditor;
            if (!editor) {
                vscode.window.showWarningMessage('Open a text editor first.');
                return;
            }
            await editor.edit(function(eb) {
                eb.replace(editor.selection, item.secret.key);
            });
        }).catch(handleError);
    }));

    subscriptions.push(vscode.commands.registerCommand('cryptenv-vscode.insertValue', function(item) {
        return Promise.resolve().then(async function() {
            await requireAuth(api);
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
    updateStatusBarItem(statusItem, api);
}

function deactivate() { }

module.exports = { activate: activate, deactivate: deactivate };
