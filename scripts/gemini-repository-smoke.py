#!/usr/bin/env python3
"""Exercise explicit cloud repository consent and a tiny synthetic repository."""
import io
import json
import os
import secrets
import time
import urllib.error
import urllib.request
import zipfile

base = os.environ.get('DEVLENS_SMOKE_API_URL', 'http://localhost:8080').rstrip('/')
model = os.environ.get('DEVLENS_SMOKE_MODEL', 'gemini-3.1-flash-lite')
token = None

def request(method, path, body=None, content_type='application/json'):
    headers = {'Content-Type': content_type}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    data = body if isinstance(body, bytes) else json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=135) as response:
            return response.status, json.loads(response.read() or b'null')
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read() or b'null')

email = 'repo-smoke-' + secrets.token_hex(8) + '@example.invalid'
password = secrets.token_urlsafe(24)
status, _ = request('POST', '/api/auth/register', {'name': 'Repository Smoke', 'email': email, 'password': password})
assert status == 201, ('register', status)
status, auth = request('POST', '/api/auth/login', {'email': email, 'password': password})
assert status == 200, ('login', status)
token = auth['token']
archive = io.BytesIO()
with zipfile.ZipFile(archive, 'w') as z:
    z.writestr('src/Main.java', 'class Main {\n static int first(int[] values) {\n  return values[0];\n }\n}\n')
boundary = 'DevLens' + secrets.token_hex(10)
body = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="synthetic.zip"\r\nContent-Type: application/zip\r\n\r\n').encode() + archive.getvalue() + ('\r\n--' + boundary + '--\r\n').encode()
status, snapshot = request('POST', '/api/repositories/imports', body, 'multipart/form-data; boundary=' + boundary)
assert status == 201, ('import', status)
snapshot_id = snapshot['id']
status, scan = request('POST', f'/api/repositories/snapshots/{snapshot_id}/scan')
assert status == 201, ('scan', status)
print('PASS: synthetic ZIP import and deterministic scan', flush=True)
path = f'/api/repositories/snapshots/{snapshot_id}/analysis-jobs'
selection = {'profileId': 'gemini', 'model': model}
status, denied = request('POST', path, selection)
assert status == 400 and 'allowCloudProcessing' in denied.get('message', ''), ('cloud consent gate', status)
print('PASS: cloud processing denied without acknowledgement', flush=True)
selection['allowCloudProcessing'] = True
status, job = request('POST', path, selection)
assert status == 202, ('queue', status)
job_id = job['id']
print('Queued synthetic repository job:', job_id, flush=True)
for attempt in range(180):
    status, job = request('GET', f'/api/repositories/analysis-jobs/{job_id}')
    assert status == 200
    if job['status'] not in {'QUEUED', 'RUNNING'}:
        break
    time.sleep(1)
else:
    request('POST', f'/api/repositories/analysis-jobs/{job_id}/cancel')
    raise SystemExit('Timed out; cancellation requested.')
print('Job status:', job['status'], flush=True)
print('Job error:', job.get('errorMessage'), flush=True)
print('Budget:', json.dumps(job.get('budgetUsage', {})), flush=True)
status, summaries = request('GET', f'/api/repositories/analysis-jobs/{job_id}/summaries')
print('Summaries HTTP:', status, 'count:', len(summaries) if isinstance(summaries, list) else 0, flush=True)
status, report = request('GET', f'/api/repositories/analysis-jobs/{job_id}/report')
print('Report HTTP:', status, flush=True)
if isinstance(report, dict):
    print('Coverage:', report.get('coverageLabel'), flush=True)
assert job['status'] == 'COMPLETED', 'Repository did not complete; inspect job coverage and errors.'
assert status == 200 and summaries, 'Expected summaries and a repository report.'
print('PASS: live Gemini repository job completed. Synthetic user/snapshot retained for inspection.', flush=True)
