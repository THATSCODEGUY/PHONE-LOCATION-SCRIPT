import crypto from 'node:crypto';

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

  const token = process.env.ACCESS_TOKEN || '';
  const provided = String(req.query.token || '');
  if (!token || !provided || !safeEq(token, provided)) {
    return res.status(401).json({ error: 'unauthorized' });
  }

  let limit = parseInt(req.query.limit, 10);
  if (!Number.isFinite(limit)) limit = 500;
  limit = Math.max(1, Math.min(2000, limit));

  let since = req.query.since ? String(req.query.since) : '';
  if (since) {
    const t = Date.parse(since);
    if (!Number.isFinite(t)) return res.status(400).json({ error: 'invalid since' });
    since = new Date(t).toISOString();
  }

  const sbUrl = (process.env.SUPABASE_URL || '').replace(/\/+$/, '');
  const sbKey = process.env.SUPABASE_SERVICE_KEY || '';
  if (!sbUrl || !sbKey) {
    console.error('locations: missing SUPABASE_URL / SUPABASE_SERVICE_KEY');
    return res.status(500).json({ error: 'server misconfigured' });
  }

  const qs = new URLSearchParams({
    select: 'ts,device,provider,lat,lng,accuracy,speed,bearing,battery',
    order: 'ts.desc',
    limit: String(limit),
  });
  if (since) qs.set('ts', `gt.${since}`);

  let r;
  try {
    r = await fetch(`${sbUrl}/rest/v1/locations?${qs}`, {
      headers: { apikey: sbKey, Authorization: `Bearer ${sbKey}` },
    });
  } catch (e) {
    console.error('locations: supabase fetch failed', e.message);
    return res.status(502).json({ error: 'upstream failed' });
  }

  if (!r.ok) {
    const t = await r.text().catch(() => '');
    console.error(`locations: supabase ${r.status} ${t.slice(0, 300)}`);
    return res.status(502).json({ error: 'upstream failed' });
  }

  const rows = await r.json().catch(() => null);
  if (!Array.isArray(rows)) return res.status(502).json({ error: 'upstream failed' });

  rows.reverse();
  return res.status(200).json(rows);
}
