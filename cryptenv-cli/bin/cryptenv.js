#!/usr/bin/env node

const { program } = require('commander');
const authCommands = require('../src/commands/auth');
const secretCommands = require('../src/commands/secrets');
const runCommand = require('../src/commands/run');
const profileCommand = require('../src/commands/profile');
const initCommand = require('../src/commands/init');
const workspaceCommands = require('../src/commands/workspaces');

program
  .name('cryptenv')
  .description('CryptEnv CLI — runtime secret injection & vault management for the CryptEnv Spring Boot API')
  .version('1.3.0')
  .enablePositionalOptions();

program.command('init').description('Initialize CryptEnv configuration (.cryptenv.json)').action(initCommand);

program.command('register').description('Register a new CryptEnv account').action(authCommands.register);
program.command('login').description('Authenticate with CryptEnv (JWT)').action(authCommands.login);
program.command('logout').description('Logout from CryptEnv').action(authCommands.logout);
program.command('profile').description('Show current user profile').action(profileCommand);

const secretsCmd = program.command('secrets').description('Manage secrets');
secretsCmd.command('ls').description('List all secrets (values masked)').action(secretCommands.list);
secretsCmd.command('get <key>').description('Get a decrypted secret value').action(secretCommands.get);
secretsCmd.command('set <key> <value>').description('Create a secret (server encrypts)').action(secretCommands.set);
secretsCmd.command('delete <key>').description('Delete a secret').action(secretCommands.delete);

const wsCmd = program.command('workspaces').alias('ws').description('Manage workspaces');
wsCmd.command('ls').description('List workspaces').action(workspaceCommands.list);
wsCmd
  .command('create <name>')
  .option('-d, --description <text>', 'Description')
  .option('-k, --key <workspaceEncryptionKey>', 'Workspace encryption key (min 16 chars)')
  .description('Create a workspace (optionally with encryption key)')
  .action(workspaceCommands.create);

program
  .command('run <command...>')
  .passThroughOptions()
  .description('Run a command with injected secrets')
  .action(runCommand);

program.parse();
