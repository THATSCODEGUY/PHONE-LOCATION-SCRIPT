import handler from '../server/api/report.js';

const sleep = (ms) => new Promise(r => setTimeout(r, ms));

function mockRes() {
  return {
    code: 0, body: null,
    setHeader() {}, 
    status(c) { this.code = c; return this; },
    json(j) { this.body = j; return this; },
  };
}

function mockReq(method, headers, body) {
  return { method, headers, body, query: {} };
}

let pass = 0, fail = 0;
function check(name, cond, extra = '') {
  if (cond) { pass++; console.log(`  PASS  ${name}`); }
  else { fail++; console.log(`  FAIL  ${name} ${extra}`); }
}

// stub supabase fetch
let lastFetch = null;
globalThis.fetch = async (url, opts) => {
  lastFetch = { url, opts };
  return { ok: true, status: 201, text: async () => '' };
};

process.env.DEVICE_KEY = 'dk_123456';
process.env.ACCESS_TOKEN = 'at_654321';
process.env.SUPABASE_URL = 'https://fake.supabase.co';
process.env.SUPABASE_SERVICE_KEY = 'svc_fake';

const goodBody = { lat: 39.9042, lng: 116.4074, accuracy: 12, battery: 80, provider: 'gps', device: 'test' };

console.log('report.js tests:');

// T1 wrong method
let r = mockRes();
await handler(mockReq('GET', {}), r);
check('GET rejected 405', r.code === 405);

// T2 no key
r = mockRes();
await handler(mockReq('POST', {}, goodBody), r);
check('missing key 401', r.code === 401);

// T3 wrong key
r = mockRes();
await handler(mockReq('POST', { 'x-device-key': 'wrong' }, goodBody), r);
check('wrong key 401', r.code === 401);

// T4 good key + good body
r = mockRes();
await handler(mockReq('POST', { 'x-device-key': 'dk_123456' }, goodBody), r);
check('valid report 200', r.code === 200, `got ${r.code} ${JSON.stringify(r.body)}`);
check('row written to supabase', lastFetch && lastFetch.url.includes('/rest/v1/locations'));
const rowSent = JSON.parse(lastFetch.opts.body);
check('row fields mapped', rowSent.lat === 39.9042 && rowSent.provider === 'gps' && rowSent.battery === 80);

// T5 invalid coords
r = mockRes();
await handler(mockReq('POST', { 'x-device-key': 'dk_123456' }, { ...goodBody, lat: 999 }), r);
check('lat 999 rejected 400', r.code === 400);

// T6 invalid battery
r = mockRes();
await handler(mockReq('POST', { 'x-device-key': 'dk_123456' }, { ...goodBody, battery: 150 }), r);
check('battery 150 rejected 400', r.code === 400);

// T7 bad provider
r = mockRes();
await handler(mockReq('POST', { 'x-device-key': 'dk_123456' }, { ...goodBody, provider: 'xyz' }), r);
check('bad provider rejected 400', r.code === 400);

// T8 future ts
r = mockRes();
await handler(mockReq('POST', { 'x-device-key': 'dk_123456' }, { ...goodBody, ts: new Date(Date.now() + 3600_000).toISOString() }), r);
check('future ts rejected 400', r.code === 400);

// T9 backfill ts accepted
r = mockRes();
await handler(mockReq('POST', { 'x-device-key': 'dk_123456' }, { ...goodBody, ts: new Date(Date.now() - 3600_000).toISOString() }), r);
check('1h-ago ts accepted 200', r.code === 200);
check('client ts preserved', JSON.parse(lastFetch.opts.body).ts.includes('T'));

// T10 malformed json
r = mockRes();
await handler(mockReq('POST', { 'x-device-key': 'dk_123456' }, '{bad json'), r);
check('malformed json 400', r.code === 400);

// locations.js tests
const locHandler = (await import('../server/api/locations.js')).default;
let fetchArgs = null;
globalThis.fetch = async (url, opts) => {
  fetchArgs = { url, opts };
  return { ok: true, status: 200, json: async () => [{ ts: '2024-01-01T00:00:02Z', lat: 1, lng: 2 }, { ts: '2024-01-01T00:00:01Z', lat: 1, lng: 2 }] };
};

console.log('locations.js tests:');

// L1 no token
r = mockRes();
await locHandler({ method: 'GET', query: {} }, r);
check('missing token 401', r.code === 401);

// L2 wrong token
r = mockRes();
await locHandler({ method: 'GET', query: { token: 'nope' } }, r);
check('wrong token 401', r.code === 401);

// L3 valid
r = mockRes();
await locHandler({ method: 'GET', query: { token: 'at_654321', limit: '10' } }, r);
check('valid token 200', r.code === 200);
check('desc rows reversed to asc', r.body[0].ts < r.body[1].ts);
const decodedUrl = decodeURIComponent(fetchArgs.url);
check('select fields present', decodedUrl.includes('select=ts,device,provider'));
check('limit applied', decodedUrl.includes('limit=10'));

// L4 since filter
r = mockRes();
await locHandler({ method: 'GET', query: { token: 'at_654321', since: '2024-01-01T00:00:00Z' } }, r);
check('since filter encoded', decodeURIComponent(fetchArgs.url).includes('ts=gt.2024-01-01T00'));

// L5 bad since
r = mockRes();
await locHandler({ method: 'GET', query: { token: 'at_654321', since: 'not-a-date' } }, r);
check('bad since 400', r.code === 400);

// L6 limit clamped
r = mockRes();
await locHandler({ method: 'GET', query: { token: 'at_654321', limit: '99999' } }, r);
check('limit clamped to 2000', decodeURIComponent(fetchArgs.url).includes('limit=2000'));

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
