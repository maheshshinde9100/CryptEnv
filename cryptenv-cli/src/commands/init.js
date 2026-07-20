const fs = require('fs');
const path = require('path');
const chalk = require('chalk');
const inquirer = require('inquirer');

async function init() {
  try {
    const configPath = path.join(process.cwd(), '.cryptenv.json');

    if (fs.existsSync(configPath)) {
      console.log(chalk.yellow('CryptEnv is already initialized in this directory'));
      return;
    }

    const answers = await inquirer.prompt([
      {
        type: 'input',
        name: 'apiUrl',
        message: 'CryptEnv API URL:',
        default: 'http://localhost:8080/api',
        validate: (input) => input.length > 0 || 'API URL is required'
      },
      {
        type: 'input',
        name: 'workspace',
        message: 'Workspace name:',
        default: path.basename(process.cwd()),
        validate: (input) => input.length > 0 || 'Workspace name is required'
      }
    ]);

    const config = {
      apiUrl: answers.apiUrl,
      workspace: answers.workspace,
      createdAt: new Date().toISOString()
    };

    fs.writeFileSync(configPath, JSON.stringify(config, null, 2));

    console.log(chalk.green('CryptEnv initialized successfully!'));
    console.log(chalk.blue(`Configuration saved to: ${configPath}`));
    console.log(chalk.yellow('Run: cryptenv login to authenticate'));
  } catch (error) {
    console.error(chalk.red('Error:', error.message));
  }
}

module.exports = init;
