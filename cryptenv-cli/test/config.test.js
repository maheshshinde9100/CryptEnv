const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');

const config = require('../src/utils/config');

function withTempDir(fn) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'cryptenv-'));
  const cwd = process.cwd();
  process.chdir(dir);
  try {
    fn(dir);
  } finally {
    process.chdir(cwd);
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

test('resolveContext reads .cryptenv.json when no flags or env are set', () => {
  withTempDir((dir) => {
    fs.writeFileSync(
      path.join(dir, '.cryptenv.json'),
      JSON.stringify({ workspaceId: 11, environmentId: 22 })
    );
    delete process.env.CRYPTENV_WORKSPACE;
    delete process.env.CRYPTENV_ENV;

    const ctx = config.resolveContext({});
    assert.strictEqual(ctx.workspaceId, 11);
    assert.strictEqual(ctx.environmentId, 22);
    assert.strictEqual(ctx.json, false);
  });
});

test('resolveContext prefers flags over env and file', () => {
  withTempDir((dir) => {
    fs.writeFileSync(
      path.join(dir, '.cryptenv.json'),
      JSON.stringify({ workspaceId: 11, environmentId: 22 })
    );
    process.env.CRYPTENV_WORKSPACE = '55';
    process.env.CRYPTENV_ENV = '66';
    try {
      const fromEnv = config.resolveContext({});
      assert.strictEqual(fromEnv.workspaceId, '55');
      assert.strictEqual(fromEnv.environmentId, '66');

      const fromFlags = config.resolveContext({ workspace: '7', env: '9', json: true });
      assert.strictEqual(fromFlags.workspaceId, '7');
      assert.strictEqual(fromFlags.environmentId, '9');
      assert.strictEqual(fromFlags.json, true);
    } finally {
      delete process.env.CRYPTENV_WORKSPACE;
      delete process.env.CRYPTENV_ENV;
    }
  });
});

test('saveCryptenvJson merges with existing config and stays valid JSON', () => {
  withTempDir((dir) => {
    fs.writeFileSync(
      path.join(dir, '.cryptenv.json'),
      JSON.stringify({ apiUrl: 'http://localhost:8080/api', workspace: 'demo' })
    );

    const merged = config.saveCryptenvJson({ workspaceId: 3 });
    assert.strictEqual(merged.apiUrl, 'http://localhost:8080/api');
    assert.strictEqual(merged.workspace, 'demo');
    assert.strictEqual(merged.workspaceId, 3);

    const reloaded = config.loadCryptenvJson();
    assert.deepStrictEqual(reloaded, merged);
  });
});

test('getApiUrl honours CRYPTENV_API_URL override', () => {
  withTempDir(() => {
    process.env.CRYPTENV_API_URL = 'https://example.test/api';
    try {
      assert.strictEqual(config.getApiUrl(), 'https://example.test/api');
    } finally {
      delete process.env.CRYPTENV_API_URL;
    }
    assert.strictEqual(config.getApiUrl(), config.DEFAULT_API_URL);
  });
});
