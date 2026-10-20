import { describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  deleteField, doc, getDoc, serverTimestamp, setDoc, Timestamp,
} from 'firebase/firestore';
import { account, seed } from './setup.js';

const PREFS = 'users/alice/chatPrefs/alice_bob';

describe('users/{uid}/chatPrefs/{a_b}', () => {
  test('владелец закрепляет, глушит и удаляет чат', async () => {
    const ref = doc(account('alice'), PREFS);
    await assertSucceeds(setDoc(ref, { pinnedAt: serverTimestamp() }, { merge: true }));
    await assertSucceeds(setDoc(ref, { muted: true }, { merge: true }));
    await assertSucceeds(setDoc(ref, { hidden: true, clearedAt: serverTimestamp(), pinnedAt: deleteField() }, { merge: true }));
    await assertSucceeds(setDoc(ref, { hidden: false }, { merge: true }));
  });

  test('чужие настройки не прочитать и не изменить', async () => {
    await seed((db) => setDoc(doc(db, PREFS), { muted: true }));
    const bob = account('bob');
    await assertFails(getDoc(doc(bob, PREFS)));
    await assertFails(setDoc(doc(bob, PREFS), { hidden: true }, { merge: true }));
  });

  test('время закрепления и удаления — только серверное', async () => {
    const ref = doc(account('alice'), PREFS);
    await assertFails(setDoc(ref, { pinnedAt: Timestamp.now() }));
    await assertFails(setDoc(ref, { hidden: true, clearedAt: Timestamp.fromMillis(0) }));
  });

  test('после удаления можно менять другие поля, не трогая clearedAt', async () => {
    await seed((db) => setDoc(doc(db, PREFS), { hidden: true, clearedAt: Timestamp.fromMillis(1000) }));
    await assertSucceeds(setDoc(doc(account('alice'), PREFS), { muted: true }, { merge: true }));
    await assertFails(setDoc(doc(account('alice'), PREFS), { clearedAt: Timestamp.fromMillis(0) }, { merge: true }));
  });

  test('только известные поля нужных типов', async () => {
    const ref = doc(account('alice'), PREFS);
    await assertFails(setDoc(ref, { muted: 'yes' }));
    await assertFails(setDoc(ref, { hidden: 1 }));
    await assertFails(setDoc(ref, { archived: true }));
  });
});
