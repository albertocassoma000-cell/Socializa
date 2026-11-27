// Web (Next.js / React) — Integração Firebase Authentication + Supabase Third-Party Auth
// Mantém identidade única (Firebase UID) e validação criptográfica dos Firebase ID Tokens pelo Supabase.

import { initializeApp, getApps } from "firebase/app";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signOut as firebaseSignOut,
  onIdTokenChanged,
  User,
} from "firebase/auth";
import { createClient, SupabaseClient } from "@supabase/supabase-js";

const firebaseConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY!,
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN!,
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID || "gen-lang-client-0990149874",
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID!,
};

const firebaseApp = getApps().length === 0 ? initializeApp(firebaseConfig) : getApps()[0];
export const firebaseAuth = getAuth(firebaseApp);

// Cliente Supabase configurado com Third-Party Auth:
// envia automaticamente o Firebase ID Token válido em todos os pedidos PostgREST/Storage/Realtime.
export const supabase: SupabaseClient = createClient(
  process.env.NEXT_PUBLIC_SUPABASE_URL!,
  process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
  {
    accessToken: async () => {
      const user = firebaseAuth.currentUser;
      return user ? await user.getIdToken() : null;
    },
  }
);

export async function signInWithGoogleWeb(): Promise<User> {
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: "select_account" });
  const result = await signInWithPopup(firebaseAuth, provider);
  await syncProfileWithSupabase(result.user);
  return result.user;
}

export async function syncProfileWithSupabase(user: User): Promise<void> {
  // Chama o RPC ensure_firebase_profile: o UID é extraído no servidor via public.firebase_uid() (JWT sub)
  await supabase.rpc("ensure_firebase_profile", {
    p_full_name: user.displayName ?? "Cidadão Meu País",
    p_avatar_url: user.photoURL ?? null,
    p_province: "Luanda",
    p_country: "Angola",
  });
}

export function observeAuthSession(onChange: (user: User | null) => void): () => void {
  return onIdTokenChanged(firebaseAuth, async (user) => {
    if (user) {
      await syncProfileWithSupabase(user);
    }
    onChange(user);
  });
}

export async function signOutWeb(): Promise<void> {
  await firebaseSignOut(firebaseAuth);
}
