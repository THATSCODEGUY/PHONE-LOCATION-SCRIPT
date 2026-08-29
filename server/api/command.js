import crypto from 'node:crypto';

function safeEq(a, b) {
  const ha = crypto.createHash('sha256').update(String(a)).digest();
  const hb = crypto.createHash('sha256').update(String(b)).digest();
  return crypto.timingSafeEqual(ha, hb);
}

function authed(req) {
  const token = process.env.ACCESS_TOKEN || '';
  const provided = String(req.query.token || '');
  return !!token && !!provided && safeEq(token, provided);
}

export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  if (!authed(req)) return res.status(401).json({ error: 'unauthorized' });

  const sbUrl = (process.env.SUPABASE_URL || '').replace(/\/+$/, '');
  const sbKey = process.env.SUPABASE_SERVICE_KEY || '';
  if (!sbUrl || !sbKey) {
    console.error('command: missing SUPABASE_URL / SUPABASE_SERVICE_KEY');
    return res.status(500).json({ error: 'server misconfigured' });
  }
  const sbHeaders = { apikey: sbKey, Authorization: `Bearer ${sbKey}`, 'Content-Type': 'application/json' };

  // POST: 创建主动定位命令 (浏览器端 [获取位置] 按钮)
  if (req.method === 'POST') {
    const body = typeof req.body === 'object' && req.body !== null ? req.body : {};
    const device = String(body.device || 'primary').slice(0, 64);
    let r;
    try {
      r = await fetch(`${sbUrl}/rest/v1/commands`, {
        method: 'POST',
        headers: { ...sbHeaders, Prefer: 'return=representation' },
        body: JSON.stringify({ device, type: 'locate' }),
      });
    } catch (e) {
      console.error('command: create failed', e.message);
      return res.status(502).json({ error: 'upstream failed' });
    }
    if (!r.ok) {
      const t = await r.text().catch(() => '');
      console.error(`command: create ${r.status} ${t.slice(0, 200)}`);
      return res.status(502).json({ error: 'upstream failed' });
    }
    const rows = await r.json().catch(() => null);
    if (!Array.isArray(rows) || rows.length === 0) return res.status(502).json({ error: 'upstream failed' });
    const c = rows[0];
    return res.status(200).json({ id: c.id, status: c.status, device: c.device, created_at: c.created_at });
  }

  // GET: 命令状态 + 设备心跳 (地图轮询)
  if (req.method === 'GET') {
    const device = String(req.query.device || 'primary').slice(0, 64);
    let command = null;
    let heartbeat = null;

    if (req.query.id) {
      const id = parseInt(req.query.id, 10);
      if (!Number.isInteger(id) || id <= 0) return res.status(400).json({ error: 'invalid id' });
      const rc = await fetch(
        `${sbUrl}/rest/v1/commands?id=eq.${id}&select=id,status,type,device,created_at,claimed_at,done_at&limit=1`,
        { headers: sbHeaders },
      ).catch(() => null);
      if (rc && rc.ok) {
        const rows = await rc.json().catch(() => []);
        if (Array.isArray(rows) && rows.length > 0) command = rows[0];
      }
    }

    const rh = await fetch(
      `${sbUrl}/rest/v1/devices?device=eq.${encodeURIComponent(device)}&select=device,last_seen,last_report&limit=1`,
      { headers: sbHeaders },
    ).catch(() => null);
    if (rh && rh.ok) {
      const rows = await rh.json().catch(() => []);
      if (Array.isArray(rows) && rows.length > 0) heartbeat = rows[0];
    }

    return res.status(200).json({ command, heartbeat });
  }

  res.setHeader('Allow', 'GET, POST');
  return res.status(405).json({ error: 'method not allowed' });
}
