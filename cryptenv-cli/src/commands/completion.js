const chalk = require('chalk');

const COMMANDS = [
  'init',
  'register',
  'login',
  'verify-email',
  'forgot-password',
  'logout',
  'profile',
  'whoami',
  'status',
  'doctor',
  'scan',
  'install-hook',
  'use',
  'envs',
  'secrets',
  'workspaces',
  'ws',
  'project',
  'jit',
  'run',
  'completion',
];

const SUBCOMMANDS = {
  secrets: [
    'ls',
    'get',
    'set',
    'delete',
    'import',
    'export',
    'versions',
    'rollback',
    'rotate',
    'activate',
    'deactivate',
    'soft-delete',
    'restore',
    'expire',
    'no-expire',
    'rotation',
    'auto-rotate',
  ],
  workspaces: ['ls', 'create'],
  ws: ['ls', 'create'],
  project: ['ls', 'create'],
  jit: ['request', 'approve', 'deny', 'ls'],
  completion: ['bash', 'zsh', 'fish'],
};

function bashScript() {
  return `# cryptenv bash completion
_cryptenv() {
  local cur prev commands
  COMPREPLY=()
  cur="\${COMP_WORDS[COMP_CWORD]}"
  prev="\${COMP_WORDS[COMP_CWORD-1]}"
  commands="${COMMANDS.join(' ')}"

  case "\${COMP_WORDS[1]}" in
${Object.entries(SUBCOMMANDS)
  .map(([cmd, subs]) => `    ${cmd}) COMPREPLY=( $(compgen -W "${subs.join(' ')}" -- "$cur") ); return 0 ;;`)
  .join('\n')}
  esac

  if [ "$COMP_CWORD" -eq 1 ]; then
    COMPREPLY=( $(compgen -W "$commands" -- "$cur") )
  fi
  return 0
}
complete -F _cryptenv cryptenv
`;
}

function zshScript() {
  return `#compdef cryptenv
# cryptenv zsh completion
_cryptenv() {
  local -a commands
  commands=(${COMMANDS.map((c) => `'${c}'`).join(' ')})
  if (( CURRENT == 2 )); then
    _describe 'command' commands
  fi
}
compdef _cryptenv cryptenv
`;
}

function fishScript() {
  const lines = COMMANDS.map(
    (c) => `complete -c cryptenv -n "__fish_use_subcommand" -a "${c}"`
  );
  for (const [cmd, subs] of Object.entries(SUBCOMMANDS)) {
    for (const s of subs) {
      lines.push(`complete -c cryptenv -n "__fish_seen_subcommand_from ${cmd}" -a "${s}"`);
    }
  }
  return `# cryptenv fish completion\n${lines.join('\n')}\n`;
}

function completion(opts = {}) {
  const shell = (opts.shell || 'bash').toLowerCase();
  switch (shell) {
    case 'bash':
      process.stdout.write(bashScript());
      break;
    case 'zsh':
      process.stdout.write(zshScript());
      break;
    case 'fish':
      process.stdout.write(fishScript());
      break;
    default:
      console.error(chalk.red(`Unsupported shell: ${shell} (use bash, zsh or fish)`));
      process.exit(1);
  }
  console.error(chalk.gray(`# Add to your shell profile, e.g.: eval "$(cryptenv completion --shell ${shell})"`));
}

module.exports = { completion };
