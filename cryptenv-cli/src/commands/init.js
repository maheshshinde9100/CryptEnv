const fs = require('fs');
const path = require('path');
const inquirer = require('inquirer');
const axios = require('axios');
const { DEFAULT_API_URL } = require('../utils/config');
const ui = require('../utils/ui');

async function init(options = {}) {
  try {
    const configPath = path.join(process.cwd(), '.cryptenv.json');

    if (fs.existsSync(configPath)) {
      console.log(ui.warn('This directory is already initialized.'));
      console.log(ui.hint(`Edit ${configPath} or run cryptenv use to change context.`));
      return;
    }

    let apiUrl = options.apiUrl;
    if (!apiUrl) {
      const answer = await inquirer.prompt([
        {
          type: 'input',
          name: 'apiUrl',
          message: 'CryptEnv API URL:',
          default: DEFAULT_API_URL,
          validate: (input) => /^https?:\/\/.+/.test(input.trim()) || 'Enter an http(s) URL',
        },
      ]);
      apiUrl = answer.apiUrl;
    }
    apiUrl = apiUrl.trim().replace(/\/+$/, '');

    const config = { apiUrl, createdAt: new Date().toISOString() };
    fs.writeFileSync(configPath, JSON.stringify(config, null, 2) + '\n');

    console.log(ui.success(`Created ${configPath}`));
    console.log(ui.hint('This file contains no secrets and is safe to commit.'));

    const base = apiUrl.replace(/\/api\/?$/, '');
    try {
      await axios.get(`${base}/health`, { timeout: 8000 });
      console.log(ui.success('Backend reachable'));
    } catch {
      console.log(ui.warn('Could not reach the backend yet (free hosts may take ~30s to wake).'));
    }

    console.log(ui.nextStep('cryptenv login'));
    console.log(ui.hint('After signing in, run cryptenv use to pick a workspace and environment.'));
  } catch (error) {
    console.error(ui.error(error.message));
    process.exitCode = 1;
  }
}

module.exports = init;