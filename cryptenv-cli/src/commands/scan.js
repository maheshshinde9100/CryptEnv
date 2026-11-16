const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const RULES = [
  { name: 'AWS access key', re: /\bAKIA[0-9A-Z]{16}\b/g },
  { name: 'GitHub token', re: /\bgh[pousr]_[A-Za-z0-9_]{30,}\b/g },
  { name: 'Stripe secret', re: /\bsk_(?:live|test)_[A-Za-z0-9]{16,}\b/g },
  { name: 'Private key', re: /-----BEGIN(?: [A-Z]+)? PRIVATE KEY-----/g },
  { name: 'Generic credential assignment', re: /(?:password|secret|api[_-]?key|token)\s*[:=]\s*['"][^'"]{8,}['"]/gi },
];
const IGNORE = new Set(['node_modules', '.git', 'dist', 'build', 'target', 'coverage']);

function entropy(value) {
  const counts = new Map(); for (const char of value) counts.set(char, (counts.get(char) || 0) + 1);
  return [...counts.values()].reduce((sum, count) => { const p = count / value.length; return sum - p * Math.log2(p); }, 0);
}
function fingerprint(value) { return crypto.createHash('sha256').update(value).digest('hex').slice(0, 12); }
function ignoredLine(line) { return /cryptenv:ignore|gitleaks:allow/i.test(line); }
function scanText(text, file) {
  const findings = [];
  text.split(/\r?\n/).forEach((line, index) => {
    if (ignoredLine(line)) return;
    for (const rule of RULES) {
      rule.re.lastIndex = 0; let match;
      while ((match = rule.re.exec(line))) findings.push({ file, line: index + 1, rule: rule.name, fingerprint: fingerprint(match[0]) });
    }
    for (const match of line.matchAll(/\b[A-Za-z0-9+/_=-]{24,}\b/g)) {
      if (entropy(match[0]) >= 4.5) findings.push({ file, line: index + 1, rule: 'High-entropy value', fingerprint: fingerprint(match[0]) });
    }
  });
  return findings;
}
function collect(input, files = []) {
  const stat = fs.statSync(input);
  if (stat.isFile()) return files.concat(input);
  for (const entry of fs.readdirSync(input, { withFileTypes: true })) {
    if (!IGNORE.has(entry.name)) collect(path.join(input, entry.name), files);
  }
  return files;
}
function scanPath(input, opts = {}) {
  const findings = [];
  for (const file of collect(path.resolve(input))) {
    try { findings.push(...scanText(fs.readFileSync(file, 'utf8'), path.relative(process.cwd(), file))); } catch { /* binary/unreadable */ }
  }
  if (opts.json) console.log(JSON.stringify(findings, null, 2));
  else findings.forEach((f) => console.error(`${f.file}:${f.line}  ${f.rule}  [sha256:${f.fingerprint}]`));
  if (findings.length) process.exitCode = 2;
  else console.log('No potential secrets found.');
}
function installHook() {
  const hook = path.join(process.cwd(), '.git', 'hooks', 'pre-commit');
  if (!fs.existsSync(path.dirname(hook))) throw new Error('Not inside a Git repository');
  const body = '#!/bin/sh\ncryptenv scan --staged\n';
  fs.writeFileSync(hook, body, { mode: 0o755 });
  console.log(`Installed ${hook}`);
}
function staged(opts = {}) {
  const { execFileSync } = require('child_process');
  const names = execFileSync('git', ['diff', '--cached', '--name-only', '--diff-filter=ACM'], { encoding: 'utf8' }).trim().split(/\r?\n/).filter(Boolean);
  const findings = names.flatMap((file) => { try { return scanText(fs.readFileSync(file, 'utf8'), file); } catch { return []; } });
  if (opts.json) console.log(JSON.stringify(findings, null, 2)); else findings.forEach((f) => console.error(`${f.file}:${f.line}  ${f.rule}  [sha256:${f.fingerprint}]`));
  if (findings.length) process.exitCode = 2; else console.log('No potential secrets found in staged files.');
}
module.exports = { scanPath, staged, installHook };
