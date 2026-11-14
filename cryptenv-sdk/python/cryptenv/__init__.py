"""CryptEnv Python SDK — client-side secret decryption for CryptEnv workspaces."""

from .client import CryptEnv, default
from .config import resolve_config, resolve_master_key
from .crypto import decrypt_with_key, encrypt_with_key

__version__ = "1.0.0"

__all__ = [
    "CryptEnv",
    "default",
    "resolve_config",
    "resolve_master_key",
    "decrypt_with_key",
    "encrypt_with_key",
    "__version__",
]
