"""Client-side AES-256-GCM decryption for the CryptEnv Python SDK.

Payload formats (server side):
  * v1 (default): ``base64( IV[12] || ciphertext || tag[16] )`` where the key is
    the workspace passphrase padded/truncated to 32 bytes.
  * v2 (opt-in):   ``"v2:" + base64( salt[16] || IV[12] || ciphertext || tag[16] )``
    where the key is derived with PBKDF2-HMAC-SHA256 (120_000 iterations).

Encryption always emits the v1 format to stay compatible with the Java and
Node SDKs and with the server's current default.
"""

from __future__ import annotations

import base64
import hashlib
import os

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

V2_PREFIX = "v2:"
PBKDF2_ITERATIONS = 120_000
_SALT_LEN = 16
_IV_LEN = 12
_TAG_LEN = 16


def _derive_key_v1(passphrase: str) -> bytes:
    raw = passphrase.encode("utf-8")
    return (raw + b"\x00" * 32)[:32]


def _derive_key_v2(passphrase: str, salt: bytes) -> bytes:
    return hashlib.pbkdf2_hmac("sha256", passphrase.encode("utf-8"), salt, PBKDF2_ITERATIONS, 32)


def encrypt_with_key(plaintext: str, passphrase: str) -> str:
    """Encrypt ``plaintext`` with ``passphrase`` (v1 format)."""
    iv = os.urandom(_IV_LEN)
    body = AESGCM(_derive_key_v1(passphrase)).encrypt(iv, plaintext.encode("utf-8"), None)
    return base64.b64encode(iv + body).decode("ascii")


def decrypt_with_key(ciphertext_b64: str, passphrase: str) -> str:
    """Decrypt a v1 or v2 ciphertext produced by the CryptEnv server."""
    if not ciphertext_b64 or not isinstance(ciphertext_b64, str):
        raise ValueError("encrypted value must be a non-empty string")

    if ciphertext_b64.startswith(V2_PREFIX):
        return _decrypt_v2(ciphertext_b64[len(V2_PREFIX) :], passphrase)
    return _decrypt_v1(ciphertext_b64, passphrase)


def _decrypt_v1(ciphertext_b64: str, passphrase: str) -> str:
    combined = base64.b64decode(ciphertext_b64)
    if len(combined) < _IV_LEN + _TAG_LEN:
        raise ValueError("ciphertext too short")
    iv = combined[:_IV_LEN]
    body = combined[_IV_LEN:]
    return AESGCM(_derive_key_v1(passphrase)).decrypt(iv, body, None).decode("utf-8")


def _decrypt_v2(payload_b64: str, passphrase: str) -> str:
    combined = base64.b64decode(payload_b64)
    if len(combined) < _SALT_LEN + _IV_LEN + _TAG_LEN:
        raise ValueError("ciphertext too short")
    salt = combined[:_SALT_LEN]
    iv = combined[_SALT_LEN : _SALT_LEN + _IV_LEN]
    body = combined[_SALT_LEN + _IV_LEN :]
    return AESGCM(_derive_key_v2(passphrase, salt)).decrypt(iv, body, None).decode("utf-8")
