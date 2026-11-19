#!/usr/bin/env node

const { program } = require('commander');
const authCommands = require('../src/commands/auth');
const secretCommands = require('../src/commands/secrets');
const runCommand = require('../src/commands/run');
const profileCommand = require('../src/commands/profile');
const initCommand = require('../src/commands/init');
const workspaceCommands = require('../src/commands/workspaces');
const projectCommands = require('../src/commands/projects');
const jitCommands = require('../src/commands/jit');
const contextCommands = require('../src/commands/context');
const { doctor, whoami, status } = require('../src/commands/doctor');
const { completion } = require('../src/commands/completion');
const scanCommands = require('../src/commands/scan');

program
  .name('cryptenv')
  .description('CryptEnv CLI — runtime secret injection & vault management')
  .version('1.4.0')
  .enablePositionalOptions();

program.command('init').description('Initialize CryptEnv configuration (.cryptenv.json)').action(initCommand);
program.command('register').description('Register a new CryptEnv account').action(authCommands.register);
program.command('login').description('Authenticate with CryptEnv (JWT)').action(authCommands.login);
program
  .command('logout')
  .option('--everywhere', 'Revoke all sessions for this account')
  .description('Logout from CryptEnv')
  .action(authCommands.logout);
program.command('profile').description('Show current user profile').action(profileCommand);
program.command('whoami').option('--json', 'JSON output').description('Show authenticated user').action(whoami);
program.command('status').option('--json', 'JSON output').description('Show auth + workspaces').action(status);
program.command('doctor').description('Check API health and local config').action(doctor);
program.command('scan [path]').option('--staged', 'Scan staged Git files').option('--json', 'JSON output').description('Scan source files for potential secret leaks').action((file = '.', opts) => opts.staged ? scanCommands.staged(opts) : scanCommands.scanPath(file, opts));
program.command('install-hook').description('Install a pre-commit secret scan hook').action(scanCommands.installHook);

// --- Context ------------------------------------------------------------------
program
  .command('use')
  .option('-w, --workspace <id>', 'Workspace id')
  .option('-e, --env <id>', 'Environment id')
  .option('-y, --yes', 'Auto-select when only one option exists')
  .description('Set active workspace/environment for this directory')
  .action(contextCommands.use);
program
  .command('envs')
  .option('-w, --workspace <id>', 'Workspace id')
  .option('--json', 'JSON output')
  .description('List environments for a workspace')
  .action(contextCommands.envs);

// --- Secrets ------------------------------------------------------------------
const secretsCmd = program.command('secrets').description('Manage secrets');
const envOpts = (cmd) =>
  cmd
    .option('-w, --workspace <id>', 'Workspace id')
    .option('-e, --env <id>', 'Environment id')
    .option('--json', 'JSON output');

envOpts(secretsCmd.command('ls').description('List secrets')).action((opts) => secretCommands.list(opts));
envOpts(secretsCmd.command('get <key>').description('Get decrypted secret')).action((key, opts) =>
  secretCommands.get(key, opts)
);
envOpts(secretsCmd.command('set <key> <value>').description('Create or update secret')).action((key, value, opts) =>
  secretCommands.set(key, value, opts)
);
envOpts(secretsCmd.command('delete <key>').description('Delete secret')).action((key, opts) =>
  secretCommands.delete(key, opts)
);
secretsCmd
  .command('import <file>')
  .requiredOption('-e, --env <id>', 'Environment id')
  .option('-f, --format <fmt>', 'ENV|JSON|YAML', 'ENV')
  .option('--json', 'JSON output')
  .description('Import secrets from file')
  .action((file, opts) => secretCommands.importSecrets(file, opts));
secretsCmd
  .command('export')
  .requiredOption('-e, --env <id>', 'Environment id')
  .option('-f, --format <fmt>', 'ENV|JSON|YAML', 'ENV')
  .option('-o, --out <file>', 'Output file')
  .description('Export secrets')
  .action((opts) => secretCommands.exportSecrets(opts));
secretsCmd
  .command('versions <key>')
  .option('--json', 'JSON output')
  .description('List secret versions')
  .action((key, opts) => secretCommands.versions(key, opts));
secretsCmd
  .command('rollback <key> <version>')
  .description('Rollback secret to a version')
  .action((key, version, opts) => secretCommands.rollback(key, version, opts));

// --- Secret lifecycle ---------------------------------------------------------
const lifecycle = (name, action, configure) => {
  const cmd = secretsCmd.command(name).description(`${action} a secret`);
  if (configure) configure(cmd);
  cmd.action((key, opts) => secretCommands.lifecycle(key, action, opts));
};
lifecycle('activate <key>', 'activate');
lifecycle('deactivate <key>', 'deactivate');
lifecycle('soft-delete <key>', 'soft-delete');
lifecycle('restore <key>', 'restore');
lifecycle('no-expire <key>', 'no-expire');
lifecycle('expire <key>', 'expire', (cmd) => cmd.requiredOption('--at <iso>', 'Expiry datetime (ISO-8601)'));
lifecycle('rotation <key>', 'rotation', (cmd) => cmd.requiredOption('--days <n>', 'Rotation interval in days'));
secretsCmd
  .command('auto-rotate <key>')
  .option('--off', 'Disable auto-rotation')
  .description('Enable/disable automatic rotation for a secret')
  .action((key, opts) => secretCommands.lifecycle(key, opts.off ? 'auto-rotate-off' : 'auto-rotate-on', opts));

// --- Workspaces ---------------------------------------------------------------
const wsCmd = program.command('workspaces').alias('ws').description('Manage workspaces');
wsCmd.command('ls').description('List workspaces').action(workspaceCommands.list);
wsCmd
  .command('create <name>')
  .option('-d, --description <text>', 'Description')
  .option('-k, --key <workspaceEncryptionKey>', 'Workspace encryption key (min 16 chars)')
  .description('Create a workspace')
  .action(workspaceCommands.create);

// --- Projects -----------------------------------------------------------------
const projectCmd = program.command('project').description('Manage projects in a workspace');
projectCmd
  .command('ls')
  .option('-w, --workspace <id>', 'Workspace id')
  .option('--json', 'JSON output')
  .description('List projects')
  .action(projectCommands.list);
projectCmd
  .command('create <name>')
  .option('-w, --workspace <id>', 'Workspace id')
  .option('-d, --description <text>', 'Description')
  .description('Create a project')
  .action(projectCommands.create);

// --- JIT access ---------------------------------------------------------------
const jitCmd = program.command('jit').description('Just-in-time access requests');
jitCmd
  .command('request <justification>')
  .option('-w, --workspace <id>', 'Workspace id')
  .option('-e, --env <id>', 'Environment id')
  .option('--hours <n>', 'Duration in hours (1-24)', '2')
  .description('Request JIT access to a protected environment')
  .action(jitCommands.request);
jitCmd.command('approve <id>').description('Approve a JIT request').action(jitCommands.approve);
jitCmd.command('deny <id>').description('Deny a JIT request').action(jitCommands.deny);
jitCmd
  .command('ls')
  .option('-w, --workspace <id>', 'Workspace id')
  .option('--json', 'JSON output')
  .description('List JIT requests for a workspace')
  .action(jitCommands.list);

// --- Runtime ------------------------------------------------------------------
program
  .command('run <command...>')
  .option('-w, --workspace <id>', 'Workspace id')
  .option('-e, --env <id>', 'Environment id')
  .passThroughOptions()
  .description('Run a command with injected secrets')
  .action((commandArgs, opts) => runCommand(commandArgs, opts));

// --- Completion ---------------------------------------------------------------
program
  .command('completion')
  .option('-s, --shell <shell>', 'Shell: bash|zsh|fish', 'bash')
  .description('Print a shell completion script')
  .action(completion);

program.parse();
