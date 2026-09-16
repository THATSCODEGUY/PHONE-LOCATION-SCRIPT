// v2 全量逻辑自测: report / locations / poll / command (Supabase 以内存路由模拟)
let pass = 0, fail = 0;
function check(name, cond, extra = '') {
  if (cond) { pass++; console.log(`  PASS  ${name}`); }
  else { fail++; console.log(`  FAIL  ${name} ${extra}`); }
}

function mockRes() {
  return {
    code: 0, body: null, ended: false,
    setHeader() {},
    status(c) { this.code = c; return this; },
    json(j) { this.body = j; return this; },
    end() { this.ended = true; return this; },
  };
}
function mockReq(method, headers, body, query = {}) {
  return { method, headers: headers || {}, body, query };
}

// ---------- 内存版 Supabase ----------
const db = {
  commands: [],
  devices: {},
  config: {},
  nextCmdId: 1,
  calls: [],
};
function jres(obj, status) {
  return { ok: status < 400, status, json: async () => obj, text: async () => JSON.stringify(obj) };
}
globalThis.fetch = async (url, opts = {}) => {
  const u = new URL(url);
  const p = u.pathname;
  const method = (opts.method || 'GET').toUpperCase();
  const body = opts.body ? JSON.parse(opts.body) : {};
  db.calls.push({ p, method, body, search: u.search });

  if (p === '/rest/v1/phonelocation_locations' && method === 'POST') return jres({}, 201);
  if (p === '/rest/v1/phonelocation_locations' && method === 'GET')
    return jres([{ ts: '2024-01-01T00:00:02Z', lat: 1, lng: 2 }, { ts: '2024-01-01T00:00:01Z', lat: 1, lng: 2 }], 200);

  if (p === '/rest/v1/phonelocation_commands' && method === 'POST') {
    const row = {
      id: db.nextCmdId++, created_at: new Date().toISOString(),
      device: body.device || 'primary', type: body.type || 'locate',
      status: 'pending', claimed_at: null, done_at: null,
    };
    db.commands.push(row);
    return jres([row], 201);
  }
  if (p === '/rest/v1/phonelocation_commands' && method === 'PATCH') {
    const idm = /id=eq\.(\d+)/.exec(u.search);
    const stm = /status=eq\.(\w+)/.exec(u.search);
    const ltm = /created_at=lt\.([^&]+)/.exec(u.search);
    const ltBefore = ltm ? new Date(decodeURIComponent(ltm[1])) : null;
    for (const c of db.commands) {
      if (idm && String(c.id) !== idm[1]) continue;
      if (stm && c.status !== stm[1]) continue;
      if (ltBefore && new Date(c.created_at) >= ltBefore) continue;
      Object.assign(c, body);
    }
    return { ok: true, status: 204, text: async () => '' };
  }
  if (p === '/rest/v1/phonelocation_commands' && method === 'GET') {
    const idm = /id=eq\.(\d+)/.exec(u.search);
    const rows = db.commands.filter((c) => !idm || String(c.id) === idm[1]);
    return jres(rows, 200);
  }

  if (p === '/rest/v1/phonelocation_devices' && method === 'POST') {
    db.devices[body.device] = Object.assign(db.devices[body.device] || { device: body.device }, body);
    return jres([db.devices[body.device]], 201);
  }
  if (p === '/rest/v1/phonelocation_devices' && method === 'GET') {
    const dm = /device=eq\.([\w-]+)/.exec(u.search);
    const rows = Object.values(db.devices).filter((d) => !dm || d.device === dm[1]);
    return jres(rows, 200);
  }

  if (p === '/rest/v1/phonelocation_config' && method === 'GET') {
    const dm = /device=eq\.([\w-]+)/.exec(u.search);
    const rows = Object.values(db.config).filter((c) => !dm || c.device === dm[1]);
    return jres(rows, 200);
  }
  if (p === '/rest/v1/phonelocation_config' && method === 'POST') {
    const dev = body.device || 'primary';
    const row = Object.assign(db.config[dev] || { device: dev }, body);
    db.config[dev] = row;
    return jres([row], 201);
  }

  if (p === '/rest/v1/rpc/phonelocation_claim_next_command') {
    const cand = db.commands.filter((c) => c.status === 'pending').sort((a, b) => a.id - b.id)[0];
    if (!cand) return jres([], 200);
    cand.status = 'claimed';
    cand.claimed_at = new Date().toISOString();
    return jres([cand], 200);
  }

  return { ok: false, status: 404, text: async () => 'mock: not found' };
};

process.env.DEVICE_KEY = 'dk_123456';
process.env.ACCESS_TOKEN = 'at_654321';
process.env.SUPABASE_URL = 'https://fake.supabase.co';
process.env.SUPABASE_SERVICE_KEY = 'svc_fake';

const report = (await import('../server/api/report.js')).default;
const locations = (await import('../server/api/locations.js')).default;
const poll = (await import('../server/api/poll.js')).default;
const command = (await import('../server/api/command.js')).default;
const config = (await import('../server/api/config.js')).default;

const goodBody = { lat: 39.9042, lng: 116.4074, accuracy: 12, battery: 80, provider: 'gps', device: 'test' };
const dkHeader = { 'x-device-key': 'dk_123456' };
let r;

// ================= report.js =================
console.log('report.js:');
r = mockRes(); await report(mockReq('GET', {}), r);
check('GET rejected 405', r.code === 405);
r = mockRes(); await report(mockReq('POST', {}, goodBody), r);
check('missing key 401', r.code === 401);
r = mockRes(); await report(mockReq('POST', { 'x-device-key': 'wrong' }, goodBody), r);
check('wrong key 401', r.code === 401);

db.calls = [];
r = mockRes(); await report(mockReq('POST', dkHeader, goodBody), r);
check('valid report 200', r.code === 200, `got ${r.code}`);
check('devices heartbeat upsert on report', db.calls.some((c) => c.p === '/rest/v1/phonelocation_devices' && c.body.device === 'test' && c.body.last_report));

r = mockRes(); await report(mockReq('POST', dkHeader, { ...goodBody, lat: 999 }), r);
check('lat 999 rejected 400', r.code === 400);
r = mockRes(); await report(mockReq('POST', dkHeader, { ...goodBody, battery: 150 }), r);
check('battery 150 rejected 400', r.code === 400);
r = mockRes(); await report(mockReq('POST', dkHeader, { ...goodBody, charging: 'yes' }), r);
check('non-boolean charging 400', r.code === 400);
db.calls = [];
r = mockRes(); await report(mockReq('POST', dkHeader, { ...goodBody, charging: true, ssid: 'HomeWiFi-5G' }), r);
check('charging/ssid accepted 200', r.code === 200);
const envRow = db.calls.find((c) => c.p === '/rest/v1/phonelocation_locations');
check('charging/ssid persisted', envRow && envRow.body.charging === true && envRow.body.ssid === 'HomeWiFi-5G');
db.calls = [];
r = mockRes(); await report(mockReq('POST', dkHeader, { ...goodBody, ssid: 'x'.repeat(100) }), r);
check('long ssid accepted (truncated)', r.code === 200);
const truncRow = db.calls.find((c) => c.p === '/rest/v1/phonelocation_locations');
check('ssid truncated to 64', truncRow && truncRow.body.ssid.length === 64);
db.calls = [];
r = mockRes(); await report(mockReq('POST', dkHeader, { ...goodBody, ssid: '   ' }), r);
check('blank ssid accepted 200', r.code === 200);
const blankRow = db.calls.find((c) => c.p === '/rest/v1/phonelocation_locations');
check('blank ssid normalized to null', blankRow && blankRow.body.ssid === null);
r = mockRes(); await report(mockReq('POST', dkHeader, { ...goodBody, cmd_id: 'abc' }), r);
check('non-numeric cmd_id 400', r.code === 400);
r = mockRes(); await report(mockReq('POST', dkHeader, { ...goodBody, ts: new Date(Date.now() + 3600_000).toISOString() }), r);
check('future ts rejected 400', r.code === 400);

// cmd_id 销单流程: 先建命令 → 领取 → 带 cmd_id 上报 → 命令应变 done
r = mockRes(); await command(mockReq('POST', {}, { device: 'test' }, { token: 'at_654321' }), r);
const cmdId = r.body && r.body.id;
check('seed command for cmd_id test', !!cmdId);
r = mockRes(); await poll(mockReq('GET', dkHeader, null, { device: 'test', wait: '0' }), r);
check('poll claims seeded command', r.code === 200 && r.body.id === cmdId);
db.calls = [];
r = mockRes(); await report(mockReq('POST', dkHeader, { ...goodBody, cmd_id: cmdId }), r);
check('report with cmd_id 200', r.code === 200);
const ack = db.calls.find((c) => c.p === '/rest/v1/phonelocation_commands' && c.method === 'PATCH');
check('ack PATCH targets id & guards claimed', !!ack && ack.search.includes(`id=eq.${cmdId}`) && ack.search.includes('status=eq.claimed') && ack.body.status === 'done');
r = mockRes(); await command(mockReq('GET', {}, null, { token: 'at_654321', id: String(cmdId), device: 'test' }), r);
check('command status now done', r.body.command && r.body.command.status === 'done');

// ================= locations.js =================
console.log('locations.js:');
r = mockRes(); await locations(mockReq('GET', {}, null, {}), r);
check('missing token 401', r.code === 401);
r = mockRes(); await locations(mockReq('GET', {}, null, { token: 'nope' }), r);
check('wrong token 401', r.code === 401);
r = mockRes(); await locations(mockReq('GET', {}, null, { token: 'at_654321', limit: '10' }), r);
check('valid token 200', r.code === 200);
check('select includes charging/ssid', db.calls.some((c) => c.p === '/rest/v1/phonelocation_locations' && c.search.includes('charging') && c.search.includes('ssid')));
check('desc rows reversed to asc', r.body[0].ts < r.body[1].ts);
r = mockRes(); await locations(mockReq('GET', {}, null, { token: 'at_654321', since: 'not-a-date' }), r);
check('bad since 400', r.code === 400);
r = mockRes(); await locations(mockReq('GET', {}, null, { token: 'at_654321', limit: '99999' }), r);
check('limit clamped to 2000', decodeURIComponent(db.calls[db.calls.length - 1].search).includes('limit=2000'));

// ================= poll.js =================
console.log('poll.js:');
r = mockRes(); await poll(mockReq('GET', {}), r);
check('missing device key 401', r.code === 401);
r = mockRes(); await poll(mockReq('GET', { 'x-device-key': 'wrong' }, null, {}), r);
check('wrong device key 401', r.code === 401);
r = mockRes(); await poll(mockReq('POST', dkHeader, null, {}), r);
check('POST rejected 405', r.code === 405);

db.calls = [];
r = mockRes(); await poll(mockReq('GET', dkHeader, null, { device: 'test', wait: '0' }), r);
check('no pending command → 204', r.code === 204 && r.ended);
check('poll writes heartbeat upsert', db.calls.some((c) => c.p === '/rest/v1/phonelocation_devices' && c.body.device === 'test' && c.body.last_seen));

// 长轮询即时命中: 排入命令后 poll(有挂线时间) 应首轮即返回, 不睡眠
r = mockRes(); await command(mockReq('POST', {}, { device: 'test' }, { token: 'at_654321' }), r);
const cmd2 = r.body.id;
check('seed command for long-poll test', !!cmd2);
const t0 = Date.now();
r = mockRes(); await poll(mockReq('GET', dkHeader, null, { device: 'test', wait: '5' }), r);
check('long-poll instant claim 200', r.code === 200 && r.body.id === cmd2);
check('long-poll returned without full wait', Date.now() - t0 < 3000);
r = mockRes(); await poll(mockReq('GET', dkHeader, null, { device: 'test', wait: '0' }), r);
check('second poll finds nothing → 204', r.code === 204);

// v2.4 免费额度省费: wait 服务端钳制 ≤5s + 过期清扫时间门控
db.calls = [];
const tClamp = Date.now();
r = mockRes(); await poll(mockReq('GET', dkHeader, null, { device: 'test', wait: '50' }), r);
check('wait>5 clamped server-side → 204 in ≤~6s (old: up to 55s)', r.code === 204 && Date.now() - tClamp < 6500);
check('expire sweep time-gated (not on every poll)', !db.calls.some((c) => c.p === '/rest/v1/phonelocation_commands' && c.method === 'PATCH' && c.search.includes('created_at=lt.')));
check('heartbeat still upserted every poll', db.calls.some((c) => c.p === '/rest/v1/phonelocation_devices' && c.body.last_seen));

// ================= config.js (v2.4.2 地图端远程配置) =================
console.log('config.js:');
r = mockRes(); await config(mockReq('GET', {}, null, {}), r);
check('config GET no token 401', r.code === 401);
r = mockRes(); await config(mockReq('GET', {}, null, { token: 'at_654321', device: 'test' }), r);
check('config GET defaults when no row', r.code === 200 && r.body.enabled === true && r.body.work_start === 480 && r.body.work_days === '1-5' && r.body.version === 0);
r = mockRes(); await config(mockReq('POST', {}, { work_start: 900, work_end: 800, work_days: '1-5' }, { token: 'at_654321' }), r);
check('config POST start>=end rejected 400', r.code === 400);
r = mockRes(); await config(mockReq('POST', {}, { work_start: 420, work_end: 1200, work_days: '0-9' }, { token: 'at_654321' }), r);
check('config POST bad days rejected 400', r.code === 400);
r = mockRes(); await config(mockReq('POST', {}, { enabled: 'yes' }, { token: 'at_654321' }), r);
check('config POST non-boolean enabled 400', r.code === 400);
r = mockRes(); await config(mockReq('POST', {}, { enabled: true, work_start: 420, work_end: 1200, work_days: '1-5' }, { token: 'at_654321', device: 'test' }), r);
check('config POST valid → 200 version 1', r.code === 200 && r.body.version === 1 && r.body.work_start === 420);
r = mockRes(); await config(mockReq('POST', {}, { enabled: false }, { token: 'at_654321', device: 'test' }), r);
check('config POST disable → version 2', r.code === 200 && r.body.version === 2 && r.body.enabled === false);

// poll cfg 捎带 (v2.4.2): cfgver 比对, 旧客户端零影响
r = mockRes(); await poll(mockReq('GET', dkHeader, null, { device: 'test', wait: '0', cfgver: '1' }), r);
check('poll cfgver stale → 200 carries cfg v2', r.code === 200 && r.body.cfg && r.body.cfg.version === 2 && r.body.cfg.enabled === false);
r = mockRes(); await poll(mockReq('GET', dkHeader, null, { device: 'test', wait: '0' }), r);
check('poll without cfgver → 204 unchanged (compat)', r.code === 204 && r.ended);
r = mockRes(); await poll(mockReq('GET', dkHeader, null, { device: 'test', wait: '0', cfgver: '2' }), r);
check('poll cfgver current → 204 (nothing to push)', r.code === 204);
r = mockRes(); await command(mockReq('POST', {}, { device: 'test' }, { token: 'at_654321' }), r);
const cmd4 = r.body.id;
r = mockRes(); await poll(mockReq('GET', dkHeader, null, { device: 'test', wait: '0', cfgver: '1' }), r);
check('poll command+cfg both delivered', r.code === 200 && r.body.id === cmd4 && r.body.cfg && r.body.cfg.version === 2);

// ================= command.js =================
console.log('command.js:');
r = mockRes(); await command(mockReq('POST', {}, { device: 'test' }, {}), r);
check('POST without token 401', r.code === 401);
r = mockRes(); await command(mockReq('GET', {}, null, { token: 'at_654321', id: 'abc' }), r);
check('GET invalid id 400', r.code === 400);
r = mockRes(); await command(mockReq('POST', {}, { device: 'test' }, { token: 'at_654321' }), r);
check('POST creates pending command', r.code === 200 && r.body.status === 'pending' && typeof r.body.id === 'number');
const cmd3 = r.body.id;
r = mockRes(); await command(mockReq('GET', {}, null, { token: 'at_654321', id: String(cmd3), device: 'test' }), r);
check('GET returns command + heartbeat', r.body.command && r.body.command.id === cmd3 && r.body.heartbeat && r.body.heartbeat.device === 'test');
r = mockRes(); await command(mockReq('GET', {}, null, { token: 'at_654321', device: 'test' }), r);
check('GET without id → heartbeat only', r.code === 200 && r.body.command === null && r.body.heartbeat);
r = mockRes(); await command(mockReq('GET', {}, null, { token: 'at_654321', id: '99999' }), r);
check('GET unknown id → command null (not 500)', r.code === 200 && r.body.command === null);
r = mockRes(); await command(mockReq('PUT', {}, null, { token: 'at_654321' }), r);
check('PUT rejected 405', r.code === 405);

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
