import crypto from 'node:crypto';

// v2.4.2 地图端远程配置: 总开关 + 工作窗口 (起止/星期)
// GET  ?token=&device= → 当前配置 (无行返回默认)
// POST ?token= {device?, enabled?, work_start?, work_end?, work_days?} → 校验后 upsert, version+1

const DAYS_RE = /^[0-7,-]+$/;

function safeEq(a, b) {
  const ha = crypto.createHash('sha256').update(String(a)).digest();
  const hb = crypto.createHash('sha256').update(String(b)).digest();
  return crypto.timingSafeEqual(ha, hb);
}

function bad(res, msg) {
  return res.status(400).json({ error: msg });
}

function validDays(s) {
  if (typeof s !== 'string' || !DAYS_RE.test(s) || s.includes('--')) return false;
  let n = 0;
  for (const part of s.split(',')) {
    if (!part) return false;
    if (part.includes('-')) {
      const a = Number(part.split('-')[0]);
      const b = Number(part.split('-')[1]);
      if (!Number.isInteger(a) || !Number.isInteger(b) || a < 1 || b > 7 || a > b) return false;
      n += b - a + 1;
    } else {
      const d = Number(part);
      if (!Number.isInteger(d) || d < 1 || d > 7) return false;
      n += 1;
    }
  }
  return n > 0;
}

export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  const token = process.env.ACCESS_TOKEN || '';
  const provided = String(req.query.token || '');
  if (!token || !provided || !safeEq(token, provided)) {
    return res.status(401).json({ error: 'unauthorized' });
  }

  const sbUrl = (process.env.SUPABASE_URL || '').replace(/\/+$/, '');
  const sbKey = process.env.SUPABASE_SERVICE_KEY || '';
  if (!sbUrl || !sbKey) {
    console.error('config: missing SUPABASE_URL / SUPABASE_SERVICE_KEY');
    return res.status(500).json({ error: 'server misconfigured' });
  }
  const sbHeaders = { apikey: sbKey, Authorization: `Bearer ${sbKey}`, 'Content-Type': 'application/json' };
  const device = String(req.query.device || 'primary').slice(0, 64);

  const CFG_SELECT = 'enabled,work_start,work_end,work_days,version';
  const DEFAULTS = { enabled: true, work_start: 480, work_end: 1200, work_days: '1-5', version: 0 };

  if (req.method === 'GET') {
    let row = null;
    try {
      const r = await fetch(`${sbUrl}/rest/v1/phonelocation_config?device=eq.${encodeURIComponent(device)}&select=${CFG_SELECT}&limit=1`, { headers: sbHeaders });
      if (r.ok) {
        const rows = await r.json().catch(() => []);
        if (Array.isArray(rows) && rows.length > 0) row = rows[0];
      } else {
        const t = await r.text().catch(() => '');
        console.error(`config: get ${r.status} ${t.slice(0, 200)}`);
        return res.status(502).json({ error: 'upstream failed' });
      }
    } catch (e) {
      console.error('config: get failed', e.message);
      return res.status(502).json({ error: 'upstream failed' });
    }
    return res.status(200).json({ device, ...(row || DEFAULTS) });
  }

  if (req.method === 'POST') {
    const body = typeof req.body === 'object' && req.body !== null ? req.body : {};

    const enabled = body.enabled === undefined ? true : body.enabled;
    if (typeof enabled !== 'boolean') return bad(res, 'enabled must be boolean');

    const work_start = body.work_start === undefined ? 480 : body.work_start;
    if (!Number.isInteger(work_start) || work_start < 0 || work_start > 1424) return bad(res, 'work_start must be 0..1424');

    const work_end = body.work_end === undefined ? 1200 : body.work_end;
    if (!Number.isInteger(work_end) || work_end < 1 || work_end > 1440) return bad(res, 'work_end must be 1..1440');

    if (work_start >= work_end) return bad(res, 'work_start must be < work_end');

    const work_days = body.work_days === undefined ? '1-5' : String(body.work_days);
    if (!validDays(work_days)) return bad(res, 'invalid work_days');

    // 读当前版本 → upsert 并 version+1
    let cur = null;
    try {
      const rg = await fetch(`${sbUrl}/rest/v1/phonelocation_config?device=eq.${encodeURIComponent(device)}&select=version&limit=1`, { headers: sbHeaders });
      if (rg.ok) {
        const rows = await rg.json().catch(() => []);
        if (Array.isArray(rows) && rows.length > 0) cur = rows[0];
      }
    } catch (_) { /* 忽略: 视为新配置 version=1 */ }
    const version = (cur ? Number(cur.version) : 0) + 1;

    const row = { device, enabled, work_start, work_end, work_days, version, updated_at: new Date().toISOString() };
    try {
      const r = await fetch(`${sbUrl}/rest/v1/phonelocation_config?on_conflict=device`, {
        method: 'POST',
        headers: { ...sbHeaders, Prefer: 'resolution=merge-duplicates,return=minimal' },
        body: JSON.stringify(row),
      });
      if (!r.ok) {
        const t = await r.text().catch(() => '');
        console.error(`config: upsert ${r.status} ${t.slice(0, 200)}`);
        return res.status(502).json({ error: 'upstream failed' });
      }
    } catch (e) {
      console.error('config: upsert failed', e.message);
      return res.status(502).json({ error: 'upstream failed' });
    }

    return res.status(200).json({ ok: true, device, enabled, work_start, work_end, work_days, version });
  }

  res.setHeader('Allow', 'GET, POST');
  return res.status(405).json({ error: 'method not allowed' });
}
