import { defineConfig } from 'vite';
import { readFileSync } from 'node:fs';

export default defineConfig({
  base: './',
  plugins: [{
    name: 'music-orbs-distribution-notices',
    generateBundle() {
      const source = ['Three-LICENSE', 'Thinking-Orbs-LICENSE'].map(name => `${name}\n\n${readFileSync(new URL(`../third_party/${name}`, import.meta.url), 'utf8')}`).join('\n\n');
      this.emitFile({ type: 'asset', fileName: 'THIRD_PARTY_NOTICES.txt', source });
    },
  }],
  build: { target: 'es2022', outDir: 'dist', emptyOutDir: true },
});
