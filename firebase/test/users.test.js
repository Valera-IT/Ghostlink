import { describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { Bytes, deleteDoc, deleteField, doc, getDoc, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
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

  test('версия фото профиля — положительное целое, её можно убрать', async () => {
    const ref = doc(account('alice'), 'users/alice');
    await assertSucceeds(setDoc(ref, { name: 'Alice', avatarVersion: 1761300000000 }));
    await assertFails(updateDoc(ref, { avatarVersion: 0 }));
    await assertFails(updateDoc(ref, { avatarVersion: 'v2' }));
    await assertFails(updateDoc(ref, { avatarVersion: 1.5 }));
    await assertSucceeds(updateDoc(ref, { avatarVersion: deleteField() }));
  });

  test('профиль не удаляется', async () => {
    await seed((db) => setDoc(doc(db, 'users/alice'), { name: 'Alice' }));
    await assertFails(deleteDoc(doc(account('alice'), 'users/alice')));
  });
});

describe('avatars/{uid}', () => {
  const photo = (size = 1000) => ({ data: Bytes.fromUint8Array(new Uint8Array(size).fill(7)), version: 1 });

  test('своё фото ставится, меняется и удаляется', async () => {
    const ref = doc(account('alice'), 'avatars/alice');
    await assertSucceeds(setDoc(ref, photo()));
    await assertSucceeds(setDoc(ref, { ...photo(2000), version: 2 }));
    await assertSucceeds(deleteDoc(ref));
  });

  test('фото видно любому вошедшему, гостю — нет', async () => {
    await seed((db) => setDoc(doc(db, 'avatars/alice'), photo()));
    await assertSucceeds(getDoc(doc(account('bob'), 'avatars/alice')));
    await assertFails(getDoc(doc(guest(), 'avatars/alice')));
  });

  test('чужое фото не меняется и не удаляется', async () => {
    await seed((db) => setDoc(doc(db, 'avatars/alice'), photo()));
    const ref = doc(account('bob'), 'avatars/alice');
    await assertFails(setDoc(ref, photo()));
    await assertFails(deleteDoc(ref));
  });

  test('не больше 64 КБ и только bytes', async () => {
    const ref = doc(account('alice'), 'avatars/alice');
    await assertSucceeds(setDoc(ref, photo(65536)));
    await assertFails(setDoc(ref, photo(65537)));
    await assertFails(setDoc(ref, photo(0)));
    await assertFails(setDoc(ref, { data: 'base64…', version: 1 }));
  });

  test('версия обязательна, лишние поля запрещены', async () => {
    const ref = doc(account('alice'), 'avatars/alice');
    await assertFails(setDoc(ref, { data: photo().data }));
    await assertFails(setDoc(ref, { ...photo(), version: 0 }));
    await assertFails(setDoc(ref, { ...photo(), url: 'https://example.com/a.jpg' }));
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
    await assertFails(setDoc(ref, { ...device(), pushToken: '' }));
    await assertFails(setDoc(ref, { ...device(), pushToken: 'x'.repeat(513) }));
    await assertFails(setDoc(ref, { ...device(), pushToken: 42 }));
    await assertFails(setDoc(ref, { ...device(), token: 'abc' }));
  });

  test('push-токен пишет только сам аккаунт', async () => {
    const ref = doc(account('alice'), 'users/alice/devices/d1');
    await assertSucceeds(setDoc(ref, { ...device(), pushToken: 'fcm-token' }));
    await assertSucceeds(updateDoc(ref, { lastActiveAt: serverTimestamp(), pushToken: 'new-token' }));
    await assertFails(setDoc(doc(account('bob'), 'users/alice/devices/d2'), { ...device(), pushToken: 'fcm-token' }));
  });
});
