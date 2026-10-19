import { describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { account, guest, seed } from './setup.js';

describe('users/{uid}', () => {
  test('профиль читает любой вошедший, гость — нет', async () => {
    await seed((db) => setDoc(doc(db, 'users/alice'), { name: 'Alice' }));
    await assertSucceeds(getDoc(doc(account('bob'), 'users/alice')));
    await assertFails(getDoc(doc(guest(), 'users/alice')));
  });

  test('свой профиль создаётся и меняется', async () => {
    const db = account('alice');
    await assertSucceeds(setDoc(doc(db, 'users/alice'), { name: 'Alice' }));
    await assertSucceeds(updateDoc(doc(db, 'users/alice'), { online: true, lastSeen: serverTimestamp() }));
  });

  test('чужой профиль менять нельзя', async () => {
    await seed((db) => setDoc(doc(db, 'users/alice'), { name: 'Alice' }));
    await assertFails(setDoc(doc(account('bob'), 'users/alice'), { name: 'Bob' }));
    await assertFails(updateDoc(doc(account('bob'), 'users/alice'), { online: true }));
  });

  test('имя от 1 до 40 символов', async () => {
    const ref = doc(account('alice'), 'users/alice');
    await assertFails(setDoc(ref, { name: '' }));
    await assertFails(setDoc(ref, { name: 'x'.repeat(41) }));
    await assertFails(setDoc(ref, { name: 42 }));
    await assertSucceeds(setDoc(ref, { name: 'x'.repeat(40) }));
  });

  test('лишние поля запрещены', async () => {
    await assertFails(setDoc(doc(account('alice'), 'users/alice'), { name: 'Alice', admin: true }));
  });

  test('профиль не удаляется', async () => {
    await seed((db) => setDoc(doc(db, 'users/alice'), { name: 'Alice' }));
    await assertFails(deleteDoc(doc(account('alice'), 'users/alice')));
  });
});

describe('users/{uid}/devices/{id}', () => {
  const device = () => ({ name: 'Pixel 8', platform: 'android', lastActiveAt: serverTimestamp() });

  test('аккаунт регистрирует, видит и удаляет свои устройства', async () => {
    const ref = doc(account('alice'), 'users/alice/devices/d1');
    await assertSucceeds(setDoc(ref, device()));
    await assertSucceeds(updateDoc(ref, { lastActiveAt: serverTimestamp() }));
    await assertSucceeds(getDoc(ref));
    await assertSucceeds(deleteDoc(ref));
  });

  test('чужие устройства не видны и не меняются', async () => {
    await seed((db) => setDoc(doc(db, 'users/alice/devices/d1'), { name: 'Pixel', platform: 'android' }));
    const ref = doc(account('bob'), 'users/alice/devices/d1');
    await assertFails(getDoc(ref));
    await assertFails(setDoc(ref, device()));
    await assertFails(deleteDoc(ref));
  });

  test('lastActiveAt — только серверное время', async () => {
    const ref = doc(account('alice'), 'users/alice/devices/d1');
    await assertFails(setDoc(ref, { ...device(), lastActiveAt: Timestamp.fromMillis(0) }));
  });

  test('ограничения на поля', async () => {
    const ref = doc(account('alice'), 'users/alice/devices/d1');
    await assertFails(setDoc(ref, { ...device(), name: 'x'.repeat(61) }));
    await assertFails(setDoc(ref, { ...device(), platform: '' }));
    await assertFails(setDoc(ref, { ...device(), pushToken: 'abc' }));
  });
});
