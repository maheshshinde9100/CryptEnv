"""Configuration resolution for the CryptEnv Python SDK.

Mirrors cryptenv-sdk/node/src/config.js so both SDKs read the same
environment variables and the same ``.cryptenv.json`` file.
"""

from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any, Dict, Optional

DEFAULT_API_HOST = "https://cryptenv-backend.onrender.com"


def normalize_api_url(url: Optional[str]) -> str:
    """Normalize an API URL to a host base without a trailing ``/api``.

    Accepts both ``https://host`` and ``https://host/api`` (CLI style).
    """
    if not url or not isinstance(url, str):
        return DEFAULT_API_HOST
    normalized = url.strip().rstrip("/")
    if normalized.endswith("/api"):
        normalized = normalized[: -len("/api")]
    return normalized


def read_cryptenv_json(cwd: Optional[str] = None) -> Optional[Dict[str, Any]]:
    path = Path(cwd or os.getcwd()) / ".cryptenv.json"
    if not path.is_file():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def resolve_master_key(options: Optional[Dict[str, Any]] = None) -> Optional[str]:
    """Workspace encryption key.

    ``CRYPTENV_MASTER_KEY`` is the preferred runtime name;
    ``CRYPTENV_WORKSPACE_ENCRYPTION_KEY`` is kept for backward compatibility.
    """
    options = options or {}
    return (
        options.get("master_key")
        or options.get("masterKey")
        or options.get("workspace_encryption_key")
        or options.get("workspaceEncryptionKey")
        or os.environ.get("CRYPTENV_MASTER_KEY")
        or os.environ.get("CRYPTENV_WORKSPACE_ENCRYPTION_KEY")
        or None
    )


def resolve_config(options: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    options = options or {}
    cryptenv_json = read_cryptenv_json() or {}

    api_url = normalize_api_url(
        options.get("api_url")
        or options.get("apiUrl")
        or os.environ.get("CRYPTENV_API_URL")
        or cryptenv_json.get("apiUrl")
        or DEFAULT_API_HOST
    )

    return {
        "api_url": api_url,
        "email": options.get("email") or os.environ.get("CRYPTENV_EMAIL"),
        "password": options.get("password") or os.environ.get("CRYPTENV_PASSWORD"),
        "api_key": options.get("api_key")
        or options.get("apiKey")
        or os.environ.get("CRYPTENV_API_KEY"),
        "token": options.get("token") or os.environ.get("CRYPTENV_TOKEN"),
        "workspace_id": options.get("workspace_id")
        or options.get("workspaceId")
        or os.environ.get("CRYPTENV_WORKSPACE_ID")
        or cryptenv_json.get("workspaceId"),
        "master_key": resolve_master_key(options),
        "workspace_name": options.get("workspace_name") or cryptenv_json.get("workspace"),
    }
