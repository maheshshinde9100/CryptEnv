#!/usr/bin/env node

const { program } = require('commander');
const authCommands = require('../src/commands/auth');
const secretCommands = require('../src/commands/secrets');
const runCommand = require('../src/commands/run');
const profileCommand = require('../src/commands/profile');
const initCommand = require('../src/commands/init');

program
  .name('cryptenv')
  .description('CryptEnv CLI - Runtime secret injection for development environments')
  .version('1.0.0');

// Init command
program
  .command('init')
  .description('Initialize CryptEnv configuration')
  .action(initCommand);

// Authentication commands
program
  .command('login')
  .description('Authenticate with CryptEnv server')
  .action(authCommands.login);

program
  .command('logout')
  .description('Logout from CryptEnv')
  .action(authCommands.logout);

// Secret management commands
const secretsCmd = program
  .command('secrets')
  .description('Manage secrets');

secretsCmd
  .command('ls')
  .description('List all secrets')
  .action(secretCommands.list);

secretsCmd
  .command('get <key>')
  .description('Get a secret value')
  .action(secretCommands.get);

secretsCmd
  .command('set <key> <value>')
  .description('Set a secret value')
  .action(secretCommands.set);

secretsCmd
  .command('delete <key>')
  .description('Delete a secret')
  .action(secretCommands.delete);

// Run command
program
  .command('run <command...>')
  .description('Run a command with injected secrets')
  .action(runCommand);

// Profile command
program
  .command('profile')
  .description('Manage user profile')
  .action(profileCommand);

program.parse();
