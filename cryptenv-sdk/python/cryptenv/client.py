"""CryptEnv Python client.

Fetches encrypted secrets from the CryptEnv backend and decrypts them
client-side with the workspace encryption key. Behaviour mirrors
``cryptenv-sdk`` for Node.js so both SDKs expose the same API surface.
"""

from __future__ import annotations

import os
import re
from typing import Any, Dict, List, Optional

import requests

from .config import resolve_config
from .crypto import decrypt_with_key, encrypt_with_key

_MASK_BEARER = re.compile(r"Bearer\s+\S+", re.IGNORECASE)
_MASK_APIKEY = re.compile(r"ce_live_[a-f0-9]+", re.IGNORECASE)


def _sanitize_error(err: BaseException) -> str:
    msg = getattr(err, "message", None) or str(err) or "Unknown error"
    if isinstance(err, requests.HTTPError) and err.response is not None:
        try:
            data = err.response.json()
            msg = data.get("message") or data.get("error") or err.response.reason or msg
        except ValueError:
            msg = err.response.reason or msg
    msg = str(msg)
    msg = _MASK_BEARER.sub("Bearer [redacted]", msg)
    msg = _MASK_APIKEY.sub("ce_live_[redacted]", msg)
    return msg


def _map_workspace_from_login(dto: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "id": dto.get("id"),
        "name": dto.get("name"),
        "description": dto.get("description"),
        "hasEncryptionKey": bool(dto.get("hasEncryptionKey")),
        "environments": dto.get("environments") or [],
    }


def _map_workspace_from_api(dto: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "id": dto.get("id"),
        "name": dto.get("name"),
        "description": dto.get("description"),
        "hasEncryptionKey": bool(dto.get("hasEncryptionKey")),
        "environments": [],
    }


class CryptEnv:
    """CryptEnv SDK client."""

    def __init__(self, options: Optional[Dict[str, Any]] = None) -> None:
        if options is not None and not isinstance(options, dict):
            options = dict(options)
        self._apply_config(resolve_config(options or {}))
        self.user_id: Optional[Any] = None
        self.username: Optional[str] = None
        self.workspaces: List[Dict[str, Any]] = []
        self.initialized = False
        self.encrypted_secrets_map: Dict[str, str] = {}
        self.plaintext_cache: Dict[str, str] = {}
        self._session: Optional[requests.Session] = None

    # --- configuration ----------------------------------------------------
    def _apply_config(self, config: Dict[str, Any]) -> None:
        self.email = config.get("email")
        self.password = config.get("password")
        self.api_key = config.get("api_key")
        self.token = config.get("token")
        self.workspace_id = config.get("workspace_id")
        self.master_key = config.get("master_key")
        self.api_url = config.get("api_url")
        self.workspace_name = config.get("workspace_name")

    def _session_or_create(self) -> requests.Session:
        if self._session is None:
            self._session = requests.Session()
            self._session.headers.update({"Content-Type": "application/json"})
        session = self._session
        session.headers.pop("Authorization", None)
        session.headers.pop("X-API-Key", None)
        if self.token:
            session.headers["Authorization"] = f"Bearer {self.token}"
        elif self.api_key:
            session.headers["X-API-Key"] = self.api_key
        return session

    def _request(self, method: str, path: str, **kwargs) -> requests.Response:
        session = self._session_or_create()
        url = f"{self.api_url}{path}"
        response = session.request(method, url, timeout=30, **kwargs)
        response.raise_for_status()
        return response

    def _require_master_key(self) -> None:
        if not self.master_key:
            raise RuntimeError(
                "CryptEnv SDK: master key not configured. Set CRYPTENV_MASTER_KEY in the "
                "environment (or CRYPTENV_WORKSPACE_ENCRYPTION_KEY for backward compatibility). "
                "This is the workspace encryption key you set when creating the workspace."
            )

    def _select_workspace(self) -> None:
        if self.workspace_id is not None:
            return
        if self.workspace_name and self.workspaces:
            wanted = str(self.workspace_name).lower()
            for ws in self.workspaces:
                if ws.get("name") and str(ws["name"]).lower() == wanted:
                    self.workspace_id = ws["id"]
                    return
        if self.workspaces:
            first_with_key = next((w for w in self.workspaces if w.get("hasEncryptionKey")), None)
            self.workspace_id = (first_with_key or self.workspaces[0])["id"]

    # --- authentication ---------------------------------------------------
    def _authenticate_with_password(self) -> None:
        response = self._request(
            "POST", "/api/sdk/login", json={"email": self.email, "password": self.password}
        )
        data = response.json()
        self.token = data.get("token")
        self.user_id = data.get("userId")
        self.email = data.get("email")
        self.username = data.get("username")
        self.workspaces = [_map_workspace_from_login(w) for w in data.get("workspaces") or []]
        self._session = None

    def _authenticate_with_api_key(self) -> None:
        response = self._request("GET", "/api/workspaces")
        self.workspaces = [_map_workspace_from_api(w) for w in response.json() or []]
        self.initialized = True

    def _authenticate_with_token(self) -> None:
        me = self._request("GET", "/api/auth/me").json()
        self.user_id = me.get("id")
        self.email = me.get("email")
        self.username = me.get("username")
        ws = self._request("GET", "/api/workspaces").json()
        self.workspaces = [_map_workspace_from_api(w) for w in ws or []]

    # --- public API -------------------------------------------------------
    def init(self, options: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        if options:
            self._apply_config(resolve_config(options))
            self._session = None

        has_password = bool(self.email and self.password)
        has_api_key = bool(self.api_key)
        has_token = bool(self.token)

        if not (has_password or has_api_key or has_token):
            raise RuntimeError(
                "CryptEnv SDK: missing credentials. Set CRYPTENV_EMAIL and CRYPTENV_PASSWORD, "
                "or CRYPTENV_API_KEY, or CRYPTENV_TOKEN, or pass them to init()."
            )

        try:
            if has_password:
                self._authenticate_with_password()
            elif has_token:
                self._authenticate_with_token()
            else:
                self._authenticate_with_api_key()

            self.initialized = True
            self._select_workspace()

            if not self.workspace_id:
                raise RuntimeError(
                    "CryptEnv SDK: no workspace available. Set CRYPTENV_WORKSPACE_ID or create a "
                    "workspace first."
                )

            self.refresh()
            return self._summary()
        except Exception as err:  # noqa: BLE001 - surfaced as a single SDK error
            raise RuntimeError(f"CryptEnv SDK init failed: {_sanitize_error(err)}") from err

    def refresh(self) -> int:
        if not self.initialized:
            raise RuntimeError("CryptEnv SDK not initialized. Call init() first.")
        if not self.workspace_id:
            raise RuntimeError("No workspace selected. Set CRYPTENV_WORKSPACE_ID.")
        try:
            response = self._request(
                "GET", f"/api/sdk/workspaces/{self.workspace_id}/encrypted-secrets-map"
            )
            self.encrypted_secrets_map = dict(response.json() or {})
            self.plaintext_cache.clear()
            return len(self.encrypted_secrets_map)
        except Exception as err:  # noqa: BLE001
            raise RuntimeError(f"CryptEnv SDK refresh failed: {_sanitize_error(err)}") from err

    def refresh_encrypted_secrets(self) -> int:
        """Deprecated: use :meth:`refresh`."""
        return self.refresh()

    def list_keys(self) -> List[str]:
        if not self.initialized:
            raise RuntimeError("CryptEnv SDK not initialized. Call init() first.")
        return list(self.encrypted_secrets_map.keys())

    def list_workspaces(self) -> List[Dict[str, Any]]:
        if not self.initialized:
            raise RuntimeError("CryptEnv SDK not initialized. Call init() first.")
        return self.workspaces

    def set_active_workspace(self, workspace_id: Any) -> Dict[str, Any]:
        if not workspace_id:
            raise RuntimeError("workspace_id is required")
        ws = next((w for w in self.workspaces if str(w["id"]) == str(workspace_id)), None)
        if ws is None:
            raise RuntimeError(f"Workspace {workspace_id} not found in your workspaces.")
        self.workspace_id = ws["id"]
        self.encrypted_secrets_map.clear()
        self.plaintext_cache.clear()
        return ws

    def get(self, key: str, refresh: bool = False, throw_on_missing: bool = True) -> Optional[str]:
        if not self.initialized:
            raise RuntimeError("CryptEnv SDK not initialized. Call init() first.")
        if not key:
            return None
        if refresh:
            self.plaintext_cache.pop(key, None)
        elif key in self.plaintext_cache:
            return self.plaintext_cache[key]

        enc = self.encrypted_secrets_map.get(key)
        if enc is None:
            if not throw_on_missing:
                return None
            raise RuntimeError(
                f"Secret '{key}' not found in workspace {self.workspace_id}. "
                f"Available keys: {', '.join(self.list_keys()) or '(none)'}"
            )

        self._require_master_key()
        try:
            plain = decrypt_with_key(enc, self.master_key)
        except Exception as err:  # noqa: BLE001
            raise RuntimeError(
                f"Failed to decrypt secret '{key}'. Verify CRYPTENV_MASTER_KEY matches the "
                "workspace encryption key."
            ) from err
        self.plaintext_cache[key] = plain
        return plain

    def get_or_fetch(self, key: str) -> Optional[str]:
        if not self.initialized:
            raise RuntimeError("CryptEnv SDK not initialized. Call init() first.")
        if key in self.encrypted_secrets_map:
            return self.get(key)
        try:
            response = self._request("GET", f"/api/sdk/secrets/encrypted/{key}")
            self.encrypted_secrets_map[key] = response.json().get("encryptedValue")
            return self.get(key)
        except requests.HTTPError as err:
            if err.response is not None and err.response.status_code == 404:
                raise RuntimeError(f"Secret '{key}' not found") from err
            raise RuntimeError(f"CryptEnv SDK fetch failed: {_sanitize_error(err)}") from err
        except Exception as err:  # noqa: BLE001
            raise RuntimeError(f"CryptEnv SDK fetch failed: {_sanitize_error(err)}") from err

    def get_all(self) -> Dict[str, Optional[str]]:
        if not self.initialized:
            raise RuntimeError("CryptEnv SDK not initialized. Call init() first.")
        if not self.encrypted_secrets_map:
            self.refresh()
        return {key: self.get(key, throw_on_missing=False) for key in self.encrypted_secrets_map}

    def load(self, prefix: str = "", overwrite: bool = True) -> int:
        """Decrypt all secrets and inject them into ``os.environ`` (no disk writes)."""
        if not self.initialized:
            self.init()

        loaded = 0
        for key, value in self.get_all().items():
            if value is None:
                continue
            env_key = f"{prefix}{key}"
            if overwrite or env_key not in os.environ:
                os.environ[env_key] = value
                loaded += 1
        return loaded

    def is_initialized(self) -> bool:
        return self.initialized

    def get_active_workspace(self) -> Optional[Dict[str, Any]]:
        if not self.workspace_id:
            return None
        return next(
            (w for w in self.workspaces if str(w["id"]) == str(self.workspace_id)), None
        )

    def _summary(self) -> Dict[str, Any]:
        return {
            "success": True,
            "user": {"email": self.email, "userId": self.user_id, "username": self.username},
            "activeWorkspaceId": self.workspace_id,
            "workspaceCount": len(self.workspaces),
            "loadedSecretCount": len(self.encrypted_secrets_map),
        }

    # --- secret management (prefer cryptenv-cli for operator workflows) ----
    def set_secret(
        self,
        key: str,
        value: Any,
        environment_id: Optional[Any] = None,
        description: str = "",
    ) -> bool:
        if not self.initialized:
            raise RuntimeError("CryptEnv SDK not initialized. Call init() first.")
        if not key or value is None:
            raise RuntimeError("key and value are required")

        plain = str(value)
        if environment_id:
            payload = {
                "key": key,
                "value": plain,
                "environmentId": environment_id,
                "description": description,
                "encrypted": True,
            }
            try:
                self._request("POST", "/api/secrets", json=payload)
            except requests.HTTPError as err:
                already_exists = err.response is not None and err.response.status_code == 409
                if not already_exists and err.response is not None:
                    try:
                        already_exists = bool(re.search(r"already exists", err.response.text, re.I))
                    except ValueError:
                        already_exists = False
                if already_exists:
                    self._request(
                        "PUT",
                        f"/api/secrets/environment/{environment_id}/{key}",
                        json={"value": plain},
                    )
                else:
                    raise RuntimeError(f"Failed to save secret: {_sanitize_error(err)}") from err
            except Exception as err:  # noqa: BLE001
                raise RuntimeError(f"Failed to save secret: {_sanitize_error(err)}") from err
            self.refresh()
            self.plaintext_cache[key] = plain
            return True

        self._require_master_key()
        self.encrypted_secrets_map[key] = encrypt_with_key(plain, self.master_key)
        self.plaintext_cache[key] = plain
        return True

    def delete_secret(self, key: str, environment_id: Optional[Any] = None) -> bool:
        if not self.initialized:
            raise RuntimeError("CryptEnv SDK not initialized. Call init() first.")
        try:
            if environment_id:
                self._request("DELETE", f"/api/secrets/environment/{environment_id}/{key}")
            else:
                self._request("DELETE", f"/api/secrets/{key}")
            self.encrypted_secrets_map.pop(key, None)
            self.plaintext_cache.pop(key, None)
            return True
        except Exception as err:  # noqa: BLE001
            raise RuntimeError(f"Failed to delete secret: {_sanitize_error(err)}") from err


# Module-level default instance, mirroring the Node SDK's default export.
default = CryptEnv()
