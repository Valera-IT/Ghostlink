import { describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  addDoc, collection, deleteField, doc, serverTimestamp, setDoc, Timestamp, updateDoc, writeBatch,
} from 'firebase/firestore';
import { account, seed } from './setup.js';

const CHAT = 'alice_bob';
const GROUP = 'g1';

const seedChats = () => seed(async (db) => {
  await setDoc(doc(db, `chats/${CHAT}`), { members: ['alice', 'bob'], updatedAt: Timestamp.now(), lastRead: {} });
  await setDoc(doc(db, `chats/${GROUP}`), {
    type: 'group', name: 'Митап', owner: 'alice', members: ['alice', 'bob', 'carol'], updatedAt: Timestamp.now(), lastRead: {},
  });
});

// Ключи в том виде, в каком их выдаёт Worker: {проект}/{chatId}/{uid}/{время}-{случайное}.jpg.
const key = (chatId, uid, n = 1) => `ghostlink-7ffc8/${chatId}/${uid}/mf2k3x-${String(n).repeat(16).slice(0, 16)}.jpg`;

const photo = (chatId, uid, extra = {}) => ({
  type: 'photo', key: key(chatId, uid, 1), thumbKey: key(chatId, uid, 2), size: 240_000, width: 1280, height: 960, ...extra,
});

const photoMessage = (chatId, uid, extra = {}) => ({ senderId: uid, sentAt: serverTimestamp(), attachment: photo(chatId, uid), ...extra });

const messages = (uid, chatId = CHAT) => collection(account(uid), `chats/${chatId}/messages`);

describe('фото в сообщениях', () => {
  test('фото без подписи и с подписью вместе с lastMessage', async () => {
    await seedChats();
    const db = account('alice');
    const batch = writeBatch(db);
    batch.set(doc(collection(db, `chats/${CHAT}/messages`)), photoMessage(CHAT, 'alice'));
    batch.update(doc(db, `chats/${CHAT}`), {
      lastMessage: { text: '', senderId: 'alice', sentAt: serverTimestamp(), photo: true }, updatedAt: serverTimestamp(),
    });
    await assertSucceeds(batch.commit());
    await assertSucceeds(addDoc(messages('alice'), photoMessage(CHAT, 'alice', { text: 'Закат с митапа' })));
  });

  test('фото в группе от любого участника', async () => {
    await seedChats();
    await assertSucceeds(addDoc(messages('carol', GROUP), photoMessage(GROUP, 'carol')));
  });

  // GitLive на iOS пишет целые как double. JS SDK целое число всегда пишет целым, поэтому double — через дробное значение.
  test('размеры принимаются и дробными числами', async () => {
    await seedChats();
    await assertSucceeds(addDoc(messages('alice'), photoMessage(CHAT, 'alice', { attachment: photo(CHAT, 'alice', { width: 1280.5, size: 1000.5 }) })));
  });

  test('ссылаться можно только на свои файлы этого чата', async () => {
    await seedChats();
    const foreign = (extra) => photoMessage(CHAT, 'alice', { attachment: photo(CHAT, 'alice', extra) });
    await assertFails(addDoc(messages('alice'), foreign({ key: key(CHAT, 'bob') })));
    await assertFails(addDoc(messages('alice'), foreign({ thumbKey: key(GROUP, 'alice', 2) })));
    await assertFails(addDoc(messages('alice'), foreign({ key: `ghostlink-7ffc8/${CHAT}/alice/../bob/x.jpg` })));
    await assertFails(addDoc(messages('alice'), foreign({ key: 'https://evil.example.com/x.jpg' })));
    await assertFails(addDoc(messages('alice'), foreign({ thumbKey: key(CHAT, 'alice', 1) })));
  });

  test('вложение — только фото с полным набором полей в пределах лимитов', async () => {
    await seedChats();
    const bad = (extra) => photoMessage(CHAT, 'alice', { attachment: photo(CHAT, 'alice', extra) });
    await assertFails(addDoc(messages('alice'), bad({ type: 'video' })));
    await assertFails(addDoc(messages('alice'), bad({ size: 1048577 })));
    await assertFails(addDoc(messages('alice'), bad({ size: 0 })));
    await assertFails(addDoc(messages('alice'), bad({ width: 5000 })));
    await assertFails(addDoc(messages('alice'), bad({ height: '960' })));
    await assertFails(addDoc(messages('alice'), bad({ url: 'https://b2/x' })));
    const { width, ...withoutWidth } = photo(CHAT, 'alice');
    await assertFails(addDoc(messages('alice'), photoMessage(CHAT, 'alice', { attachment: withoutWidth })));
  });

  test('без фото текст обязателен, подпись к фото — до 4000 символов', async () => {
    await seedChats();
    await assertFails(addDoc(messages('alice'), { senderId: 'alice', sentAt: serverTimestamp() }));
    await assertFails(addDoc(messages('alice'), photoMessage(CHAT, 'alice', { text: '' })));
    await assertFails(addDoc(messages('alice'), photoMessage(CHAT, 'alice', { text: 'x'.repeat(4001) })));
  });

  test('ответ на фото без подписи — с пустым превью и отметкой photo', async () => {
    await seedChats();
    const reply = (replyTo) => ({ senderId: 'alice', text: 'Красиво!', sentAt: serverTimestamp(), replyTo });
    await assertSucceeds(addDoc(messages('alice'), reply({ id: 'm1', senderId: 'bob', preview: '', photo: true })));
    await assertSucceeds(addDoc(messages('alice'), reply({ id: 'm1', senderId: 'bob', preview: 'Закат', photo: true })));
    await assertFails(addDoc(messages('alice'), reply({ id: 'm1', senderId: 'bob', preview: '', photo: false })));
    await assertFails(addDoc(messages('alice'), reply({ id: 'm1', senderId: 'bob', preview: '' })));
    await assertFails(addDoc(messages('alice'), reply({ id: 'm1', senderId: 'bob', preview: 'Закат', photo: 'yes' })));
  });

  test('при удалении у обоих фото пропадает из сообщения', async () => {
    await seedChats();
    await seed((db) => setDoc(doc(db, `chats/${CHAT}/messages/m1`), { senderId: 'alice', sentAt: Timestamp.now(), attachment: photo(CHAT, 'alice') }));
    const ref = doc(account('alice'), `chats/${CHAT}/messages/m1`);
    await assertFails(updateDoc(ref, { deletedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(ref, { attachment: deleteField(), deletedAt: serverTimestamp() }));
  });

  test('подпись к фото можно исправить, само фото — нет', async () => {
    await seedChats();
    await seed((db) => setDoc(doc(db, `chats/${CHAT}/messages/m1`), {
      senderId: 'alice', text: 'Закат', sentAt: Timestamp.now(), attachment: photo(CHAT, 'alice'),
    }));
    const ref = doc(account('alice'), `chats/${CHAT}/messages/m1`);
    await assertSucceeds(updateDoc(ref, { text: 'Рассвет', editedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref, { attachment: photo(CHAT, 'alice', { width: 10 }), editedAt: serverTimestamp() }));
  });
});
