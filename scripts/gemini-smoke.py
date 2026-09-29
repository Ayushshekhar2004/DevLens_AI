#!/usr/bin/env python3
"""Opt-in live Gemini check using the real Java provider and synthetic code only."""
import os
from pathlib import Path
import shutil
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
values = {}
config = root / ".env"
if config.exists():
    for line in config.read_text().splitlines():
        name, separator, value = line.partition("=")
        if separator and name.strip() in {"AI_API_KEY", "AI_MODEL"}:
            values[name.strip()] = value.strip().strip("\"'")
key = values.get("AI_API_KEY", "")
model = values.get("AI_MODEL", "")
if not key or key.startswith("replace_"):
    sys.exit("Save your Gemini key as AI_API_KEY in the root .env first. No request sent.")
if not model.startswith("gemini-"):
    sys.exit("Set AI_MODEL to your available Gemini model in the root .env first. No request sent.")
if not shutil.which("mvn"):
    sys.exit("Maven is required for this check. No request sent.")
env = os.environ.copy()
env.update(AI_API_KEY=key, AI_MODEL=model, DEVLENS_GEMINI_SMOKE="true")
print("Testing the application provider with synthetic Java code. Credentials are not printed.", flush=True)
result = subprocess.run(["mvn", "-q", "-Dtest=GeminiSnippetSmokeTest", "test"],
                        cwd=root / "backend", env=env)
if result.returncode == 0:
    print("PASS: Gemini returned a structured review accepted by the application parser.")
else:
    print("FAIL: Live Gemini check did not pass. This does not verify the database or full app.")
sys.exit(result.returncode)
