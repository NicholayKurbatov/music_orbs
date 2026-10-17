import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import Ajv2020 from 'ajv/dist/2020.js';
import { decodeEnvelope, encodeEnvelope, migrateSession, validateSession } from './Wire';
import { DEFAULT_SETTINGS, validateSettings } from '../sculpture/domain/ProfileLimits';

const fixtures = JSON.parse(fs.readFileSync(new URL('../../contracts/fixtures/golden.json', import.meta.url), 'utf8')) as { name: string; kind: string; valid: boolean; value: unknown }[];
const schema = JSON.parse(fs.readFileSync(new URL('../../contracts/wire-v1.schema.json', import.meta.url), 'utf8'));
const ajv = new Ajv2020({ strict: false });
const schemaValid = ajv.compile(schema);
for (const fixture of fixtures) test(`CT-04 Kotlin/TS/schema fixture: ${fixture.name}`, () => {
  assert.equal(schemaValid(fixture.value), fixture.valid, JSON.stringify(schemaValid.errors));
  const run = () => fixture.kind === 'session' ? validateSession(fixture.value) : decodeEnvelope(JSON.stringify(fixture.value));
  if (fixture.valid) {
    assert.doesNotThrow(run);
    if (fixture.kind === 'envelope') assert.deepEqual(decodeEnvelope(encodeEnvelope(decodeEnvelope(JSON.stringify(fixture.value)))), fixture.value);
  } else assert.throws(run);
});
test('v1 colorHex migration preserves shape and revision, unknown versions reject', () => {
  const legacy = JSON.parse(fs.readFileSync(new URL('../../contracts/fixtures/session-v1.json', import.meta.url), 'utf8'));
  const upgraded = migrateSession(legacy);
  assert.equal(upgraded.schemaVersion, 2); assert.equal(upgraded.revision, legacy.revision);
  assert.deepEqual(upgraded.shapes, legacy.shapes); assert.equal(upgraded.settings.paletteId, 'custom');
  assert.deepEqual(upgraded.settings.paletteStops, [{ offset: 0, colorHex: '#FFFFFF' }, { offset: 1, colorHex: '#FFFFFF' }]);
  assert.equal(legacy.schemaVersion, 1); assert.throws(() => migrateSession({ ...legacy, schemaVersion: 3 }));
});
test('payload bytes, non-finite numbers and palette ordering rejected before mutation', () => {
  assert.throws(() => decodeEnvelope(' '.repeat(262145)));
  assert.throws(() => validateSettings({ ...DEFAULT_SETTINGS, speed: NaN }));
  assert.throws(() => validateSettings({ ...DEFAULT_SETTINGS, shapeId: ['orb'] }));
  assert.throws(() => validateSettings({ ...DEFAULT_SETTINGS, paletteStops: [{ offset: 0, colorHex: '#FFFFFF' }, { offset: 0, colorHex: '#FFFFFF' }] }));
  assert.throws(() => validateSession({ schemaVersion: 2, revision: 0, settings: DEFAULT_SETTINGS, shapes: [] }));
});
