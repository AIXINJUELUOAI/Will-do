"""只打包 manifest.json 和 main.js；输出到仓库 build/，不包含说明或网页样本。"""
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile
import hashlib
import json

source = Path(__file__).resolve().parent
root = source.parents[2]
manifest = json.loads((source / "manifest.json").read_text(encoding="utf-8"))
output = root / "build/link-source-examples" / (
    manifest["id"] + "-v" + manifest["version"] + ".zip"
)
output.parent.mkdir(parents=True, exist_ok=True)
with ZipFile(output, "w", compression=ZIP_DEFLATED) as archive:
    for name in ("manifest.json", "main.js"):
        archive.write(source / name, name)
with ZipFile(output) as archive:
    assert archive.testzip() is None
print(output)
print("SHA256:", hashlib.sha256(output.read_bytes()).hexdigest())
