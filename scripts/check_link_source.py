"""Offline protocol-v1 preflight: no network, JS execution, AI or extra dependencies."""
import argparse
import copy
import io
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlsplit
from zipfile import BadZipFile, ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
# Protocol-v1 limits; keep in sync with ConfigCatalog and the Android validator.
SOURCE_BYTES, RESULT_BYTES, TEXT_CHARS = 2 * 1024 * 1024, 256 * 1024, 60_000
FILES, MEDIA, URLS, HTTP_CALLS, LOGINS = 128, 32, 5, 32, 16


class CheckError(ValueError):
    """Messages contain fixed field names/rules, never the rejected value."""


def need(condition, message):
    if not condition:
        raise CheckError(message)


def shape(value, required, optional=(), label="object"):
    need(type(value) is dict, label + ": expected object")
    need(not (set(required) - value.keys()), label + ": missing " + ", ".join(sorted(set(required) - value.keys())))
    need(not (value.keys() - set(required) - set(optional)), label + ": unknown fields")


def text(value, label, maximum=None, nonblank=False):
    need(type(value) is str, label + ": expected string")
    length = len(value.encode("utf-16-le")) // 2  # Kotlin String.length counts UTF-16 units.
    need(maximum is None or length <= maximum, label + ": too long")
    need(not nonblank or bool(value.strip()), label + ": blank string")
    return value


def array(value, label, maximum, nonempty=False):
    need(type(value) is list, label + ": expected array")
    need(len(value) <= maximum and (not nonempty or bool(value)), label + ": invalid item count")
    return value


def integer(value, label, minimum=None, maximum=None):
    need(type(value) is int, label + ": expected integer")
    need(minimum is None or value >= minimum, label + ": below minimum")
    need(maximum is None or value <= maximum, label + ": above maximum")


def url_host(value, label):
    text(value, label, nonblank=True)
    need(not re.search(r"[\x00-\x20\x7f]", value), label + ": invalid URL characters")
    uri = urlsplit(value)
    need(uri.scheme.lower() in ("http", "https") and uri.hostname and
         uri.username is None and uri.password is None and uri.port in (None, 80, 443),
         label + ": expected HTTP(S) URL without credentials")
    return uri.hostname.encode("idna").decode("ascii").lower().rstrip(".")


def host_rule(value):
    text(value, "host rule")
    host = value.removeprefix("*.")
    need(host == host.encode("idna").decode("ascii").lower() and "." in host and
         all(re.fullmatch(r"[a-z0-9](?:[a-z0-9-]*[a-z0-9])?", part) for part in host.split(".")) and
         not host.rsplit(".", 1)[1].isdigit(), "host rule: invalid domain")


def covers(rule, host):
    return host.endswith("." + rule[2:]) if rule.startswith("*.") else rule == host


def portable(path):
    return bool(path) and not path.startswith("/") and "\\" not in path and all(
        part not in (".", "..") and re.fullmatch(r"[a-zA-Z0-9_.-]+", part) for part in path.split("/")
    )


def manifest_check(data):
    shape(data, ("protocolVersion", "id", "name", "version", "matches", "permissions"),
          ("entry", "loginEntries"), "manifest")
    integer(data["protocolVersion"], "manifest.protocolVersion", 1, 1)
    need(re.fullmatch(r"[a-z][a-z0-9_.-]{2,63}", text(data["id"], "manifest.id")),
         "manifest.id: invalid ID")
    for key in ("name", "version"):
        text(data[key], "manifest." + key, 80, True)
    entry = text(data.get("entry", "main.js"), "manifest.entry")
    need(portable(entry) and entry.endswith(".js"), "manifest.entry: invalid module path")
    permissions = data["permissions"]
    shape(permissions, ("networkHosts",), ("browser", "replay"), "permissions")
    hosts = array(permissions["networkHosts"], "networkHosts", FILES, True)
    for rule in hosts:
        host_rule(rule)
    browser = permissions.get("browser", False)
    need(type(browser) is bool, "permissions.browser: expected boolean")
    for value in array(permissions.get("replay", []), "replay", HTTP_CALLS):
        text(value, "replay item", 200, True)
        need("\r" not in value and "\n" not in value, "replay item: newline")
    for match in array(data["matches"], "matches", FILES, True):
        shape(match, ("host",), ("pathPrefix",), "match")
        host_rule(match["host"])
        need(text(match.get("pathPrefix", "/"), "pathPrefix").startswith("/"),
             "pathPrefix: must start with /")
        need(any(rule == match["host"] or covers(rule, match["host"].removeprefix("*."))
                 for rule in hosts), "matches: domain missing from networkHosts")
    login_urls = []
    for login in array(data.get("loginEntries", []), "loginEntries", LOGINS):
        shape(login, ("name", "url"), ("userAgent",), "loginEntry")
        name = text(login["name"], "loginEntry.name", 80, True)
        need(not re.search(r"[\x00-\x1f\x7f-\x9f]", name), "loginEntry.name: control character")
        host = url_host(login["url"], "loginEntry.url")
        need(browser and urlsplit(login["url"]).scheme.lower() == "https" and
             any(covers(rule, host) for rule in hosts), "loginEntry: browser/HTTPS/domain permission missing")
        agent = login.get("userAgent")
        if agent is not None:
            text(agent, "loginEntry.userAgent", 512, True)
            need("\r" not in agent and "\n" not in agent, "loginEntry.userAgent: newline")
        login_urls.append(login["url"])
    need(len(set(login_urls)) == len(login_urls), "loginEntries: duplicate URLs")


def input_check(data):
    shape(data, ("requestId", "url"), ("protocolVersion", "shareText"), "input")
    integer(data.get("protocolVersion", 1), "input.protocolVersion", 1, 1)
    text(data["requestId"], "input.requestId", nonblank=True)
    url_host(data["url"], "input.url")
    if "shareText" in data:
        text(data["shareText"], "input.shareText")


def result_check(data, request):
    input_check(request)
    shape(data, ("requestId", "status"), ("protocolVersion", "source", "title", "author",
          "contentType", "body", "media", "warnings", "error"), "result")
    integer(data.get("protocolVersion", 1), "result.protocolVersion", 1, 1)
    need(text(data["requestId"], "result.requestId") == request["requestId"], "result.requestId: task mismatch")
    need(data["status"] in ("ok", "partial", "error"), "result.status: unsupported value")
    for key in ("title", "author"):
        text(data.get(key, ""), "result." + key, 512)
    need(data.get("contentType", "unknown") in ("article", "image_post", "video", "mixed", "unknown"),
         "result.contentType: unsupported value")
    source = data.get("source")
    if source is not None:
        shape(source, ("url",), ("canonicalUrl", "contentId"), "source")
        url_host(source["url"], "source.url")
        for key in ("canonicalUrl", "contentId"):
            text(source.get(key, ""), "source." + key)
    body = data.get("body", {})
    shape(body, (), ("kind", "format", "text"), "body")
    need(body.get("kind", "none") in ("full", "excerpt", "description", "none"), "body.kind: unsupported value")
    need(body.get("format", "plain") == "plain", "body.format: only plain is supported")
    text(body.get("text", ""), "body.text", TEXT_CHARS)
    orders = []
    for item in array(data.get("media", []), "media", MEDIA):
        shape(item, ("type", "role", "urls", "order"),
              ("headers", "mimeType", "durationMs", "group"), "media item")
        need(item["type"] in ("image", "audio", "video"), "media.type: unsupported value")
        need(item["role"] in ("content_image", "cover", "speech_audio", "background_music", "video"),
             "media.role: unsupported value")
        for url in array(item["urls"], "media.urls", URLS, True):
            url_host(url, "media URL")
        integer(item["order"], "media.order", 0, 2**31 - 1)
        orders.append(item["order"])
        text(item.get("group", ""), "media.group", 80)
        text(item.get("mimeType", ""), "media.mimeType")
        if item.get("durationMs") is not None:
            integer(item["durationMs"], "media.durationMs", 0, 2**63 - 1)
        headers = item.get("headers", {})
        need(type(headers) is dict and len(headers) <= MEDIA, "media.headers: invalid object/count")
        for key, value in headers.items():
            need(re.fullmatch(r"[a-zA-Z0-9-]+", key), "media header: invalid name")
            text(value, "media header", RESULT_BYTES)
            need("\r" not in value and "\n" not in value, "media header: newline")
    need(len(set(orders)) == len(orders), "media.order: must be globally unique")
    for warning in array(data.get("warnings", []), "warnings", MEDIA):
        text(warning, "warning", 512)
    error = data.get("error")
    if error is not None:
        shape(error, ("code", "message"), (), "error")
        text(error["code"], "error.code", nonblank=True)
        text(error["message"], "error.message")
    if data["status"] == "error":
        need(error is not None, "error result: missing error object")
    else:
        need(source is not None and source["url"] == request["url"], "source.url: must equal input.url verbatim")
        need(error is None, "successful result: must not contain an error")


def unique_object(pairs):
    value = {}
    for key, item in pairs:
        need(key not in value, "JSON: duplicate field")
        value[key] = item
    return value


def decode(raw):
    return json.loads(raw.decode("utf-8"), object_pairs_hook=unique_object,
                      parse_constant=lambda _: need(False, "JSON: non-finite number"))


def read_json(path, maximum):
    with Path(path).open("rb") as stream:
        raw = stream.read(maximum + 1)
    need(len(raw) <= maximum, "JSON file: exceeds byte limit")
    return decode(raw)


def package_check(raw):
    need(len(raw) <= SOURCE_BYTES, "ZIP: exceeds compressed size limit")
    files, total = {}, 0
    with ZipFile(io.BytesIO(raw)) as archive:
        need(len(archive.infolist()) <= FILES, "ZIP: too many entries")
        for entry in archive.infolist():
            need(portable(entry.filename.rstrip("/")), "ZIP: invalid entry path")
            if entry.is_dir():
                continue
            name = entry.filename
            need(name not in files and (name == "manifest.json" or name.endswith(".js")),
                 "ZIP: duplicate or unsupported file")
            with archive.open(entry) as stream:
                content = stream.read(SOURCE_BYTES - total + 1)
            total += len(content)
            need(total <= SOURCE_BYTES, "ZIP: exceeds uncompressed size limit")
            files[name] = content.decode("utf-8")
    need("manifest.json" in files, "ZIP: missing root manifest.json")
    manifest = decode(files["manifest.json"].encode("utf-8"))
    manifest_check(manifest)
    entry = manifest.get("entry", "main.js")
    need(entry in files and bool(files[entry].strip()), "ZIP: missing/empty entry module")
    return manifest


def examples_check(path):
    cases = read_json(path, SOURCE_BYTES)["cases"]
    need(type(cases) is list and bool(cases), "examples: expected cases")
    for case in cases:
        raw = json.dumps(case["result"], ensure_ascii=False).encode("utf-8")
        need(len(raw) <= RESULT_BYTES, "example result: exceeds byte limit")
        result_check(case["result"], case["input"])
    return cases


def self_test():
    cases = examples_check(ROOT / "examples/link-sources/protocol-cases.json")
    request, good = cases[0]["input"], cases[0]["result"]
    bad = []
    for key, value in (("extra", True), ("requestId", "other-task"), ("protocolVersion", True),
                       ("body", {"kind": "full", "format": "html", "text": "text"}),
                       ("body", {"text": "x" * (TEXT_CHARS + 1)}),
                       ("source", {"url": request["url"] + "?changed=1"}),
                       ("warnings", "not-an-array")):
        candidate = copy.deepcopy(good)
        candidate[key] = value
        bad.append(candidate)
    duplicate = copy.deepcopy(cases[-1]["result"])
    duplicate["media"][1]["order"] = duplicate["media"][0]["order"]
    bad.append((duplicate, cases[-1]["input"]))
    for candidate in bad:
        value, input_value = candidate if isinstance(candidate, tuple) else (candidate, request)
        try:
            result_check(value, input_value)
        except (ValueError, TypeError):
            continue
        raise AssertionError("Invalid result accepted")
    raw = io.BytesIO()
    import zipfile
    with zipfile.ZipFile(raw, "w", ZIP_DEFLATED) as archive:
        archive.writestr("../main.js", "export function extract() {}")
    try:
        package_check(raw.getvalue())
    except ValueError:
        pass
    else:
        raise AssertionError("Unsafe ZIP path accepted")
    return len(cases), len(bad) + 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    pack = commands.add_parser("package", help="Check ZIP structure and manifest; does not execute JS")
    pack.add_argument("zip")
    result = commands.add_parser("result", help="Check a saved extraction-result JSON against its input JSON")
    result.add_argument("json")
    result.add_argument("--input", required=True)
    examples = commands.add_parser("examples", help="Check offline teaching cases; no URLs are requested")
    examples.add_argument("json")
    commands.add_parser("self-test", help="Run the positive cases and negative boundary checks")
    args = parser.parse_args()
    try:
        if args.command == "package":
            with Path(args.zip).open("rb") as stream:
                raw = stream.read(SOURCE_BYTES + 1)
            package_check(raw)
            print("PASS: ZIP structure and manifest")
        elif args.command == "result":
            result_check(read_json(args.json, RESULT_BYTES), read_json(args.input, SOURCE_BYTES))
            print("PASS: result format and task/source identity")
        elif args.command == "examples":
            print("PASS:", len(examples_check(args.json)), "offline examples")
        else:
            positive, negative = self_test()
            print("PASS:", positive, "positive cases;", negative, "negative checks")
        return 0
    except CheckError as error:
        print("FAIL:", error, file=sys.stderr)
        return 1
    except (ValueError, TypeError, KeyError, OSError, UnicodeError, BadZipFile, RuntimeError):
        # Parser/IO exceptions can contain rejected data; report only a fixed message.
        print("FAIL: invalid JSON/URL/file; see the field/ZIP rules in the source guide", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
