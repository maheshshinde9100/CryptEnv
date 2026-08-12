const CryptEnv = require('../src/index');

async function runExample() {
  console.log('--- CryptEnv SDK Example ---');
  console.log('This example requires a running CryptEnv backend and valid .env file.');
  console.log('Set: CRYPTENV_EMAIL, CRYPTENV_PASSWORD, CRYPTENV_WORKSPACE_ENCRYPTION_KEY, CRYPTENV_WORKSPACE_ID (optional)\n');

  try {
    const init = await CryptEnv.init();
    console.log('✓ Logged in:', init.user.email);
    console.log('  Workspaces:', init.workspaceCount);
    console.log('  Active workspace ID:', init.activeWorkspaceId);
    console.log('  Encrypted secrets loaded:', init.loadedSecretCount, '\n');

    const keys = await CryptEnv.listKeys();
    console.log('Available keys:', keys.length > 0 ? keys.join(', ') : '(none yet)');

    if (keys.length > 0 && process.env.CRYPTENV_WORKSPACE_ENCRYPTION_KEY) {
      const firstKey = keys[0];
      console.log(`\nDecrypting key '${firstKey}' using workspace key from .env...`);
      try {
        const value = CryptEnv.get(firstKey);
        console.log(`  Result: ${value.slice(0, 6)}${'*'.repeat(Math.max(0, value.length - 6))} (${value.length} chars)`);
      } catch (e) {
        console.log('  Decryption error (expected if key is a demo secret or workspace key is wrong):', e.message);
      }

      console.log('\nAll decrypted secrets:');
      const all = await CryptEnv.getAll();
      Object.entries(all).forEach(([k, v]) => {
        const preview = (v || '').toString().slice(0, 8).padEnd(8, '•');
        console.log(`  ${k.padEnd(24)} = ${preview}...`);
      });
    }

    const allWorkspaces = CryptEnv.listWorkspaces();
    console.log('\nWorkspaces you own:', allWorkspaces.map(w => `- ${w.name} (id=${w.id}) ${w.hasEncryptionKey ? '✓ hasKey' : '⚠ no key'}`).join('\n  '));

    console.log('\n✓ SDK functions correctly. Use in your app:');
    console.log('  const CryptEnv = require("@cryptenv/sdk");');
    console.log('  await CryptEnv.init();');
    console.log('  const dbPassword = CryptEnv.get("DB_PASSWORD");');
    console.log('  process.env.DB_PASSWORD = dbPassword;');
  } catch (e) {
    console.error('✗ Example failed:', e.message);
    console.error('\nTroubleshooting:');
    console.error('  1. Make sure backend is running: cd cryptenv-core ; mvn spring-boot:run');
    console.error('  2. Copy .env.example -> .env and set CRYPTENV_EMAIL / CRYPTENV_PASSWORD / CRYPTENV_WORKSPACE_ENCRYPTION_KEY');
    console.error('  3. Verify user registered at POST /api/auth/register and created a workspace with encryption key.');
  }
}

if (require.main === module) {
  runExample();
}

module.exports = { runExample };
