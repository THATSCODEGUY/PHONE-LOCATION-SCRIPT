import crypto from 'node:crypto';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const CHECK_INTERVAL_MS = 2000;
const CMD_TTL_MS = 10 * 60 * 1000;
// v2.4 免费额度省费: 服务端硬限挂线 ≤5s (Hobby 版内存固定 2GB 不可降, 只能压时长)
const MAX_WAIT_S = 5;
// 过期清扫时间门控: 每个暖实例最多 10 分钟扫一次, 不再每次 poll 都扫 (省 Active CPU)
const SWEEP_GATE_MS = 10 * 60 * 1000;
let lastSweepMs = 0;

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
  if (!Number.isFinite(wait)) wait = MAX_WAIT_S;
  wait = Math.max(0, Math.min(MAX_WAIT_S, wait));

  const sbUrl = (process.env.SUPABASE_URL || '').replace(/\/+$/, '');
  const sbKey = process.env.SUPABASE_SERVICE_KEY || '';
  if (!sbUrl || !sbKey) {
    console.error('poll: missing SUPABASE_URL / SUPABASE_SERVICE_KEY');
    return res.status(500).json({ error: 'server misconfigured' });
  }
  const sbHeaders = { apikey: sbKey, Authorization: `Bearer ${sbKey}`, 'Content-Type': 'application/json' };

  // 心跳: 每次进入即刷新 (长轮询每 ~wait 秒必然重连, 心跳粒度 ≈ wait)
  try {
    await fetch(`${sbUrl}/rest/v1/phonelocation_devices?on_conflict=device`, {
      method: 'POST',
      headers: { ...sbHeaders, Prefer: 'resolution=merge-duplicates' },
      body: JSON.stringify({ device, last_seen: new Date().toISOString() }),
    });
  } catch (e) {
    console.error('poll: heartbeat upsert failed', e.message);
  }

  // 懒清理: 过期未领取的命令标记 expired (10 分钟门控, 非每次 poll 都扫)
  if (Date.now() - lastSweepMs > SWEEP_GATE_MS) {
    lastSweepMs = Date.now();
    try {
      const before = new Date(Date.now() - CMD_TTL_MS).toISOString();
      await fetch(`${sbUrl}/rest/v1/phonelocation_commands?status=eq.pending&created_at=lt.${before}`, {
        method: 'PATCH',
        headers: { ...sbHeaders, Prefer: 'return=minimal' },
        body: JSON.stringify({ status: 'expired' }),
      });
    } catch (e) {
      console.error('poll: expire sweep failed', e.message);
    }
  }

  // v2.4.2 远程配置下发: 仅当客户端携带 cfgver (即 v2.4.2+ App) 且服务端版本更新时捎带,
  // 旧客户端不发该参数, 行为与历史版本逐字节一致
  let cfg = null;
  const cfgver = parseInt(req.query.cfgver, 10);
  if (Number.isFinite(cfgver)) {
    try {
      const rc = await fetch(
        `${sbUrl}/rest/v1/phonelocation_config?device=eq.${encodeURIComponent(device)}&select=enabled,work_start,work_end,work_days,version&limit=1`,
        { headers: sbHeaders },
      );
      if (rc.ok) {
        const rows = await rc.json().catch(() => []);
        if (Array.isArray(rows) && rows.length > 0 && Number(rows[0].version) > cfgver) {
          cfg = {
            enabled: rows[0].enabled,
            work_start: rows[0].work_start,
            work_end: rows[0].work_end,
            work_days: rows[0].work_days,
            version: Number(rows[0].version),
          };
        }
      }
    } catch (e) {
      console.error('poll: cfg fetch failed', e.message);
    }
  }

  const deadline = Date.now() + wait * 1000;
  do {
    let r;
    try {
      r = await fetch(`${sbUrl}/rest/v1/rpc/phonelocation_claim_next_command`, {
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
        const out = { id: rows[0].id, type: rows[0].type, created_at: rows[0].created_at };
        if (cfg) out.cfg = cfg;
        return res.status(200).json(out);
      }
    } else {
      const t = await r.text().catch(() => '');
      console.error(`poll: claim rpc ${r.status} ${t.slice(0, 200)}`);
      break;
    }
    if (Date.now() >= deadline) break;
    await sleep(CHECK_INTERVAL_MS);
  } while (true);

  if (cfg) return res.status(200).json({ cfg });
  return res.status(204).end();
}
