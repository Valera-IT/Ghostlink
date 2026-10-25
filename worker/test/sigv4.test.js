import assert from 'node:assert/strict';
import { test } from 'node:test';
import { presign } from '../src/sigv4.js';

// Пример из документации AWS: https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-query-string-auth.html
test('presigned GET matches the AWS documentation example', async () => {
  const url = await presign({
    method: 'GET',
    host: 'examplebucket.s3.amazonaws.com',
    region: 'us-east-1',
    accessKeyId: 'AKIAIOSFODNN7EXAMPLE',
    secretKey: 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY',
    key: 'test.txt',
    expiresIn: 86400,
    now: new Date('2013-05-24T00:00:00Z'),
  });
  assert.equal(
    url,
    'https://examplebucket.s3.amazonaws.com/test.txt' +
      '?X-Amz-Algorithm=AWS4-HMAC-SHA256' +
      '&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request' +
      '&X-Amz-Date=20130524T000000Z&X-Amz-Expires=86400&X-Amz-SignedHeaders=host' +
      '&X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404',
  );
});

test('extra headers are signed', async () => {
  const url = await presign({
    method: 'PUT',
    host: 'bucket.s3.eu-central-003.backblazeb2.com',
    region: 'eu-central-003',
    accessKeyId: 'id',
    secretKey: 'secret',
    key: 'p/chat/uid/abc-0123456789abcdef.jpg',
    expiresIn: 600,
    headers: { 'Content-Type': 'image/jpeg', 'Content-Length': '42' },
  });
  assert.match(url, /^https:\/\/bucket\.s3\.eu-central-003\.backblazeb2\.com\/p\/chat\/uid\/abc-0123456789abcdef\.jpg\?/);
  assert.match(url, /X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost/);
});
