const chalk = require('chalk');

const pkg = require('../../package.json');

const accent = (value) => chalk.hex('#6D7CFF')(value);
const dim = (value) => chalk.hex('#B7B7B7')(value);
const faint = (value) => chalk.hex('#6B7280')(value);
const white = (value) => chalk.hex('#FFFFFF')(value);
const danger = (value) => chalk.hex('#FF6B6B')(value);
const okColor = (value) => chalk.hex('#4ADE80')(value);
const warnColor = (value) => chalk.hex('#FBBF24')(value);

const ANSI = /\u001b\[[0-9;]*m/g;

function width(value) {
  return String(value).replace(ANSI, '').length;
}

function pad(value, size, align = 'left') {
  const text = String(value);
  const gap = Math.max(0, size - width(text));
  if (align === 'right') return ' '.repeat(gap) + text;
  if (align === 'center') return ' '.repeat(Math.floor(gap / 2)) + text + ' '.repeat(Math.ceil(gap / 2));
  return text + ' '.repeat(gap);
}

function banner() {
  return [
    '',
    `  ${white('CryptEnv')} ${faint(`v${pkg.version}`)}`,
    `  ${dim('Secrets injected at runtime. Nothing written to disk.')}`,
    '',
  ].join('\n');
}

function heading(text) {
  return `\n${accent('›')} ${white(text)}\n`;
}

function success(message) {
  return `${okColor('✓')} ${message}`;
}

function info(message) {
  return `${accent('›')} ${message}`;
}

function warn(message) {
  return `${warnColor('!')} ${message}`;
}

function error(message) {
  return `${danger('✗')} ${message}`;
}

function badge(text, tone = 'neutral') {
  const map = {
    neutral: faint,
    accent,
    ok: okColor,
    warn: warnColor,
    danger,
  };
  return (map[tone] || faint)(` ${text} `);
}

function table(headers, rows, options = {}) {
  const all = [headers, ...rows].map((row) => row.map((cell) => (cell === null || cell === undefined ? '' : String(cell))));
  const widths = headers.map((_, index) => Math.max(...all.map((row) => width(row[index]))));
  const aligns = options.align || [];
  const line = (row, decorate) =>
    row
      .map((cell, index) => decorate(pad(cell, widths[index], aligns[index] || 'left'), index))
      .join(dim('   '));

  const output = [line(headers, (cell) => faint(cell.toUpperCase()))];
  output.push(faint(widths.map((size) => '─'.repeat(size)).join('   ')));
  rows.forEach((row) => output.push(line(row.map(String), (cell) => cell)));
  return output.join('\n');
}

function keyValue(pairs, indent = '  ') {
  const size = Math.max(...pairs.map(([key]) => width(key)));
  return pairs
    .map(([key, value]) => `${indent}${faint(pad(key, size))}   ${value}`)
    .join('\n');
}

function mask(value) {
  if (value === null || value === undefined || value === '') return '';
  const text = String(value);
  if (text.length <= 4) return '•'.repeat(12);
  return `${text.slice(0, 2)}${'•'.repeat(10)}${text.slice(-2)}`;
}

function timeAgo(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  const seconds = Math.round((Date.now() - date.getTime()) / 1000);
  if (seconds < 60) return 'just now';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  if (days < 30) return `${days}d ago`;
  return date.toLocaleDateString();
}

function status(active, label = 'active') {
  return active ? badge(label, 'ok') : badge('inactive', 'neutral');
}

function hint(message) {
  return faint(`  ${message}`);
}

function nextStep(message) {
  return `\n${dim('Next:')} ${accent(message)}`;
}

module.exports = {
  accent,
  dim,
  faint,
  white,
  danger,
  okColor,
  warnColor,
  banner,
  heading,
  success,
  info,
  warn,
  error,
  badge,
  table,
  keyValue,
  mask,
  timeAgo,
  status,
  hint,
  nextStep,
};