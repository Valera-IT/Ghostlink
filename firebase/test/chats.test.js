import { describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  addDoc, collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc, writeBatch,
} from 'firebase/firestore';
import { account, seed } from './setup.js';

const CHAT = 'alice_bob';

const seedChat = () => seed((db) => setDoc(doc(db, `chats/${CHAT}`), { members: ['alice', 'bob'], updatedAt: Timestamp.now(), lastRead: {} }));

const message = (senderId, text = 'Привет') => ({ senderId, text, sentAt: serverTimestamp() });

describe('chats/{a_b}', () => {
  test('участник создаёт чат пары', async () => {
    await assertSucceeds(setDoc(doc(account('alice'), `chats/${CHAT}`), {
      members: ['alice', 'bob'], updatedAt: serverTimestamp(), lastRead: {},
    }));
  });

  test('id чата — отсортированная пара участников', async () => {
    const db = account('alice');
    await assertFails(setDoc(doc(db, 'chats/bob_alice'), { members: ['bob', 'alice'] }));
    await assertFails(setDoc(doc(db, 'chats/whatever'), { members: ['alice', 'bob'] }));
  });

  test('чужой паре чат не создать', async () => {
    await assertFails(setDoc(doc(account('mallory'), `chats/${CHAT}`), { members: ['alice', 'bob'] }));
  });

  test('в чате ровно два участника', async () => {
    await assertFails(setDoc(doc(account('alice'), 'chats/alice_bob_carol'), { members: ['alice', 'bob', 'carol'] }));
  });

  test('проверка «есть ли чат» до создания разрешена', async () => {
    await assertSucceeds(getDoc(doc(account('alice'), `chats/${CHAT}`)));
  });

  test('чат читают только участники', async () => {
    await seedChat();
    await assertSucceeds(getDoc(doc(account('bob'), `chats/${CHAT}`)));
    await assertFails(getDoc(doc(account('mallory'), `chats/${CHAT}`)));
  });

  test('участник обновляет lastMessage, updatedAt и свой lastRead', async () => {
    await seedChat();
    const ref = doc(account('alice'), `chats/${CHAT}`);
    await assertSucceeds(updateDoc(ref, { lastMessage: { text: 'Привет', senderId: 'alice' }, updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(ref, { 'lastRead.alice': serverTimestamp() }));
  });

  test('чужой lastRead менять нельзя', async () => {
    await seedChat();
    await assertFails(updateDoc(doc(account('alice'), `chats/${CHAT}`), { 'lastRead.bob': serverTimestamp() }));
  });

  test('состав чата не меняется', async () => {
    await seedChat();
    await assertFails(updateDoc(doc(account('alice'), `chats/${CHAT}`), { members: ['alice', 'mallory'] }));
  });

  test('посторонний чат не обновит', async () => {
    await seedChat();
    await assertFails(updateDoc(doc(account('mallory'), `chats/${CHAT}`), { updatedAt: serverTimestamp() }));
  });
});

describe('chats/{a_b}/messages/{id}', () => {
  test('участник отправляет сообщение вместе с lastMessage одним batch', async () => {
    await seedChat();
    const db = account('alice');
    const batch = writeBatch(db);
    batch.set(doc(collection(db, `chats/${CHAT}/messages`)), message('alice'));
    batch.update(doc(db, `chats/${CHAT}`), {
      lastMessage: { text: 'Привет', senderId: 'alice', sentAt: serverTimestamp() }, updatedAt: serverTimestamp(),
    });
    await assertSucceeds(batch.commit());
  });

  test('сообщения читают только участники', async () => {
    await seedChat();
    await assertSucceeds(getDocs(collection(account('bob'), `chats/${CHAT}/messages`)));
    await assertFails(getDocs(collection(account('mallory'), `chats/${CHAT}/messages`)));
  });

  test('нельзя писать от чужого имени', async () => {
    await seedChat();
    await assertFails(addDoc(collection(account('alice'), `chats/${CHAT}/messages`), message('bob')));
  });

  test('посторонний не пишет в чат', async () => {
    await seedChat();
    await assertFails(addDoc(collection(account('mallory'), `chats/${CHAT}/messages`), message('mallory')));
  });

  test('текст от 1 до 4000 символов', async () => {
    await seedChat();
    const messages = collection(account('alice'), `chats/${CHAT}/messages`);
    await assertFails(addDoc(messages, message('alice', '')));
    await assertFails(addDoc(messages, message('alice', 'x'.repeat(4001))));
    await assertSucceeds(addDoc(messages, message('alice', 'x'.repeat(4000))));
  });

  test('sentAt — только серверное время', async () => {
    await seedChat();
    await assertFails(addDoc(collection(account('alice'), `chats/${CHAT}/messages`), { ...message('alice'), sentAt: Timestamp.now() }));
  });

  test('лишние поля запрещены', async () => {
    await seedChat();
    await assertFails(addDoc(collection(account('alice'), `chats/${CHAT}/messages`), { ...message('alice'), edited: true }));
  });

  test('сообщения неизменяемы', async () => {
    await seedChat();
    await seed((db) => setDoc(doc(db, `chats/${CHAT}/messages/m1`), { senderId: 'alice', text: 'Привет', sentAt: Timestamp.now() }));
    const ref = doc(account('alice'), `chats/${CHAT}/messages/m1`);
    await assertFails(updateDoc(ref, { text: 'Изменено' }));
    await assertFails(deleteDoc(ref));
  });
});
