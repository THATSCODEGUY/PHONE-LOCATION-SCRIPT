import crypto from 'node:crypto';

const MAX_BODY = 4096;
const PROVIDERS = new Set(['gps', 'network', 'fused', 'passive']);
const MAX_FUTURE_MS = 2 * 60 * 1000;
const MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000;

function safeEq(a, b) {
  const ha = crypto.createHash('sha256').update(String(a)).digest();
  const hb = crypto.createHash('sha256').update(String(b)).digest();
  return crypto.timingSafeEqual(ha, hb);
}

function num(v) {
  if (v === null || v === undefined || v === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function bad(res, msg) {
  return res.status(400).json({ error: msg });
}

export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  if (req.method !== 'POST') {
    res.setHeader('Allow', 'POST');
    return res.status(405).json({ error: 'method not allowed' });
  }

  const key = process.env.DEVICE_KEY || '';
  const provided = req.headers['x-device-key'] || '';
  if (!key || !provided || !safeEq(key, provided)) {
    return res.status(401).json({ error: 'unauthorized' });
  }

  const raw = typeof req.body === 'string' ? req.body : JSON.stringify(req.body ?? {});
  if (raw.length > MAX_BODY) return bad(res, 'payload too large');

  let p;
  try { p = JSON.parse(raw); } catch { return bad(res, 'invalid json'); }

  const lat = num(p.lat);
  const lng = num(p.lng);
  if (lat === null || lng === null) return bad(res, 'invalid coordinates');
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return bad(res, 'invalid coordinates');

  const accuracy = num(p.accuracy);
  if (accuracy !== null && accuracy < 0) return bad(res, 'invalid accuracy');

  const battery = num(p.battery);
  if (battery !== null && (battery < 0 || battery > 100)) return bad(res, 'invalid battery');

  const speed = num(p.speed);
  if (speed !== null && speed < 0) return bad(res, 'invalid speed');

  const bearing = num(p.bearing);
  if (bearing !== null && (bearing < 0 || bearing > 360)) return bad(res, 'invalid bearing');

  const provider = p.provider === undefined || p.provider === null ? 'gps' : String(p.provider);
  if (!PROVIDERS.has(provider)) return bad(res, 'invalid provider');

  const device = p.device === undefined || p.device === null ? 'primary' : String(p.device).slice(0, 64);

  const cmdId = p.cmd_id === undefined || p.cmd_id === null || p.cmd_id === '' ? null : Number(p.cmd_id);
  if (cmdId !== null && (!Number.isInteger(cmdId) || cmdId <= 0)) return bad(res, 'invalid cmd_id');

  let tsMs = Date.now();
  if (p.ts !== undefined && p.ts !== null && p.ts !== '') {
    const t = Date.parse(String(p.ts));
    if (!Number.isFinite(t)) return bad(res, 'invalid ts');
    if (t > Date.now() + MAX_FUTURE_MS) return bad(res, 'ts in future');
    if (t < Date.now() - MAX_AGE_MS) return bad(res, 'ts too old');
    tsMs = t;
  }

  const sbUrl = (process.env.SUPABASE_URL || '').replace(/\/+$/, '');
  const sbKey = process.env.SUPABASE_SERVICE_KEY || '';
  if (!sbUrl || !sbKey) {
    console.error('report: missing SUPABASE_URL / SUPABASE_SERVICE_KEY');
    return res.status(500).json({ error: 'server misconfigured' });
  }

  const row = {
    ts: new Date(tsMs).toISOString(),
    device,
    provider,
    lat,
    lng,
    accuracy,
    speed,
    bearing,
    altitude: num(p.altitude),
    battery,
  };

  let r;
  try {
    r = await fetch(`${sbUrl}/rest/v1/locations`, {
      method: 'POST',
      headers: {
        apikey: sbKey,
        Authorization: `Bearer ${sbKey}`,
        'Content-Type': 'application/json',
        Prefer: 'return=minimal',
      },
      body: JSON.stringify(row),
    });
  } catch (e) {
    console.error('report: supabase fetch failed', e.message);
    return res.status(502).json({ error: 'upstream failed' });
  }

  if (!r.ok) {
    const t = await r.text().catch(() => '');
    console.error(`report: supabase ${r.status} ${t.slice(0, 300)}`);
    return res.status(502).json({ error: 'upstream failed' });
  }

  // 上报成功后的跟进操作: 销单 + 刷新 last_report 心跳 (失败不影响上报本身)
  try {
    if (cmdId !== null) {
      const rp = await fetch(`${sbUrl}/rest/v1/commands?id=eq.${cmdId}&status=eq.claimed`, {
        method: 'PATCH',
        headers: { apikey: sbKey, Authorization: `Bearer ${sbKey}`, 'Content-Type': 'application/json', Prefer: 'return=minimal' },
        body: JSON.stringify({ status: 'done', done_at: new Date().toISOString() }),
      });
      if (!rp.ok) console.error(`report: ack cmd ${cmdId} failed ${rp.status}`);
    }
    await fetch(`${sbUrl}/rest/v1/devices?on_conflict=device`, {
      method: 'POST',
      headers: { apikey: sbKey, Authorization: `Bearer ${sbKey}`, 'Content-Type': 'application/json', Prefer: 'resolution=merge-duplicates' },
      body: JSON.stringify({ device, last_report: row.ts, last_seen: new Date().toISOString() }),
    });
  } catch (e) {
    console.error('report: follow-up failed', e.message);
  }

  return res.status(200).json({ ok: true, ts: row.ts });
}
