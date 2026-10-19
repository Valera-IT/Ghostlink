import { readFileSync } from 'node:fs';
import { after, afterEach, before } from 'node:test';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { setLogLevel } from 'firebase/firestore';

// Отказы правил — ожидаемый результат половины тестов, SDK не должен их логировать.
setLogLevel('silent');

export let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-ghostlink',
    firestore: { rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8') },
  });
});

afterEach(() => env.clearFirestore());

after(() => env.cleanup());

/** Настоящий аккаунт: вход по ключу (Email/Password). */
export const account = (uid) => env.authenticatedContext(uid, { firebase: { sign_in_provider: 'password' } }).firestore();

/** Временный анонимный аккаунт нового устройства. */
export const anonymous = (uid) => env.authenticatedContext(uid, { firebase: { sign_in_provider: 'anonymous' } }).firestore();

export const guest = () => env.unauthenticatedContext().firestore();

/** Подготовить данные в обход правил. */
export const seed = (fn) => env.withSecurityRulesDisabled((ctx) => fn(ctx.firestore()));
