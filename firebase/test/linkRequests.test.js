import { describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { Bytes, collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { account, anonymous, seed } from './setup.js';

const minutes = (m) => Timestamp.fromMillis(Date.now() + m * 60_000);

const request = (owner, expiresInMinutes = 2) => ({
  owner,
  deviceName: 'MacBook',
  platform: 'macos',
  createdAt: serverTimestamp(),
  expiresAt: minutes(expiresInMinutes),
});

const payload = (size = 109) => Bytes.fromUint8Array(new Uint8Array(size).fill(7));

/** Живая заявка нового устройства (анонимный uid `newDevice`). */
const seedRequest = (data = {}) =>
  seed((db) => setDoc(doc(db, 'linkRequests/r1'), {
    owner: 'newDevice', deviceName: 'MacBook', platform: 'macos', createdAt: Timestamp.now(), expiresAt: minutes(2), ...data,
  }));

describe('linkRequests/{id}: создание', () => {
  test('новое устройство создаёт заявку под анонимным входом', async () => {
    await assertSucceeds(setDoc(doc(anonymous('newDevice'), 'linkRequests/r1'), request('newDevice')));
  });

  test('владелец — только сам создающий', async () => {
    await assertFails(setDoc(doc(anonymous('newDevice'), 'linkRequests/r1'), request('someoneElse')));
  });

  test('срок жизни не больше 3 минут и в будущем', async () => {
    const ref = doc(anonymous('newDevice'), 'linkRequests/r1');
    await assertFails(setDoc(ref, request('newDevice', 4)));
    await assertFails(setDoc(ref, request('newDevice', -1)));
  });

  test('createdAt — только серверное время', async () => {
    await assertFails(setDoc(doc(anonymous('newDevice'), 'linkRequests/r1'), { ...request('newDevice'), createdAt: Timestamp.now() }));
  });

  test('payload нельзя положить при создании', async () => {
    await assertFails(setDoc(doc(anonymous('newDevice'), 'linkRequests/r1'), { ...request('newDevice'), payload: payload() }));
  });
});

describe('linkRequests/{id}: чтение', () => {
  test('по id читает любой вошедший, список — никто', async () => {
    await seedRequest();
    await assertSucceeds(getDoc(doc(account('phone'), 'linkRequests/r1')));
    await assertFails(getDocs(collection(account('phone'), 'linkRequests')));
  });
});

describe('linkRequests/{id}: подтверждение', () => {
  const approve = (db) => updateDoc(doc(db, 'linkRequests/r1'), { payload: payload(), approvedBy: 'phone' });

  test('настоящий аккаунт подтверждает живую заявку', async () => {
    await seedRequest();
    await assertSucceeds(approve(account('phone')));
  });

  test('анонимный аккаунт подтвердить не может', async () => {
    await seedRequest();
    await assertFails(updateDoc(doc(anonymous('other'), 'linkRequests/r1'), { payload: payload(), approvedBy: 'other' }));
  });

  test('владелец заявки не подтверждает её сам', async () => {
    await seedRequest({ owner: 'phone' });
    await assertFails(approve(account('phone')));
  });

  test('подтвердить можно только один раз', async () => {
    await seedRequest({ payload: payload(), approvedBy: 'phone' });
    await assertFails(updateDoc(doc(account('attacker'), 'linkRequests/r1'), { payload: payload(), approvedBy: 'attacker' }));
  });

  test('просроченную заявку подтвердить нельзя', async () => {
    await seedRequest({ expiresAt: minutes(-1) });
    await assertFails(approve(account('phone')));
  });

  test('approvedBy — только свой uid', async () => {
    await seedRequest();
    await assertFails(updateDoc(doc(account('phone'), 'linkRequests/r1'), { payload: payload(), approvedBy: 'someoneElse' }));
  });

  test('payload — байты не длиннее 512', async () => {
    await seedRequest();
    const ref = doc(account('phone'), 'linkRequests/r1');
    await assertFails(updateDoc(ref, { payload: 'not bytes', approvedBy: 'phone' }));
    await assertFails(updateDoc(ref, { payload: payload(513), approvedBy: 'phone' }));
  });

  test('остальные поля менять нельзя', async () => {
    await seedRequest();
    await assertFails(updateDoc(doc(account('phone'), 'linkRequests/r1'), { payload: payload(), approvedBy: 'phone', owner: 'phone' }));
  });
});

describe('linkRequests/{id}: удаление', () => {
  test('удаляет только владелец', async () => {
    await seedRequest();
    await assertFails(deleteDoc(doc(account('phone'), 'linkRequests/r1')));
    await assertSucceeds(deleteDoc(doc(anonymous('newDevice'), 'linkRequests/r1')));
  });
});
