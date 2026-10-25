// Подписанные ссылки S3 (AWS Signature V4, подпись в строке запроса). B2 принимает их через свой S3-совместимый API.
// https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-query-string-auth.html

const encoder = new TextEncoder();

/**
 * Ссылка, по которой можно один раз сделать [method] над объектом [key] без ключей B2.
 * [headers] входят в подпись: клиент обязан прислать ровно их (так ограничиваем размер и тип файла).
 */
export async function presign({ method, host, region, accessKeyId, secretKey, key, expiresIn, headers = {}, now = new Date() }) {
  const amzDate = now.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
  const date = amzDate.slice(0, 8);
  const scope = `${date}/${region}/s3/aws4_request`;

  const allHeaders = { host, ...lowercaseKeys(headers) };
  const signedHeaders = Object.keys(allHeaders).sort().join(';');
  const query = {
    'X-Amz-Algorithm': 'AWS4-HMAC-SHA256',
    'X-Amz-Credential': `${accessKeyId}/${scope}`,
    'X-Amz-Date': amzDate,
    'X-Amz-Expires': String(expiresIn),
    'X-Amz-SignedHeaders': signedHeaders,
  };
  const canonicalQuery = Object.keys(query)
    .sort()
    .map((name) => `${encode(name)}=${encode(query[name])}`)
    .join('&');
  const canonicalHeaders = Object.keys(allHeaders)
    .sort()
    .map((name) => `${name}:${String(allHeaders[name]).trim()}\n`)
    .join('');
  const path = '/' + key.split('/').map(encode).join('/');
  const canonicalRequest = [method, path, canonicalQuery, canonicalHeaders, signedHeaders, 'UNSIGNED-PAYLOAD'].join('\n');

  const stringToSign = ['AWS4-HMAC-SHA256', amzDate, scope, await sha256Hex(canonicalRequest)].join('\n');
  let signingKey = encoder.encode(`AWS4${secretKey}`);
  for (const part of [date, region, 's3', 'aws4_request']) signingKey = await hmac(signingKey, part);
  const signature = toHex(await hmac(signingKey, stringToSign));

  return `https://${host}${path}?${canonicalQuery}&X-Amz-Signature=${signature}`;
}

/** URI-кодирование по правилам SigV4: всё, кроме A–Z a–z 0–9 - _ . ~ */
function encode(value) {
  return encodeURIComponent(value).replace(/[!'()*]/g, (c) => '%' + c.charCodeAt(0).toString(16).toUpperCase());
}

function lowercaseKeys(object) {
  return Object.fromEntries(Object.entries(object).map(([name, value]) => [name.toLowerCase(), value]));
}

async function hmac(key, message) {
  const cryptoKey = await crypto.subtle.importKey('raw', key, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  return new Uint8Array(await crypto.subtle.sign('HMAC', cryptoKey, encoder.encode(message)));
}

async function sha256Hex(message) {
  return toHex(new Uint8Array(await crypto.subtle.digest('SHA-256', encoder.encode(message))));
}

function toHex(bytes) {
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}
