const assert = require('assert');
const Module = require('module');
const path = require('path');

const manifest = require('../package.json');
const registeredCommands = [];

class MockThemeIcon {
    constructor(id, color) {
        this.id = id;
        this.color = color;
    }
}

class MockThemeColor {
    constructor(id) {
        this.id = id;
    }
}

class MockMarkdownString {
    constructor(value) {
        this.value = value;
    }
}

const globalStateMap = new Map();
const secretsMap = new Map();

const vscode = {
    TreeItem: class TreeItem {
        constructor(label, collapsibleState) {
            this.label = label;
            this.collapsibleState = collapsibleState;
        }
    },
    TreeItemCollapsibleState: { None: 0, Collapsed: 1, Expanded: 2 },
    EventEmitter: class EventEmitter {
        constructor() { this.event = function () {}; this.fire = function() {}; }
        dispose() {}
    },
    Uri: { parse: function (value) { return { toString: function() { return String(value); } }; } },
    StatusBarAlignment: { Right: 2, Left: 1 },
    ThemeIcon: MockThemeIcon,
    ThemeColor: MockThemeColor,
    MarkdownString: MockMarkdownString,
    ProgressLocation: { Notification: 1, Window: 2, SourceControl: 3 },
    window: {
        createTreeView: function () { return { dispose: function () {} }; },
        createStatusBarItem: function () {
            const item = { show: function () {}, dispose: function () {} };
            return item;
        },
        showInputBox: function () { return Promise.resolve(null); },
        showQuickPick: function () { return Promise.resolve(null); },
        showInformationMessage: function () { return Promise.resolve(undefined); },
        showWarningMessage: function () { return Promise.resolve(undefined); },
        showErrorMessage: function () { return Promise.resolve(undefined); },
        withProgress: function (_opts, fn) { return Promise.resolve(fn ? fn({}) : undefined); }
    },
    commands: {
        registerCommand: function (command) {
            registeredCommands.push(command);
            return { dispose: function () {} };
        },
        executeCommand: function () { return Promise.resolve(); }
    },
    env: {
        clipboard: {
            writeText: function () { return Promise.resolve(); }
        }
    }
};

const contextMock = {
    subscriptions: [],
    globalState: {
        get: function (key, fallback) { return globalStateMap.has(key) ? globalStateMap.get(key) : fallback; },
        update: async function (key, value) { globalStateMap.set(key, value); }
    },
    secrets: {
        get: async function (key) { return secretsMap.has(key) ? secretsMap.get(key) : undefined; },
        store: async function (key, value) { secretsMap.set(key, value); },
        delete: async function (key) { secretsMap.delete(key); }
    }
};

const originalLoad = Module._load;
Module._load = function (request, parent, isMain) {
    if (request === 'vscode') return vscode;
    return originalLoad.call(this, request, parent, isMain);
};

try {
    globalStateMap.clear();
    secretsMap.clear();
    registeredCommands.length = 0;
    contextMock.subscriptions.length = 0;

    const extension = require(path.join(__dirname, '..', 'extension.js'));
    extension.activate(contextMock);

    const contributed = manifest.contributes.commands.map(function (item) { return item.command; });
    const registered = [...registeredCommands].sort();
    const expected = [...contributed].sort();

    assert.deepStrictEqual(registered, expected,
        'Every contributed command must be registered during activation. Registered: ' + JSON.stringify(registered) + ' vs expected: ' + JSON.stringify(expected));

    const expectedSubscriptions = contributed.length + 2;
    assert.ok(contextMock.subscriptions.length >= expectedSubscriptions,
        'Expected at least ' + expectedSubscriptions + ' subscriptions (commands + tree + status), got ' + contextMock.subscriptions.length);

    for (const command of contributed) {
        assert.ok(manifest.activationEvents.includes('onCommand:' + command),
            'Missing activation event for ' + command);
    }

    console.log('Extension command registration test passed. Commands: ' + contributed.length + '. Subscriptions: ' + contextMock.subscriptions.length + '.');
} finally {
    Module._load = originalLoad;
}
