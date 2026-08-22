export interface CryptEnvInitOptions {
  email?: string;
  password?: string;
  apiKey?: string;
  token?: string;
  workspaceId?: number | string;
  /** Preferred name for the local workspace encryption key. */
  masterKey?: string;
  /** @deprecated Use masterKey — kept for backward compatibility. */
  workspaceEncryptionKey?: string;
  apiUrl?: string;
}

export interface CryptEnvWorkspaceSummary {
  id: number;
  name: string;
  description: string | null;
  hasEncryptionKey: boolean;
  environments: Array<{
    id: number;
    name: string;
    isActive: boolean;
  }>;
}

export interface CryptEnvInitResult {
  success: boolean;
  user: {
    email: string | null;
    userId: number | null;
    username: string | null;
  };
  activeWorkspaceId: number | string | null;
  workspaceCount: number;
  loadedSecretCount: number;
}

export interface GetOptions {
  refresh?: boolean;
  throwOnMissing?: boolean;
}

export interface LoadOptions {
  /** Prefix applied to each secret key when setting process.env (default: ""). */
  prefix?: string;
  /** When false, existing process.env values are not overwritten (default: true). */
  overwrite?: boolean;
}

export interface SetOptions {
  environmentId?: number;
  description?: string;
}

export interface DeleteOptions {
  environmentId?: number;
}

export declare class CryptEnv {
  constructor(options?: CryptEnvInitOptions);
  init(options?: CryptEnvInitOptions): Promise<CryptEnvInitResult>;
  refresh(): Promise<number>;
  /** @deprecated Use refresh() */
  refreshEncryptedSecrets(): Promise<number>;
  listKeys(): string[];
  listWorkspaces(): CryptEnvWorkspaceSummary[];
  setActiveWorkspace(workspaceId: number | string): CryptEnvWorkspaceSummary;
  get(key: string, options?: GetOptions): string | undefined | null;
  getOrFetch(key: string): Promise<string>;
  getAll(): Promise<Record<string, string | undefined>>;
  load(options?: LoadOptions): Promise<number>;
  isInitialized(): boolean;
  getActiveWorkspace(): CryptEnvWorkspaceSummary | null;
  setSecret(key: string, value: string, options?: SetOptions): Promise<boolean>;
  deleteSecret(key: string, options?: DeleteOptions): Promise<boolean>;
}

declare const cryptenv: CryptEnv & { CryptEnv: typeof CryptEnv; default: CryptEnv };
export default cryptenv;
export { CryptEnv };
export as namespace cryptenv;
