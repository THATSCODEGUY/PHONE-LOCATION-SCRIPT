import crypto from 'node:crypto';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const CHECK_INTERVAL_MS = 2000;
const CMD_TTL_MS = 10 * 60 * 1000;

function safeEq(a, b) {
  const ha = crypto.createHash('sha256').update(String(a)).digest();
  const hb = crypto.createHash('sha256').update(String(b)).digest();
  return crypto.timingSafeEqual(ha, hb);
}

export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  if (req.method !== 'GET') {
    res.setHeader('Allow', 'GET');
    return res.status(405).json({ error: 'method not allowed' });
  }

  const key = process.env.DEVICE_KEY || '';
  const provided = req.headers['x-device-key'] || '';
  if (!key || !provided || !safeEq(key, provided)) {
    return res.status(401).json({ error: 'unauthorized' });
  }

  const device = String(req.query.device || 'primary').slice(0, 64);
  let wait = parseInt(req.query.wait, 10);
  if (!Number.isFinite(wait)) wait = 50;
  wait = Math.max(0, Math.min(55, wait));

  const sbUrl = (process.env.SUPABASE_URL || '').replace(/\/+$/, '');
  const sbKey = process.env.SUPABASE_SERVICE_KEY || '';
  if (!sbUrl || !sbKey) {
    console.error('poll: missing SUPABASE_URL / SUPABASE_SERVICE_KEY');
    return res.status(500).json({ error: 'server misconfigured' });
  }
  const sbHeaders = { apikey: sbKey, Authorization: `Bearer ${sbKey}`, 'Content-Type': 'application/json' };

  // 心跳: 每次进入即刷新 (长轮询每 ~wait 秒必然重连, 心跳粒度 ≈ wait)
  try {
    await fetch(`${sbUrl}/rest/v1/devices?on_conflict=device`, {
      method: 'POST',
      headers: { ...sbHeaders, Prefer: 'resolution=merge-duplicates' },
      body: JSON.stringify({ device, last_seen: new Date().toISOString() }),
    });
  } catch (e) {
    console.error('poll: heartbeat upsert failed', e.message);
  }

  // 懒清理: 过期未领取的命令标记 expired
  try {
    const before = new Date(Date.now() - CMD_TTL_MS).toISOString();
    await fetch(`${sbUrl}/rest/v1/commands?status=eq.pending&created_at=lt.${before}`, {
      method: 'PATCH',
      headers: { ...sbHeaders, Prefer: 'return=minimal' },
      body: JSON.stringify({ status: 'expired' }),
    });
  } catch (e) {
    console.error('poll: expire sweep failed', e.message);
  }

  const deadline = Date.now() + wait * 1000;
  do {
    let r;
    try {
      r = await fetch(`${sbUrl}/rest/v1/rpc/claim_next_command`, {
        method: 'POST',
        headers: sbHeaders,
        body: JSON.stringify({ p_device: device }),
      });
    } catch (e) {
      console.error('poll: claim rpc failed', e.message);
      break;
    }
    if (r.ok) {
      const rows = await r.json().catch(() => []);
      if (Array.isArray(rows) && rows.length > 0) {
        return res.status(200).json({ id: rows[0].id, type: rows[0].type, created_at: rows[0].created_at });
      }
    } else {
      const t = await r.text().catch(() => '');
      console.error(`poll: claim rpc ${r.status} ${t.slice(0, 200)}`);
      break;
    }
    if (Date.now() >= deadline) break;
    await sleep(CHECK_INTERVAL_MS);
  } while (true);

  return res.status(204).end();
}
