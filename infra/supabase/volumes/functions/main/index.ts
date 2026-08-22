import * as jose from "jsr:@panva/jose@6";

console.log("Apachiy edge main router started");

const JWT_SECRET = Deno.env.get("JWT_SECRET");
const VERIFY_JWT = Deno.env.get("VERIFY_JWT") === "true";

function getAuthToken(req: Request) {
  const authHeader = req.headers.get("authorization");
  if (!authHeader) throw new Error("Missing authorization header");
  const [bearer, token] = authHeader.split(" ");
  if (bearer !== "Bearer") throw new Error("Auth header is not 'Bearer {token}'");
  return token;
}

async function isValidLegacyJWT(jwt: string): Promise<boolean> {
  if (!JWT_SECRET) return false;
  const secretKey = new TextEncoder().encode(JWT_SECRET);
  try {
    await jose.jwtVerify(jwt, secretKey);
    return true;
  } catch {
    return false;
  }
}

Deno.serve(async (req: Request) => {
  if (req.method !== "OPTIONS" && VERIFY_JWT) {
    try {
      const token = getAuthToken(req);
      if (!await isValidLegacyJWT(token)) {
        return new Response(JSON.stringify({ msg: "Invalid JWT" }), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        });
      }
    } catch (e) {
      return new Response(JSON.stringify({ msg: String(e) }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }
  }

  const url = new URL(req.url);
  const pathParts = url.pathname.split("/");
  const serviceName = pathParts[1];

  if (!serviceName) {
    return new Response(JSON.stringify({ msg: "missing function name in request" }), {
      status: 400,
      headers: { "Content-Type": "application/json" },
    });
  }

  const servicePath = `/home/deno/functions/${serviceName}`;
  const importMapPath = "/home/deno/functions/deno.jsonc";
  const envVarsObj = Deno.env.toObject();
  const envVars = Object.keys(envVarsObj).map((k) => [k, envVarsObj[k]]);

  try {
    const worker = await EdgeRuntime.userWorkers.create({
      servicePath,
      memoryLimitMb: 150,
      workerTimeoutMs: 60_000,
      noModuleCache: false,
      importMapPath,
      envVars,
    });
    return await worker.fetch(req);
  } catch (e) {
    return new Response(JSON.stringify({ msg: String(e) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
