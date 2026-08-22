const cryptenv = require('../src/index');

async function runExample() {
  console.log('--- CryptEnv Runtime SDK Example ---\n');
  console.log('Required .env variables:');
  console.log('  CRYPTENV_EMAIL, CRYPTENV_PASSWORD, CRYPTENV_MASTER_KEY');
  console.log('Optional: CRYPTENV_WORKSPACE_ID, CRYPTENV_API_URL\n');

  try {
    const init = await cryptenv.init();
    console.log('Authenticated:', init.user.email || '(api key)');
    console.log('Active workspace:', init.activeWorkspaceId);
    console.log('Secrets loaded:', init.loadedSecretCount, '\n');

    const keys = cryptenv.listKeys();
    console.log('Available keys:', keys.length > 0 ? keys.join(', ') : '(none)');

    if (keys.length > 0) {
      const firstKey = keys[0];
      const value = cryptenv.get(firstKey);
      const preview = value.slice(0, 4) + '*'.repeat(Math.min(8, Math.max(0, value.length - 4)));
      console.log(`\n${firstKey} = ${preview}`);

      console.log('\nAll secrets (masked):');
      const all = await cryptenv.getAll();
      for (const [k, v] of Object.entries(all)) {
        const p = v ? v.slice(0, 4) + '****' : '(empty)';
        console.log(`  ${k.padEnd(24)} = ${p}`);
      }
    }

    console.log('\n--- load() demo ---');
    const count = await cryptenv.load({ overwrite: false });
    console.log(`Injected ${count} secret(s) into process.env (skipped existing vars).`);

    console.log('\nUsage in your app:');
    console.log('  const cryptenv = require("@cryptenv/sdk");');
    console.log('  await cryptenv.init();');
    console.log('  const dbUrl = cryptenv.get("DATABASE_URL");');
    console.log('  // or: await cryptenv.load(); then process.env.DATABASE_URL');
  } catch (e) {
    console.error('Example failed:', e.message);
    console.error('\nTroubleshooting:');
    console.error('  1. Start backend: cd cryptenv-core && mvn spring-boot:run');
    console.error('  2. Set .env: CRYPTENV_EMAIL, CRYPTENV_PASSWORD, CRYPTENV_MASTER_KEY');
    console.error('  3. Register a user and create a workspace with an encryption key.');
    process.exitCode = 1;
  }
}

if (require.main === module) {
  runExample();
}

module.exports = { runExample };
