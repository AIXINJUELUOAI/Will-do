"""Run after :app:compileNativeDebugKotlin. Check actual migration SQL using SQLite."""
from pathlib import Path
import json
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com/antgskds/calendarassistant"
source = (ROOT / f"app/src/main/java/{PACKAGE}/feature/schedule/data/db/EventsDatabase.kt").read_text(encoding="utf-8")
dao = (ROOT / f"app/src/main/java/{PACKAGE}/feature/quickmemo/data/local/QuickMemoDao.kt").read_text(encoding="utf-8")
generated = (ROOT / f"app/build/generated/ksp/nativeDebug/kotlin/{PACKAGE}/feature/schedule/data/db/EventsDatabase_Impl.kt").read_text(encoding="utf-8")
migration = source.split("internal val MIGRATION_20_21", 1)[1].split("internal val MIGRATION_19_20", 1)[0]
sqls = [json.loads('"' + raw + '"') for raw in re.findall(r'db.execSQL\("([^"]+)"\)', migration)]
assert sqls, "Missing migration statements"

db = sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys = ON")
old_schema = re.search(r"CREATE TABLE IF NOT EXISTS quick_memos \(.*?\)", source, re.S).group()
db.execute(old_schema)
old_source = source.split("internal val MIGRATION_19_20", 1)[1]
for raw in re.findall(r'db.execSQL\("(ALTER TABLE quick_memos [^"]+)"\)', old_source):
    db.execute(json.loads('"' + raw + '"'))
reference = sqlite3.connect(":memory:")
table_sqls = [json.loads('"' + raw + '"') for raw in re.findall(r'connection.execSQL\("([^"]+)"\)', generated)]
for sql in table_sqls:
    if re.match(r"CREATE TABLE.*`quick_memo", sql):
        reference.execute(sql)
        if sql.startswith("CREATE TABLE IF NOT EXISTS `quick_memo_reminders`") or sql.startswith("CREATE TABLE IF NOT EXISTS `quick_memo_suggestions`"):
            db.execute(sql)

row = dict(id=7, type="VOICE", body_text="旧正文", audio_path="synthetic/audio.m4a",
           image_path="synthetic/image.png", audio_duration_ms=2500, transcription_status="SUCCESS",
           analysis_status="NONE", created_at=100, updated_at=200, todo_state="ACTIVE",
           todo_pending_until=None, todo_completed_at=None, sort_rank=42, reminder_at=None, reminder_rrule="")
columns = list(row)
db.execute(f"INSERT INTO quick_memos ({','.join(columns)}) VALUES ({','.join('?' for _ in columns)})", list(row.values()))
db.execute("INSERT INTO quick_memo_reminders VALUES (1, 7, 900, '', 100, 200)")
db.execute("INSERT INTO quick_memo_suggestions VALUES (1, 7, 'SCHEDULE', 'PENDING', '{}', NULL, 100, 200)")
before_reminders = db.execute("SELECT * FROM quick_memo_reminders").fetchall()
before_suggestions = db.execute("SELECT * FROM quick_memo_suggestions").fetchall()
for sql in sqls:
    db.execute(sql)
assert db.execute(f"SELECT {','.join(columns)} FROM quick_memos").fetchone() == tuple(row.values())
assert db.execute("SELECT title, source_url, link_key, folder_id FROM quick_memos").fetchone() == ("", None, None, None)
assert db.execute("SELECT * FROM quick_memo_reminders").fetchall() == before_reminders
assert db.execute("SELECT * FROM quick_memo_suggestions").fetchall() == before_suggestions
assert db.execute("PRAGMA foreign_key_check").fetchall() == []

def info(connection, table):
    return {r[1]: r for r in connection.execute(f"PRAGMA table_info({table})")}
for table in ("quick_memos", "quick_memo_folders"):
    migrated, expected = info(db, table), info(reference, table)
    assert migrated.keys() == expected.keys()
    for name, col in expected.items():
        actual = migrated[name]
        assert (actual[2], actual[3], actual[5]) == (col[2], col[3], col[5]), (table, name)
        if name in ("title", "source_url", "link_key", "folder_id"):
            assert actual[4] == col[4], (table, name, actual[4], col[4])

def query(method):
    pattern = r'@Query\("([^"]+)"\)\s+suspend fun ' + method + r"\("
    return re.search(pattern, dao).group(1)

db.execute("INSERT INTO quick_memo_folders VALUES ('folder', '文章', 10, 20)")
db.execute(query("unassignFolder"), {"folderId": "missing", "now": 300})
db.execute("UPDATE quick_memos SET folder_id='folder', title='标题' WHERE id=7")
db.execute(query("unassignFolder"), {"folderId": "folder", "now": 300})
db.execute(query("deleteFolderRow"), {"id": "folder"})
assert db.execute("SELECT id, body_text, folder_id, title FROM quick_memos").fetchone() == (7, "旧正文", None, "标题")
assert db.execute("SELECT * FROM quick_memo_reminders").fetchall() == before_reminders
db.execute(query("updateBody"), {"id": 7, "body": "新正文", "now": 400})
assert db.execute("SELECT title, body_text FROM quick_memos").fetchone() == ("标题", "新正文")
print("PASS: migration 20->21 preserves memo IDs, attachments, reminders and suggestions; Room schema matches; folder removal preserves content.")
