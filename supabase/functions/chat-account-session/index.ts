import { createClient } from "npm:@supabase/supabase-js@2";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const pepper = Deno.env.get("CHAT_AUTH_PEPPER")!;
const encoder = new TextEncoder();

const admin = createClient(supabaseUrl, serviceRoleKey, {
  auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
});
const authClient = createClient(supabaseUrl, anonKey, {
  auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
});

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function hex(bytes: ArrayBuffer): string {
  return Array.from(new Uint8Array(bytes), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function hmac(value: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(pepper),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  return hex(await crypto.subtle.sign("HMAC", key, encoder.encode(value)));
}

function validUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function validIdentity(type: string, id: string): boolean {
  return (type === "microsoft" && /^[0-9a-f]{32}$/.test(id))
    || (type === "offline" && validUuid(id));
}

async function verifyMicrosoftIdentity(identityId: string, accessToken: unknown): Promise<Response | null> {
  const token = typeof accessToken === "string" ? accessToken : "";
  if (!token || token === "0" || token.length > 8192) {
    return json(401, { message: "Refresh the selected Microsoft account in Account Manager, then try again." });
  }
  try {
    const profileResponse = await fetch("https://api.minecraftservices.com/minecraft/profile", {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!profileResponse.ok) {
      return json(401, { message: "The selected Microsoft Minecraft session could not be verified. Refresh it in Account Manager." });
    }
    const profile = await profileResponse.json();
    if (typeof profile.id !== "string" || profile.id.toLowerCase() !== identityId) {
      return json(403, { message: "The selected Microsoft session does not match this Minecraft profile." });
    }
  } catch {
    return json(503, { message: "Could not verify the selected Microsoft account right now." });
  }
  return null;
}

async function sessionForLinkedUser(authUserId: string) {
  const { data, error } = await admin.auth.admin.getUserById(authUserId);
  const user = data.user;
  if (error || !user?.email) return { session: null, user: null, error };

  const metadata = user.user_metadata ?? {};
  const identityType = typeof metadata.chat_identity_source === "string"
    ? metadata.chat_identity_source.toLowerCase()
    : "";
  const identityId = typeof metadata.chat_identity_id === "string"
    ? metadata.chat_identity_id.toLowerCase()
    : "";
  if (!validIdentity(identityType, identityId)) {
    return { session: null, user, error: new Error("Chat identity metadata is invalid.") };
  }

  const derivedSecret = await hmac(`chat:${identityType}:${identityId}`);
  const { data: signedIn, error: signInError } = await authClient.auth.signInWithPassword({
    email: user.email,
    password: `TCL-${derivedSecret}`,
  });
  return { session: signedIn.session, user, error: signInError };
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return json(405, { message: "Method not allowed." });
  if (!pepper || !anonKey || !serviceRoleKey) return json(503, { message: "Chat account service is not configured." });

  let input: Record<string, unknown>;
  try {
    const value = await request.json();
    if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("Invalid body");
    input = value as Record<string, unknown>;
  } catch {
    return json(400, { message: "Invalid request." });
  }

  const identityType = typeof input.identity_type === "string" ? input.identity_type.toLowerCase() : "";
  const identityId = typeof input.identity_id === "string" ? input.identity_id.toLowerCase() : "";
  if (!validIdentity(identityType, identityId)) {
    return json(400, { message: "Unsupported launcher account identity." });
  }

  const rawIp = request.headers.get("cf-connecting-ip")
    ?? request.headers.get("x-forwarded-for")?.split(",")[0]?.trim()
    ?? "unknown";
  const ipHash = await hmac(`ip:${rawIp}`);
  const { data: allowed, error: rateLimitError } = await admin.rpc("take_chat_auth_rate_limit", {
    p_ip_hash: ipHash,
  });
  if (rateLimitError) return json(503, { message: "Chat account service is temporarily unavailable." });
  if (allowed !== true) return json(429, { message: "Please wait a little before reconnecting chat." });

  if (identityType === "microsoft") {
    const verificationError = await verifyMicrosoftIdentity(identityId, input.minecraft_access_token);
    if (verificationError) return verificationError;
  }

  const { data: identityLink, error: identityLookupError } = await admin
    .from("chat_launcher_identities")
    .select("auth_user_id")
    .eq("identity_type", identityType)
    .eq("identity_id", identityId)
    .maybeSingle();
  if (identityLookupError) return json(503, { message: "Chat account service is temporarily unavailable." });

  if (identityLink?.auth_user_id) {
    const { session, error } = await sessionForLinkedUser(identityLink.auth_user_id);
    if (error || !session) return json(503, { message: "Could not start the chat session. Please try again." });
    return json(200, {
      access_token: session.access_token,
      refresh_token: session.refresh_token,
      expires_in: session.expires_in,
      token_type: session.token_type,
      user: { id: identityLink.auth_user_id },
    });
  }

  const derivedSecret = await hmac(`chat:${identityType}:${identityId}`);
  const emailPrefix = identityType === "microsoft" ? "mc" : "offline";
  const syntheticEmail = `${emailPrefix}-${identityId}@chat.turbocraft.invalid`;
  const syntheticPassword = `TCL-${derivedSecret}`;

  const { error: createError } = await admin.auth.admin.createUser({
    email: syntheticEmail,
    password: syntheticPassword,
    email_confirm: true,
    user_metadata: {
      chat_identity_source: identityType,
      chat_identity_id: identityId,
    },
  });
  if (createError && !/already (been )?registered|already exists/i.test(createError.message)) {
    return json(503, { message: "Could not create the chat identity. Please try again." });
  }

  const { data, error: signInError } = await authClient.auth.signInWithPassword({
    email: syntheticEmail,
    password: syntheticPassword,
  });
  if (signInError || !data.session || !data.user) {
    return json(503, { message: "Could not start the chat session. Please try again." });
  }

  const { error: linkError } = await admin.from("chat_launcher_identities").insert({
    identity_type: identityType,
    identity_id: identityId,
    auth_user_id: data.user.id,
  });
  if (linkError) {
    const { data: savedLink, error: verifyLinkError } = await admin
      .from("chat_launcher_identities")
      .select("auth_user_id")
      .eq("identity_type", identityType)
      .eq("identity_id", identityId)
      .maybeSingle();
    if (verifyLinkError || savedLink?.auth_user_id !== data.user.id) {
      return json(409, { message: "This launcher account could not be linked to its chat profile." });
    }
  }

  return json(200, {
    access_token: data.session.access_token,
    refresh_token: data.session.refresh_token,
    expires_in: data.session.expires_in,
    token_type: data.session.token_type,
    user: { id: data.user.id },
  });
});
