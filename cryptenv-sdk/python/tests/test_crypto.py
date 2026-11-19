import base64
import hashlib
import os

import pytest
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from cryptenv.crypto import decrypt_with_key, encrypt_with_key


def test_roundtrip_v1():
    key = "super-secret-workspace-key"
    encrypted = encrypt_with_key("hello world", key)
    assert decrypt_with_key(encrypted, key) == "hello world"


def test_roundtrip_unicode_and_empty():
    key = "k" * 32
    assert decrypt_with_key(encrypt_with_key("", key), key) == ""
    assert decrypt_with_key(encrypt_with_key("pä££wörd🔐", key), key) == "pä££wörd🔐"


def test_short_key_is_zero_padded_like_server():
    # A 16-char key must derive the same 32-byte key as padding with zeros.
    encrypted = encrypt_with_key("value", "0123456789abcdef")
    assert decrypt_with_key(encrypted, "0123456789abcdef") == "value"


def test_wrong_key_raises():
    encrypted = encrypt_with_key("value", "correct-key")
    with pytest.raises(Exception):
        decrypt_with_key(encrypted, "wrong-key")


def test_decrypt_v2_payload():
    key = "workspace-passphrase"
    salt = os.urandom(16)
    iv = os.urandom(12)
    derived = hashlib.pbkdf2_hmac("sha256", key.encode(), salt, 120_000, 32)
    body = AESGCM(derived).encrypt(iv, b"v2-secret", None)
    payload = "v2:" + base64.b64encode(salt + iv + body).decode()
    assert decrypt_with_key(payload, key) == "v2-secret"


def test_decrypt_rejects_too_short_ciphertext():
    with pytest.raises(ValueError):
        decrypt_with_key(base64.b64encode(b"short").decode(), "key")
