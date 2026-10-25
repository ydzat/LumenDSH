"""Exercise the real local boundary without synthesizing user text."""
import json
from pathlib import Path
import urllib.error
import urllib.request
import uuid

token = (Path.home() / "lumendsh-voice/token").read_text().strip()


def request(method, path, body=None, credential=token, origin=False):
    headers = {"Authorization": "Bearer " + credential}
    if origin: headers["Origin"] = "https://example.invalid"
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request("http://127.0.0.1:3086" + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


assert request("GET", "/health")[1]["engine"] == "lumendsh.silver_wolf.local"
assert request("GET", "/health", credential="invalid")[0] == 401
assert request("GET", "/health", origin=True)[0] == 401
assert request("POST", "/synthesize/" + str(uuid.uuid4()), {"text": ""})[0] == 400
path = "/synthesize/" + str(uuid.uuid4())
assert request("DELETE", path)[0] == 200
assert request("POST", path, {"text": "你好"})[0] == 409
print("REAL_HTTP_AUTH_VALIDATION_CANCEL_RACE_PASSED")
