import json
import os
from pathlib import Path

from cryptenv.config import normalize_api_url, resolve_config, resolve_master_key


def test_normalize_api_url():
    assert normalize_api_url("https://host.example/api/") == "https://host.example"
    assert normalize_api_url("https://host.example") == "https://host.example"
    assert normalize_api_url(None).startswith("https://")


def test_resolve_master_key_precedence(monkeypatch):
    monkeypatch.setenv("CRYPTENV_MASTER_KEY", "env-key")
    assert resolve_master_key({}) == "env-key"
    assert resolve_master_key({"master_key": "option-key"}) == "option-key"


def test_resolve_master_key_backward_compatible_env(monkeypatch):
    monkeypatch.delenv("CRYPTENV_MASTER_KEY", raising=False)
    monkeypatch.setenv("CRYPTENV_WORKSPACE_ENCRYPTION_KEY", "legacy-key")
    assert resolve_master_key({}) == "legacy-key"


def test_resolve_config_reads_cryptenv_json(tmp_path, monkeypatch):
    (tmp_path / ".cryptenv.json").write_text(
        json.dumps({"apiUrl": "https://api.example/api", "workspaceId": 42}),
        encoding="utf-8",
    )
    monkeypatch.chdir(tmp_path)
    for var in ("CRYPTENV_API_URL", "CRYPTENV_WORKSPACE_ID", "CRYPTENV_MASTER_KEY"):
        monkeypatch.delenv(var, raising=False)

    config = resolve_config({})
    assert config["api_url"] == "https://api.example"
    assert config["workspace_id"] == 42
