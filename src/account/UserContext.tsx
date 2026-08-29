import React, {createContext, useCallback, useContext, useEffect, useMemo, useState} from 'react';
import {Platform} from 'react-native';

export interface RecipeTreeUser {
  displayName: string;
}

type AccountStatus = 'loading' | 'anonymous' | 'authenticated' | 'error';

interface UserContextValue {
  status: AccountStatus;
  user: RecipeTreeUser | null;
  revision: number;
  error: string | null;
  signIn(): void;
  signOut(): Promise<void>;
  refresh(): Promise<void>;
}

const UserContext = createContext<UserContextValue | null>(null);

function parseSession(value: unknown): RecipeTreeUser | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Account session response is not an object.');
  }
  const record = value as Record<string, unknown>;
  if (Object.keys(record).length !== 1) {
    throw new Error('Account session response has an invalid shape.');
  }
  if (record.user === null) return null;
  if (!record.user || typeof record.user !== 'object' || Array.isArray(record.user)) {
    throw new Error('Account session response contains an invalid user.');
  }
  const user = record.user as Record<string, unknown>;
  if (
    Object.keys(user).length !== 1 ||
    typeof user.displayName !== 'string' ||
    user.displayName.length === 0 ||
    user.displayName.length > 80
  ) {
    throw new Error('Account session response contains invalid profile data.');
  }
  return {displayName: user.displayName};
}

export function UserProvider({children}: {children: React.ReactNode}) {
  const [status, setStatus] = useState<AccountStatus>('loading');
  const [user, setUser] = useState<RecipeTreeUser | null>(null);
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (Platform.OS !== 'web') {
      setStatus('anonymous');
      setUser(null);
      return;
    }
    try {
      const response = await fetch('/api/auth/session', {
        headers: {Accept: 'application/json'},
        cache: 'no-store',
        credentials: 'include',
      });
      if (!response.ok) throw new Error(`Account session returned HTTP ${response.status}.`);
      const nextUser = parseSession(await response.json());
      setUser(nextUser);
      setStatus(nextUser ? 'authenticated' : 'anonymous');
      setError(null);
      setRevision(value => value + 1);
    } catch (cause) {
      console.error('Account session could not be loaded.', cause);
      setUser(null);
      setStatus('error');
      setError('Account sync is temporarily unavailable.');
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const signIn = useCallback(() => {
    if (Platform.OS !== 'web' || typeof window === 'undefined') {
      console.error('Discord sign-in is currently available only in the web app.');
      return;
    }
    const returnTo = `${window.location.pathname}${window.location.search}${window.location.hash}`;
    window.location.assign(`/api/auth/discord/start?${new URLSearchParams({returnTo})}`);
  }, []);

  const signOut = useCallback(async () => {
    if (Platform.OS !== 'web') return;
    const response = await fetch('/api/auth/signout', {
      method: 'POST',
      headers: {Accept: 'application/json'},
      credentials: 'include',
    });
    if (!response.ok) throw new Error(`Account sign-out returned HTTP ${response.status}.`);
    setUser(null);
    setStatus('anonymous');
    setError(null);
    setRevision(value => value + 1);
  }, []);

  const value = useMemo<UserContextValue>(
    () => ({status, user, revision, error, signIn, signOut, refresh}),
    [error, refresh, revision, signIn, signOut, status, user],
  );
  return <UserContext.Provider value={value}>{children}</UserContext.Provider>;
}

export function useUser(): UserContextValue {
  const value = useContext(UserContext);
  if (!value) throw new Error('useUser must be used inside UserProvider.');
  return value;
}
