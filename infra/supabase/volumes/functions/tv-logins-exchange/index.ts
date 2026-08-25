const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";

type ExchangeRequest = {
  code?: string;
  device_nonce?: string;
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function serviceHeaders(): HeadersInit {
  return {
    Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
    apikey: SERVICE_ROLE_KEY,
    "Content-Type": "application/json",
  };
}

async function consumeSession(code: string, deviceNonce: string): Promise<string | null> {
  const res = await fetch(`${SUPABASE_URL}/rest/v1/rpc/consume_tv_login_session`, {
    method: "POST",
    headers: serviceHeaders(),
    body: JSON.stringify({
      p_code: code,
      p_device_nonce: deviceNonce,
    }),
  });

  if (!res.ok) {
    console.error("consume_tv_login_session failed", res.status, await res.text());
    return null;
  }

  const userId = await res.json();
  return typeof userId === "string" && userId.length > 0 ? userId : null;
}

async function fetchUserEmail(userId: string): Promise<string | null> {
  const res = await fetch(`${SUPABASE_URL}/auth/v1/admin/users/${userId}`, {
    headers: {
      Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
      apikey: SERVICE_ROLE_KEY,
    },
  });

  if (!res.ok) {
    console.error("admin user lookup failed", res.status, await res.text());
    return null;
  }

  const user = await res.json();
  return typeof user?.email === "string" ? user.email : null;
}

async function mintSessionForEmail(email: string): Promise<Record<string, unknown> | null> {
  const linkRes = await fetch(`${SUPABASE_URL}/auth/v1/admin/generate_link`, {
    method: "POST",
    headers: serviceHeaders(),
    body: JSON.stringify({
      type: "magiclink",
      email,
    }),
  });

  if (!linkRes.ok) {
    console.error("generate_link failed", linkRes.status, await linkRes.text());
    return null;
  }

  const linkData = await linkRes.json();
  const tokenHash = linkData?.hashed_token ?? linkData?.properties?.hashed_token;
  if (typeof tokenHash !== "string" || tokenHash.length === 0) {
    console.error("generate_link missing hashed_token", linkData);
    return null;
  }

  const verifyRes = await fetch(`${SUPABASE_URL}/auth/v1/verify`, {
    method: "POST",
    headers: {
      apikey: ANON_KEY || SERVICE_ROLE_KEY,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      type: "magiclink",
      token_hash: tokenHash,
    }),
  });

  if (!verifyRes.ok) {
    console.error("verify failed", verifyRes.status, await verifyRes.text());
    return null;
  }

  return await verifyRes.json();
}

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return jsonResponse({ error: "method_not_allowed" }, 405);
  }

  if (!SUPABASE_URL || !SERVICE_ROLE_KEY) {
    return jsonResponse({ error: "misconfigured" }, 500);
  }

  let body: ExchangeRequest;
  try {
    body = await req.json();
  } catch {
    return jsonResponse({ error: "invalid_json" }, 400);
  }

  const code = String(body.code ?? "").trim().toUpperCase();
  const deviceNonce = String(body.device_nonce ?? "").trim();

  if (!code || !deviceNonce) {
    return jsonResponse({ error: "missing_fields" }, 400);
  }

  const userId = await consumeSession(code, deviceNonce);
  if (!userId) {
    return jsonResponse({ error: "invalid_session" }, 409);
  }

  const email = await fetchUserEmail(userId);
  if (!email) {
    return jsonResponse({ error: "token_mint_failed" }, 500);
  }

  const tokens = await mintSessionForEmail(email);
  if (!tokens) {
    return jsonResponse({ error: "token_mint_failed" }, 500);
  }

  const accessToken = tokens.access_token;
  const refreshToken = tokens.refresh_token;
  const expiresIn = tokens.expires_in;

  if (
    typeof accessToken !== "string" || accessToken.length === 0 ||
    typeof refreshToken !== "string" || refreshToken.length === 0 ||
    typeof expiresIn !== "number" || expiresIn <= 0
  ) {
    return jsonResponse({ error: "token_mint_failed" }, 500);
  }

  return jsonResponse({
    access_token: accessToken,
    refresh_token: refreshToken,
    token_type: typeof tokens.token_type === "string" ? tokens.token_type : "bearer",
    expires_in: expiresIn,
    user: tokens.user ?? undefined,
  });
});
