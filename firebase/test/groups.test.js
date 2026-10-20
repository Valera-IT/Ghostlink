import { describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  addDoc, arrayRemove, arrayUnion, collection, doc, getDoc, serverTimestamp, setDoc, Timestamp, updateDoc,
} from 'firebase/firestore';
import { account, seed } from './setup.js';

const GROUP = 'chats/g1';
const group = (extra = {}) => ({
  type: 'group', name: 'Митап', owner: 'alice', members: ['alice', 'bob', 'carol'], updatedAt: serverTimestamp(), lastRead: {}, ...extra,
});
const seedGroup = () => seed((db) => setDoc(doc(db, GROUP), {
  type: 'group', name: 'Митап', owner: 'alice', members: ['alice', 'bob', 'carol'], updatedAt: Timestamp.now(), lastRead: {},
}));

describe('групповые чаты', () => {
  test('админ создаёт группу с собой в составе', async () => {
    await assertSucceeds(setDoc(doc(account('alice'), GROUP), group()));
  });

  test('нельзя создать группу от чужого имени, без себя, пустую или больше 20', async () => {
    const db = account('alice');
    await assertFails(setDoc(doc(db, GROUP), group({ owner: 'bob' })));
    await assertFails(setDoc(doc(db, GROUP), group({ members: ['bob', 'carol'] })));
    await assertFails(setDoc(doc(db, GROUP), group({ members: ['alice'] })));
    await assertFails(setDoc(doc(db, GROUP), group({ members: ['alice', 'bob', 'bob'] })));
    const many = ['alice', ...Array.from({ length: 20 }, (_, i) => `u${i}`)];
    await assertFails(setDoc(doc(db, GROUP), group({ members: many })));
    await assertFails(setDoc(doc(db, GROUP), group({ name: '' })));
    await assertFails(setDoc(doc(db, GROUP), group({ extra: 1 })));
  });

  test('групповой чат не выдать за чат пары', async () => {
    await assertFails(setDoc(doc(account('alice'), 'chats/alice_bob'), {
      type: 'group', members: ['alice', 'bob'], updatedAt: serverTimestamp(), lastRead: {},
    }));
  });

  test('участники читают и пишут, посторонние — нет', async () => {
    await seedGroup();
    await assertSucceeds(getDoc(doc(account('carol'), GROUP)));
    await assertSucceeds(addDoc(collection(account('carol'), `${GROUP}/messages`), {
      senderId: 'carol', text: 'всем привет', sentAt: serverTimestamp(),
    }));
    await assertFails(getDoc(doc(account('eve'), GROUP)));
    await assertFails(addDoc(collection(account('eve'), `${GROUP}/messages`), {
      senderId: 'eve', text: 'я тут', sentAt: serverTimestamp(),
    }));
  });

  test('админ добавляет, удаляет участников и меняет название', async () => {
    await seedGroup();
    const ref = doc(account('alice'), GROUP);
    await assertSucceeds(updateDoc(ref, { members: arrayUnion('dave') }));
    await assertSucceeds(updateDoc(ref, { members: arrayRemove('bob') }));
    await assertSucceeds(updateDoc(ref, { name: 'Митап 2' }));
  });

  test('обычный участник не меняет состав и название', async () => {
    await seedGroup();
    const ref = doc(account('bob'), GROUP);
    await assertFails(updateDoc(ref, { members: arrayUnion('dave') }));
    await assertFails(updateDoc(ref, { members: arrayRemove('carol') }));
    await assertFails(updateDoc(ref, { name: 'Моя группа' }));
    await assertFails(updateDoc(ref, { owner: 'bob' }));
  });

  test('участник выходит сам', async () => {
    await seedGroup();
    await assertSucceeds(updateDoc(doc(account('bob'), GROUP), { members: arrayRemove('bob') }));
    await assertFails(getDoc(doc(account('bob'), GROUP)));
  });

  test('админ выходит, только передав права оставшемуся участнику', async () => {
    await seedGroup();
    const ref = doc(account('alice'), GROUP);
    await assertFails(updateDoc(ref, { members: arrayRemove('alice') }));
    await assertFails(updateDoc(ref, { members: arrayRemove('alice'), owner: 'eve' }));
    await assertSucceeds(updateDoc(ref, { members: arrayRemove('alice'), owner: 'bob' }));
  });

  test('отметки прочтения и «печатает…» в группе — только свои', async () => {
    await seedGroup();
    const ref = doc(account('carol'), GROUP);
    await assertSucceeds(updateDoc(ref, { 'lastRead.carol': serverTimestamp(), 'typing.carol': serverTimestamp() }));
    await assertFails(updateDoc(ref, { 'lastRead.bob': serverTimestamp() }));
  });
});
