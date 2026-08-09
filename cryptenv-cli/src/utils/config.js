const fs = require('fs');
const path = require('path');

function getApiUrl() {
  // 1. Check environment variable first (highest priority)
  if (process.env.CRYPTENV_API_URL) {
    return process.env.CRYPTENV_API_URL;
  }

  // 2. Check local .cryptenv.json in the current working directory
  const configPath = path.join(process.cwd(), '.cryptenv.json');
  if (fs.existsSync(configPath)) {
    try {
      const configData = fs.readFileSync(configPath, 'utf8');
      const config = JSON.parse(configData);
      if (config.apiUrl) {
        return config.apiUrl;
      }
    } catch (err) {
      // Ignore parse errors, fallback to default
    }
  }

  // 3. Fallback to default production URL
  return 'https://cryptenv-backend.onrender.com/api';
}

module.exports = {
  getApiUrl
};
