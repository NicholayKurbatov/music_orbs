import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const root = process.cwd();
const files = [...new Set(execFileSync('git', ['ls-files', '-c', '-o', '--exclude-standard'], { encoding: 'utf8' }).split(/\r?\n/))].filter(f => f && fs.existsSync(f));
const errors = [];
let links = 0, sources = 0;
const slug = heading => heading.toLowerCase().replace(/[`*_]/g, '').replace(/[^\p{L}\p{N}\s-]/gu, '').trim().replace(/\s/g, '-');
for (const file of files) {
  if (file.startsWith('.agents/')) continue;
  if (file.endsWith('.md')) {
    const text = fs.readFileSync(file, 'utf8').replace(/```[\s\S]*?```/g, '');
    for (const match of text.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
      const target = match[1].replace(/^<|>$/g, '').split(/\s+"/)[0];
      if (/^[a-z][a-z+.-]*:/i.test(target)) continue;
      const [relative, fragment] = target.split('#');
      const destination = path.resolve(path.dirname(file), decodeURIComponent(relative || path.basename(file)));
      links++;
      if (!destination.startsWith(root + path.sep) && destination !== root) { errors.push(`${file}: outside project ${target}`); continue; }
      if (!fs.existsSync(destination)) { errors.push(`${file}: missing ${target}`); continue; }
      if (fragment && destination.endsWith('.md')) {
        const headings = [...fs.readFileSync(destination, 'utf8').matchAll(/^#{1,6}\s+(.+)$/gm)].map(m => slug(m[1]));
        if (!headings.includes(decodeURIComponent(fragment))) errors.push(`${file}: missing anchor ${target}`);
      }
    }
  }
  if (/\.(ts|kt)$/.test(file) && /\/(domain|application)\//.test(file) && !/\.test\.|\/src\/test\//.test(file)) {
    sources++;
    const domain = file.includes('/domain/');
    const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
    lines.forEach((line, index) => {
      if (!/^\s*import\b/.test(line)) return;
      const forbiddenLayer = domain ? /(application|infrastructure|presentation|bootstrap)[/.]/ : /(infrastructure|presentation|bootstrap)[/.]/;
      if (forbiddenLayer.test(line) || /\b(android\.|three\b|node:|java\.(io|nio)\.)/.test(line)) errors.push(`${file}:${index + 1}: forbidden import ${line.trim()}`);
    });
  }
}
const scope = fs.readFileSync('docs/scope.md', 'utf8');
const tests = fs.readFileSync('docs/tests.md', 'utf8');
const requirements = [...new Set([...scope.matchAll(/\|\s*((?:FR|NFR)-\d{2})\s*\|/g)].map(m => m[1]))];
for (const requirement of requirements) if (!tests.includes(requirement)) errors.push(`Requirement without scenario: ${requirement}`);
console.log(`${links} local links; ${sources} domain/application source files; ${requirements.length} requirement IDs checked.`);
for (const error of errors) console.error(error);
if (errors.length) process.exitCode = 1;
