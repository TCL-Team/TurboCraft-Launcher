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

function constantTimeEqual(left: string, right: string): boolean {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index++) {
    difference |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return difference === 0;
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

async function signInForChatUser(authUserId: string) {
  const { data, error } = await admin.auth.admin.getUserById(authUserId);
  const user = data.user;
  if (error || !user?.email) return { session: null, error };

  const metadata = user.user_metadata ?? {};
  const identityType = typeof metadata.chat_identity_source === "string"
    ? metadata.chat_identity_source.toLowerCase()
    : "";
  const identityId = typeof metadata.chat_identity_id === "string"
    ? metadata.chat_identity_id.toLowerCase()
    : "";
  if (!validIdentity(identityType, identityId)) return { session: null, error: new Error("Chat account credentials are invalid.") };

  const derivedSecret = await hmac(`chat:${identityType}:${identityId}`);
  const { data: signedIn, error: signInError } = await authClient.auth.signInWithPassword({
    email: user.email,
    password: `TCL-${derivedSecret}`,
  });
  return { session: signedIn.session, error: signInError };
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return json(405, { message: "Method not allowed." });
  if (!pepper || !anonKey || !serviceRoleKey) return json(503, { message: "Chat recovery is not configured." });

  let input: Record<string, unknown>;
  try {
    const value = await request.json();
    if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("Invalid body");
    input = value as Record<string, unknown>;
  } catch {
    return json(400, { message: "Invalid request." });
  }

  const action = typeof input.action === "string" ? input.action : "";
  const recoveryCode = typeof input.recovery_code === "string" ? input.recovery_code.trim() : "";
  if (!/^[A-Za-z0-9_-]{32,64}$/.test(recoveryCode)) {
    return json(400, { message: "Enter a valid chat recovery code." });
  }

  if (action === "set") {
    const accessToken = request.headers.get("authorization")?.match(/^Bearer\s+(.+)$/i)?.[1];
    if (!accessToken) return json(401, { message: "Sign in to chat before creating a recovery code." });
    const { data, error } = await authClient.auth.getUser(accessToken);
    if (error || !data.user) return json(401, { message: "Your chat session expired. Reopen Chat and try again." });

    const recoveryHash = await hmac(`chat-recovery:${data.user.id}:${recoveryCode}`);
    const { error: saveError } = await admin
      .from("chat_recovery_credentials")
      .upsert({
        auth_user_id: data.user.id,
        recovery_hash: recoveryHash,
        updated_at: new Date().toISOString(),
      }, { onConflict: "auth_user_id" });
    if (saveError) return json(503, { message: "Could not save the recovery code. Please try again." });
    return json(200, { ok: true });
  }

  if (action !== "recover") return json(400, { message: "Unsupported recovery action." });

  const username = typeof input.username === "string" ? input.username.trim().replace(/^@/, "").toLowerCase() : "";
  if (!/^[a-z0-9_.]{3,20}$/.test(username)) return json(400, { message: "Enter a valid 3-20 character chat username." });

  const identityType = typeof input.identity_type === "string" ? input.identity_type.toLowerCase() : "";
  const identityId = typeof input.identity_id === "string" ? input.identity_id.toLowerCase() : "";
  if (!validIdentity(identityType, identityId)) return json(400, { message: "Unsupported launcher account identity." });
  if (identityType === "microsoft") {
    const verificationError = await verifyMicrosoftIdentity(identityId, input.minecraft_access_token);
    if (verificationError) return verificationError;
  }

  const rawIp = request.headers.get("cf-connecting-ip")
    ?? request.headers.get("x-forwarded-for")?.split(",")[0]?.trim()
    ?? "unknown";
  const ipHash = await hmac(`chat-recovery-ip:${rawIp}`);
  const usernameHash = await hmac(`chat-recovery-username:${username}`);
  const { data: allowed, error: rateLimitError } = await admin.rpc("take_chat_recovery_rate_limit", {
    p_ip_hash: ipHash,
    p_username_hash: usernameHash,
  });
  if (rateLimitError) return json(503, { message: "Chat recovery is temporarily unavailable." });
  if (allowed !== true) return json(429, { message: "Too many recovery attempts. Please wait before trying again." });

  const { data: profile, error: profileError } = await admin
    .from("chat_profiles")
    .select("id")
    .eq("username", username)
    .maybeSingle();
  if (profileError) return json(503, { message: "Chat recovery is temporarily unavailable." });
  if (!profile?.id) return json(401, { message: "Username or recovery code is incorrect." });

  const { data: credential, error: credentialError } = await admin
    .from("chat_recovery_credentials")
    .select("recovery_hash")
    .eq("auth_user_id", profile.id)
    .maybeSingle();
  if (credentialError) return json(503, { message: "Chat recovery is temporarily unavailable." });
  const suppliedHash = await hmac(`chat-recovery:${profile.id}:${recoveryCode}`);
  if (!credential?.recovery_hash || !constantTimeEqual(credential.recovery_hash, suppliedHash)) {
    return json(401, { message: "Username or recovery code is incorrect." });
  }

  const { data: currentLink, error: linkLookupError } = await admin
    .from("chat_launcher_identities")
    .select("auth_user_id")
    .eq("identity_type", identityType)
    .eq("identity_id", identityId)
    .maybeSingle();
  if (linkLookupError) return json(503, { message: "Chat recovery is temporarily unavailable." });
  if (currentLink && currentLink.auth_user_id !== profile.id) {
    const { data: linkedProfile, error: linkedProfileError } = await admin
      .from("chat_profiles")
      .select("id")
      .eq("id", currentLink.auth_user_id)
      .maybeSingle();
    if (linkedProfileError) return json(503, { message: "Chat recovery is temporarily unavailable." });
    if (linkedProfile) {
      return json(409, { message: "This launcher account is already linked to another chat profile." });
    }
    const { error: deleteLinkError } = await admin
      .from("chat_launcher_identities")
      .delete()
      .eq("identity_type", identityType)
      .eq("identity_id", identityId)
      .eq("auth_user_id", currentLink.auth_user_id);
    if (deleteLinkError) return json(503, { message: "Chat recovery is temporarily unavailable." });
  }

  if (!currentLink || currentLink.auth_user_id !== profile.id) {
    const { error: linkInsertError } = await admin.from("chat_launcher_identities").insert({
      identity_type: identityType,
      identity_id: identityId,
      auth_user_id: profile.id,
    });
    if (linkInsertError) {
      const { data: confirmedLink } = await admin
        .from("chat_launcher_identities")
        .select("auth_user_id")
        .eq("identity_type", identityType)
        .eq("identity_id", identityId)
        .maybeSingle();
      if (confirmedLink?.auth_user_id !== profile.id) {
        return json(409, { message: "This launcher account could not be linked to the recovered chat profile." });
      }
    }
  }

  const { session, error: signInError } = await signInForChatUser(profile.id);
  if (signInError || !session) return json(503, { message: "Could not restore the chat session. Please try again." });

  return json(200, {
    access_token: session.access_token,
    refresh_token: session.refresh_token,
    expires_in: session.expires_in,
    token_type: session.token_type,
    user: { id: profile.id },
  });
});
