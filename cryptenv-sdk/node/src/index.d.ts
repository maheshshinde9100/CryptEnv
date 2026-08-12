export interface CryptEnvInitOptions {
  email?: string;
  password?: string;
  workspaceId?: number | string;
  workspaceEncryptionKey?: string;
  apiUrl?: string;
  token?: string;
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
    email: string;
    userId: number;
    username: string;
  };
  activeWorkspaceId: number | null;
  workspaceCount: number;
  loadedSecretCount: number;
}

export interface GetOptions {
  refresh?: boolean;
  throwOnMissing?: boolean;
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
  refreshEncryptedSecrets(): Promise<number>;
  listKeys(): Promise<string[]>;
  listWorkspaces(): CryptEnvWorkspaceSummary[];
  setActiveWorkspace(workspaceId: number | string): CryptEnvWorkspaceSummary;
  get(key: string, options?: GetOptions): string | undefined;
  getOrFetch(key: string): Promise<string>;
  getAll(): Promise<Record<string, string | undefined>>;
  setSecret(key: string, value: string, options?: SetOptions): Promise<boolean>;
  deleteSecret(key: string, options?: DeleteOptions): Promise<boolean>;
  isInitialized(): boolean;
  getActiveWorkspace(): CryptEnvWorkspaceSummary | null;
}

declare const cryptenv: CryptEnv & { CryptEnv: typeof CryptEnv; default: CryptEnv };
export default cryptenv;
export { CryptEnv };
export as namespace cryptenv;
