#!/usr/bin/env python3
"""Verify SmartWash DDL using an owned, temporary MySQL 8 instance.

No existing MySQL service, option file, login-path file, or application database is
used. Test accounts and rows exist only in the temporary instance. This runner
uses Python's standard library and the mysql/mysqld/mysqladmin command-line tools.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import time
from typing import Iterable


PREFIX = "smartwash-schema-v2-"
MIN_VERSION = (8, 0, 16)
LEGACY_TABLES = (
    "t_user", "cleaning_system", "cleaning_winch", "cleaning_rope_sensor",
    "cleaning_telemetry_batch", "cleaning_telemetry_segment",
    "cleaning_segment_measurement",
)
FRESH_DB = "fresh_db"
UPGRADE_DB = "upgrade_db"
REJECT_SLOT_DB = "reject_slot_db"
REJECT_PRIMARY_DB = "reject_primary_db"
REJECT_AUTO_DB = "reject_auto_db"
LEGACY_GUARD_DB = "legacy_guard_db"
RUNTIME_UPGRADE_DB = "runtime_upgrade_db"
TEST_DATABASES = frozenset((FRESH_DB, UPGRADE_DB, REJECT_SLOT_DB,
                            REJECT_PRIMARY_DB, REJECT_AUTO_DB, LEGACY_GUARD_DB,RUNTIME_UPGRADE_DB))
V2_TABLE_COUNT = 19
CREATE_FLAGS = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0


class VerificationError(RuntimeError):
    pass


def run_command(args: Iterable[str], *, env: dict[str, str], timeout: int = 60,
                input_text: str | None = None, cwd: Path | None = None,
                check: bool = True) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        list(map(str, args)), input=input_text, capture_output=True,
        encoding="utf-8", errors="replace", timeout=timeout, shell=False,
        env=env, cwd=cwd, creationflags=CREATE_FLAGS,
    )
    if check and result.returncode:
        detail = (result.stderr or result.stdout).strip()[-6000:]
        raise VerificationError(f"Command failed ({Path(str(result.args[0])).name}): {detail}")
    return result


def executable(name: str, bin_dir: Path | None) -> Path:
    suffix = ".exe" if os.name == "nt" else ""
    candidate = bin_dir / f"{name}{suffix}" if bin_dir else None
    resolved = str(candidate) if candidate and candidate.is_file() else (
        shutil.which(name) if bin_dir is None else None
    )
    if not resolved:
        raise VerificationError(f"Cannot find {name}; pass --mysql-bin-dir.")
    return Path(resolved).resolve()


def without_comments(sql: str) -> str:
    return re.sub(r"/\*.*?\*/|--[^\r\n]*|#[^\r\n]*", " ", sql, flags=re.S)


def table_names_from_sql(sql: str) -> set[str]:
    return set(re.findall(
        r"\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([a-zA-Z0-9_]+)`?",
        without_comments(sql), flags=re.I,
    ))


def assert_additive_sql(label: str, sql: str) -> None:
    clean = without_comments(sql)
    forbidden = (
        r"\bDROP\s+(?:TEMPORARY\s+)?(?:TABLE|DATABASE|SCHEMA)\b",
        r"\bTRUNCATE\b", r"\bDELETE\s+(?:FROM\b|[a-zA-Z_`]+\s+FROM\b)",
        r"\bALTER\s+TABLE\b[^;]*\bDROP\s+(?:COLUMN\b|PRIMARY\s+KEY\b)",
    )
    if any(re.search(pattern, clean, re.I | re.S) for pattern in forbidden):
        raise VerificationError(f"{label} contains destructive table/data SQL.")
    if re.search(r"^\s*(?:SOURCE\b|\\\.)", clean, re.I | re.M):
        raise VerificationError(f"{label} must be self-contained; SOURCE is not supported.")


def reserve_free_port() -> int:
    for _ in range(40):
        port = 20000 + secrets.randbelow(40000)
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
            if hasattr(socket, "SO_EXCLUSIVEADDRUSE"):
                listener.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
            try:
                listener.bind(("127.0.0.1", port))
            except OSError:
                continue
            return port
    raise VerificationError("Cannot reserve a free high loopback port.")


def process_snapshot() -> dict[int, tuple[int, str]]:
    """Read only PID/parent/name metadata, never existing server arguments."""
    if os.name != "nt":
        processes = {}
        for directory in Path("/proc").iterdir():
            if not directory.name.isdigit():
                continue
            try:
                stat = (directory / "stat").read_text()
                name = stat[stat.index("(") + 1:stat.rindex(")")]
                parent = int(stat[stat.rindex(")") + 2:].split()[1])
                processes[int(directory.name)] = (parent, name)
            except (OSError, ValueError, IndexError):
                continue
        return processes
    import ctypes
    from ctypes import wintypes

    class ProcessEntry(ctypes.Structure):
        _fields_ = [
            ("dwSize", wintypes.DWORD), ("cntUsage", wintypes.DWORD),
            ("th32ProcessID", wintypes.DWORD), ("th32DefaultHeapID", ctypes.c_size_t),
            ("th32ModuleID", wintypes.DWORD), ("cntThreads", wintypes.DWORD),
            ("th32ParentProcessID", wintypes.DWORD), ("pcPriClassBase", wintypes.LONG),
            ("dwFlags", wintypes.DWORD), ("szExeFile", wintypes.WCHAR * 260),
        ]
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel.CreateToolhelp32Snapshot.argtypes = (wintypes.DWORD, wintypes.DWORD)
    kernel.CreateToolhelp32Snapshot.restype = wintypes.HANDLE
    kernel.Process32FirstW.argtypes = (wintypes.HANDLE, ctypes.POINTER(ProcessEntry))
    kernel.Process32FirstW.restype = wintypes.BOOL
    kernel.Process32NextW.argtypes = (wintypes.HANDLE, ctypes.POINTER(ProcessEntry))
    kernel.Process32NextW.restype = wintypes.BOOL
    kernel.CloseHandle.argtypes = (wintypes.HANDLE,)
    handle = kernel.CreateToolhelp32Snapshot(0x00000002, 0)
    if handle == ctypes.c_void_p(-1).value:
        raise VerificationError("Cannot inspect process ancestry; directory cleanup will be refused.")
    entry = ProcessEntry()
    entry.dwSize = ctypes.sizeof(entry)
    processes = {}
    try:
        found = kernel.Process32FirstW(handle, ctypes.byref(entry))
        while found:
            processes[int(entry.th32ProcessID)] = (int(entry.th32ParentProcessID), entry.szExeFile)
            found = kernel.Process32NextW(handle, ctypes.byref(entry))
    finally:
        kernel.CloseHandle(handle)
    return processes


def process_info(pid: int) -> dict:
    """False means definitely absent/exited; None means insufficient access."""
    if os.name != "nt":
        directory = Path("/proc") / str(pid)
        if not directory.exists():
            return {"alive": False, "executable": None}
        try:
            return {"alive": True, "executable": str((directory / "exe").resolve(strict=True))}
        except OSError as failure:
            return {"alive": None, "executable": None, "error": str(failure)}
    import ctypes
    from ctypes import wintypes
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel.OpenProcess.argtypes = (wintypes.DWORD, wintypes.BOOL, wintypes.DWORD)
    kernel.OpenProcess.restype = wintypes.HANDLE
    kernel.GetExitCodeProcess.argtypes = (wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD))
    kernel.GetExitCodeProcess.restype = wintypes.BOOL
    kernel.QueryFullProcessImageNameW.argtypes = (
        wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD),
    )
    kernel.QueryFullProcessImageNameW.restype = wintypes.BOOL
    kernel.CloseHandle.argtypes = (wintypes.HANDLE,)
    handle = kernel.OpenProcess(0x1000, False, pid)
    if not handle:
        error = ctypes.get_last_error()
        return {"alive": False if error == 87 else None, "executable": None, "error": error}
    try:
        code = wintypes.DWORD()
        if not kernel.GetExitCodeProcess(handle, ctypes.byref(code)):
            return {"alive": None, "executable": None, "error": ctypes.get_last_error()}
        if code.value != 259:
            return {"alive": False, "executable": None, "exit_code": code.value}
        buffer = ctypes.create_unicode_buffer(32768)
        size = wintypes.DWORD(len(buffer))
        if not kernel.QueryFullProcessImageNameW(handle, 0, buffer, ctypes.byref(size)):
            return {"alive": True, "executable": None, "error": ctypes.get_last_error()}
        return {"alive": True, "executable": buffer.value}
    finally:
        kernel.CloseHandle(handle)


class IsolatedMysql:
    def __init__(self, bin_dir: Path | None, startup_timeout: int,
                 temp_parent: Path | None = None):
        self.mysqld = executable("mysqld", bin_dir)
        self.mysql = executable("mysql", bin_dir)
        self.mysqladmin = executable("mysqladmin", bin_dir)
        self.base_dir = self.mysqld.parent.parent
        self.port = reserve_free_port()
        self.temp_root = (temp_parent or Path(tempfile.gettempdir())).resolve()
        self.temp_root.mkdir(parents=True, exist_ok=True)
        self.root = Path(tempfile.mkdtemp(prefix=PREFIX, dir=self.temp_root)).resolve()
        self.token = secrets.token_hex(24)
        self.owner_file = self.root / ".owner-token"
        self.owner_file.write_text(self.token, encoding="ascii")
        self.data_dir = self.root / "data"
        self.mysql_tmp_dir = self.root / "tmp"
        self.mysql_tmp_dir.mkdir()
        self.upload_dir = self.root / "uploads"
        self.upload_dir.mkdir()
        self.pid_file = self.root / "mysqld.pid"
        self.error_log = self.root / "mysqld-error.log"
        self.console_log = self.root / "mysqld-console.log"
        self.startup_timeout = startup_timeout
        self.process: subprocess.Popen | None = None
        self.console_handle = None
        self.identity_verified = False
        self.server_pid: int | None = None
        self.version = ""
        self.env = os.environ.copy()
        for key in list(self.env):
            if key.upper().startswith("MYSQL_") or key in (
                "LIBMYSQL_PLUGINS", "LIBMYSQL_PLUGIN_DIR", "NOTIFY_SOCKET",
            ):
                self.env.pop(key, None)
        # In MySQL 8, --no-defaults alone still permits .mylogin.cnf. This
        # documented variable redirects it to our own nonexistent path:
        # https://dev.mysql.com/doc/refman/8.0/en/environment-variables.html
        self.env["MYSQL_TEST_LOGIN_FILE"] = (self.root / "no-login.cnf").as_posix()
        self.env["MYSQL_HISTFILE"] = (self.root / "mysql.history").as_posix()
        self.env["MYSQL_HOME"] = self.root.as_posix()
        # Child processes only: avoid MySQL falling back to a non-ASCII system
        # temp path. The parent shell/process environment is never changed.
        for key in ("TEMP", "TMP", "TMPDIR"):
            self.env[key] = self.mysql_tmp_dir.as_posix()

    def check_version(self) -> None:
        result = run_command([self.mysqld, "--no-defaults", "--version"],
                             env=self.env, timeout=10)
        self.version = (result.stdout or result.stderr).strip()
        match = re.search(r"\bVer\s+(\d+)\.(\d+)\.(\d+)", self.version)
        if not match or "mariadb" in self.version.lower():
            raise VerificationError("An Oracle MySQL 8 server binary is required.")
        version = tuple(map(int, match.groups()))
        if version[0] != 8 or version < MIN_VERSION:
            raise VerificationError("MySQL 8.0.16 or newer MySQL 8 is required.")

    def diagnostics(self) -> str:
        chunks = []
        for path in (self.error_log, self.console_log, self.root / "initialize.log"):
            if path.is_file():
                chunks.append(path.read_text(encoding="utf-8", errors="replace")[-5000:])
        return "\n".join(chunks)

    def directory_owned(self) -> bool:
        try:
            resolved = self.root.resolve()
            return (resolved.parent == self.temp_root and resolved.name.startswith(PREFIX)
                    and not self.root.is_symlink()
                    and self.owner_file.read_text(encoding="ascii") == self.token)
        except OSError:
            return False

    def pid_text(self) -> str:
        try:
            return self.pid_file.read_text(encoding="utf-8-sig")
        except OSError:
            return ""

    def ready_log(self) -> str:
        return "\n".join(path.read_text(encoding="utf-8", errors="replace")
                         for path in (self.error_log, self.console_log) if path.is_file())

    def is_descendant(self, pid: int, processes: dict[int, tuple[int, str]]) -> bool:
        if self.process is None:
            return False
        seen = set()
        while pid and pid not in seen:
            if pid == self.process.pid:
                return True
            seen.add(pid)
            pid = processes.get(pid, (0, ""))[0]
        return False

    def owns_live_process(self) -> bool:
        if self.process is None or not self.directory_owned():
            return False
        try:
            pid = int(self.pid_text().strip())
            info = process_info(pid)
            log = self.ready_log()
            if (info["alive"] is not True or not info.get("executable")
                    or Path(info["executable"]).resolve() != self.mysqld
                    or not self.is_descendant(pid, process_snapshot())
                    or not re.search(r"\bstarting as process\s+" + str(pid) + r"\b", log, re.I)
                    or not re.search(r"ready for connections.*\bport:\s*" + str(self.port) + r"\b", log, re.I)):
                return False
            self.server_pid = pid
            return True
        except (OSError, ValueError, VerificationError):
            return False

    def diagnostic_state(self) -> dict:
        result = {"popen_pid": self.process.pid if self.process else None,
                  "popen_poll": self.process.poll() if self.process else None,
                  "pid_file": self.pid_file.as_posix(), "pid_file_raw": self.pid_text(),
                  "server_pid": self.server_pid, "port": self.port,
                  "temporary_directory": self.root.as_posix()}
        if self.server_pid:
            result["server_process"] = process_info(self.server_pid)
        return result

    def port_is_free(self) -> bool:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
            if hasattr(socket, "SO_EXCLUSIVEADDRUSE"):
                listener.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
            try:
                listener.bind(("127.0.0.1", self.port))
                return True
            except OSError:
                return False

    def related_live_servers(self) -> list[int]:
        if self.process is None:
            return []
        processes = process_snapshot()
        candidates = {pid for pid, (_, name) in processes.items()
                      if pid != self.process.pid and name.lower() in ("mysqld", "mysqld.exe")
                      and self.is_descendant(pid, processes)}
        if self.server_pid:
            candidates.add(self.server_pid)
        text = self.pid_text().strip()
        if text.isdigit():
            candidates.add(int(text))
        return [pid for pid in candidates if process_info(pid)["alive"] is not False]

    def start(self) -> None:
        self.check_version()
        self.data_dir.mkdir()
        result = run_command([
            self.mysqld, "--no-defaults", "--initialize-insecure",
            f"--basedir={self.base_dir.as_posix()}", f"--datadir={self.data_dir.as_posix()}",
            f"--tmpdir={self.mysql_tmp_dir.as_posix()}",
            f"--log-error={(self.root / 'initialize.log').as_posix()}", "--skip-log-bin",
        ], env=self.env, cwd=self.root, timeout=60, check=False)
        if result.returncode:
            raise VerificationError("Temporary MySQL initialization failed: " +
                                    (result.stderr or result.stdout or self.diagnostics())[-5000:])
        args = [
            self.mysqld, "--no-defaults", f"--basedir={self.base_dir.as_posix()}",
            f"--datadir={self.data_dir.as_posix()}",
            f"--tmpdir={self.mysql_tmp_dir.as_posix()}",
            f"--secure-file-priv={self.upload_dir.as_posix()}", "--bind-address=127.0.0.1",
            f"--port={self.port}", "--mysqlx=0", "--skip-log-bin",
            f"--pid-file={self.pid_file.as_posix()}", f"--log-error={self.error_log.as_posix()}",
            "--character-set-server=utf8mb4", "--collation-server=utf8mb4_bin",
            "--default-time-zone=+00:00", "--innodb-buffer-pool-size=64M",
        ]
        if os.name == "nt":
            # On Windows --console overrides --log-error. Use our explicit log
            # instead, with stdout/stderr still captured separately by Popen.
            args.extend(["--named-pipe=OFF", "--shared-memory=OFF"])
        else:
            args.append(f"--socket={(self.root / 'mysql.sock').as_posix()}")
        self.console_handle = self.console_log.open("wb")
        self.process = subprocess.Popen(
            list(map(str, args)), stdout=self.console_handle,
            stderr=subprocess.STDOUT, env=self.env, cwd=self.root,
            shell=False, creationflags=CREATE_FLAGS,
        )
        deadline = time.monotonic() + self.startup_timeout
        while time.monotonic() < deadline:
            # Do not contact the selected port until our owned PID and its log
            # prove a live server is this process or its MySQL monitor child.
            # A port race must never send SQL to an unrelated existing service.
            if self.owns_live_process():
                row = self.sql("SELECT @@port, @@datadir;").strip().split("\t")
                if len(row) != 2 or int(row[0]) != self.port or Path(row[1]).resolve() != self.data_dir.resolve():
                    raise VerificationError("Temporary server identity does not match owned port/datadir.")
                self.identity_verified = True
                return
            time.sleep(0.2)
        raise VerificationError("Temporary MySQL startup timed out: "
                                + json.dumps(self.diagnostic_state(), ensure_ascii=False)
                                + "\n" + self.diagnostics())

    def client_args(self, database: str | None = None) -> list[str]:
        args = [str(self.mysql), "--no-defaults", "--protocol=TCP",
                "--host=127.0.0.1", f"--port={self.port}", "--user=root",
                "--connect-timeout=2", "--default-character-set=utf8mb4",
                "--batch", "--raw", "--skip-column-names"]
        if database:
            if database not in TEST_DATABASES:
                raise VerificationError("Refusing an unexpected database name.")
            args.append(f"--database={database}")
        return args

    def sql(self, statement: str, database: str | None = None,
            *, expected_error: int | None = None) -> str:
        if not self.owns_live_process():
            raise VerificationError("Owned temporary MySQL process is not running.")
        result = run_command(self.client_args(database), env=self.env, cwd=self.root,
                             input_text=statement, timeout=60, check=False)
        if expected_error is not None:
            if result.returncode == 0:
                raise VerificationError("SQL unexpectedly succeeded; an enforced constraint was expected.")
            if not re.search(r"\bERROR\s+" + str(expected_error) + r"\b", result.stderr):
                raise VerificationError(f"Expected MySQL error {expected_error}, got: {result.stderr.strip()[-3000:]}")
            return result.stderr
        if result.returncode:
            raise VerificationError("SQL failed in isolated instance: " + result.stderr.strip()[-5000:])
        return result.stdout

    def table_names(self, database: str) -> set[str]:
        return set(self.sql("SHOW TABLES;", database).splitlines())

    def stop_and_cleanup(self) -> None:
        try:
            if not self.directory_owned():
                raise VerificationError("Temporary-directory ownership check failed; cleanup refused.")
            if self.process is not None:
                owned = self.owns_live_process()
                if owned and not self.identity_verified:
                    row = self.sql("SELECT @@port, @@datadir;").strip().split("\t")
                    self.identity_verified = (len(row) == 2 and int(row[0]) == self.port
                                              and Path(row[1]).resolve() == self.data_dir.resolve())
                if owned and self.identity_verified:
                    run_command([
                        self.mysqladmin, "--no-defaults", "--protocol=TCP",
                        "--host=127.0.0.1", f"--port={self.port}", "--user=root",
                        "--connect-timeout=2", "shutdown",
                    ], env=self.env, cwd=self.root, timeout=15, check=False)
                deadline = time.monotonic() + 15
                while time.monotonic() < deadline:
                    if not self.related_live_servers() and self.port_is_free():
                        break
                    time.sleep(0.2)
                if self.related_live_servers() or not self.port_is_free():
                    raise VerificationError("Server may still be active; temporary directory retained. "
                                            + json.dumps(self.diagnostic_state(), ensure_ascii=False))
                if self.process.poll() is None:
                    # Only stop our launcher after its actual server children
                    # are definitely gone; never kill a child PID by name.
                    self.process.terminate()
                    self.process.wait(timeout=5)
                if self.related_live_servers() or not self.port_is_free():
                    raise VerificationError("A server reappeared; temporary directory retained. "
                                            + json.dumps(self.diagnostic_state(), ensure_ascii=False))
            if self.console_handle is not None:
                self.console_handle.close()
                self.console_handle = None
            if not self.directory_owned():
                raise VerificationError("Directory ownership changed; cleanup refused.")
            shutil.rmtree(self.root.resolve())
        finally:
            if self.console_handle is not None:
                self.console_handle.close()
                self.console_handle = None


def legacy_seed_sql() -> str:
    statements = [
        "INSERT INTO t_user (id,username,password,real_name,status,role_code,create_time,update_time) "
        "VALUES (920000001,'schema_fixture_user','fixture-not-for-login','schema fixture',1,'VIEWER',"
        "'2026-10-25 01:00:00','2026-10-25 01:00:00');",
        "INSERT INTO cleaning_system (system_id,device_id,ring_id) "
        "VALUES ('legacy-system','legacy-plc','legacy-ring');",
    ]
    for winch in range(1, 5):
        statements.append("INSERT INTO cleaning_winch (winch_id,system_id,slot_no) "
                          f"VALUES ('legacy-winch-{winch}','legacy-system',{winch});")
        for rope in range(1, 3):
            statements.append("INSERT INTO cleaning_rope_sensor "
                              "(rope_id,winch_id,slot_no,tension_sensor_id) "
                              f"VALUES ('legacy-rope-{winch}-{rope}','legacy-winch-{winch}',"
                              f"{rope},'legacy-sensor-{winch}-{rope}');")
    statements.extend([
        "INSERT INTO cleaning_telemetry_batch "
        "(device_id,batch_id,first_sequence,last_sequence) "
        "VALUES ('legacy-plc','legacy-batch',7,7);",
        "INSERT INTO cleaning_telemetry_segment "
        "(device_id,batch_id,telemetry_sequence,session_id,round_no,direction,started_at,finished_at,"
        "start_height_m,end_height_m,trigger_reason,policy_version) VALUES "
        "('legacy-plc','legacy-batch',7,'legacy-session',3,'UP','2026-10-25 01:02:03',"
        "'2026-10-25 01:02:04',0,0.5,'SCHEMA_VERIFY','legacy-v1');",
        "INSERT INTO cleaning_segment_measurement "
        "(device_id,batch_id,telemetry_sequence,metric_type,channel_no,target_id,minimum_value,"
        "maximum_value,average_value,latest_value,sample_count) VALUES "
        "('legacy-plc','legacy-batch',7,'ROPE_TENSION_KG',1,'legacy-rope-1-1',"
        "123.4567,123.4567,123.4567,123.4567,1);",
    ])
    return "\n".join(statements)


def fingerprints(server: IsolatedMysql, database: str,
                 columns: dict[str, list[str]]) -> dict[str, str]:
    return {
        table: server.sql("SELECT " + ",".join(f"`{column}`" for column in names)
                          + f" FROM `{table}` ORDER BY "
                          + ",".join(f"`{column}`" for column in names) + ";", database)
        for table, names in columns.items()
    }


def check_schema(server: IsolatedMysql, database: str, expected: set[str]) -> None:
    actual = server.table_names(database)
    if actual != expected or len(actual) != V2_TABLE_COUNT:
        raise VerificationError(f"{database}: expected {V2_TABLE_COUNT} tables; missing={sorted(expected-actual)}, "
                                f"unexpected={sorted(actual-expected)}.")


def database_snapshot(server: IsolatedMysql, database: str) -> dict:
    """Capture table definitions and every fixture value before a rejected DDL."""
    snapshot = {}
    for table in sorted(server.table_names(database)):
        if not re.fullmatch(r"[a-z_][a-z_0-9]*", table):
            raise VerificationError("Unexpected fixture table identifier.")
        primary = server.sql(
            "SELECT COLUMN_NAME FROM information_schema.STATISTICS "
            "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=" + sql_value(table)
            + " AND INDEX_NAME='PRIMARY' ORDER BY SEQ_IN_INDEX;", database).splitlines()
        if not primary:
            raise VerificationError(f"Cannot snapshot fixture {table} without a primary key.")
        columns = [row.split("\t")[0] for row in server.sql(
            f"SHOW COLUMNS FROM `{table}`;", database).splitlines()]
        # Hex makes binary request/capture IDs and embedded control characters
        # unambiguous in the UTF-8 client's output; NULL has a distinct marker.
        cells = ",".join(
            f"IF(`{column}` IS NULL,'N',CONCAT('V',HEX(CAST(`{column}` AS BINARY))))"
            for column in columns)
        snapshot[table] = {
            "definition": server.sql(f"SHOW CREATE TABLE `{table}`;", database),
            "rows": server.sql(f"SELECT {cells} FROM `{table}` ORDER BY "
                               + ",".join(f"`{column}`" for column in primary) + ";", database),
        }
    return snapshot


def require_snapshot_unchanged(server: IsolatedMysql, database: str,
                               before: dict, label: str) -> None:
    after = database_snapshot(server, database)
    if after != before:
        changed = sorted(table for table in set(before) | set(after)
                         if before.get(table) != after.get(table))
        raise VerificationError(f"{label} changed table definitions or fixture data: {changed}.")


def check_public_codes_unfilled(server: IsolatedMysql) -> None:
    for table, code, expected_count in (
        ("cleaning_system", "system_code", 1),
        ("cleaning_winch", "winch_code", 4),
        ("cleaning_rope_sensor", "sensor_code", 8),
    ):
        names = {row.split("\t")[0] for row in server.sql(f"SHOW COLUMNS FROM `{table}`;", UPGRADE_DB).splitlines()}
        if code not in names:
            raise VerificationError(f"Missing public-code column {table}.{code}.")
        count = int(server.sql(f"SELECT COUNT(*) FROM `{table}` WHERE `{code}` IS NULL;", UPGRADE_DB).strip())
        if count != expected_count:
            raise VerificationError(f"Upgrade fabricated or lost legacy public codes in {table}.")


def sql_value(value) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bytes):
        return "X'" + value.hex() + "'"
    if isinstance(value, (dict, list)):
        value = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def insert_row(table: str, values: dict) -> str:
    if not re.fullmatch(r"[a-z_][a-z_0-9]*", table) or any(
        not re.fullmatch(r"[a-z_][a-z_0-9]*", key) for key in values
    ):
        raise VerificationError("Invalid internal fixture identifier.")
    return (f"INSERT INTO `{table}` (" + ",".join(f"`{key}`" for key in values)
            + ") VALUES (" + ",".join(map(sql_value, values.values())) + ");")


def require_scalar(server: IsolatedMysql, statement: str, expected: str,
                   label: str, database: str = FRESH_DB) -> None:
    if server.sql(statement, database).strip() != expected:
        raise VerificationError(label)


def create_test_database(server: IsolatedMysql, database: str) -> None:
    if database not in TEST_DATABASES:
        raise VerificationError("Refusing to create an unexpected fixture database.")
    server.sql(f"CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;")


def cycle_sentinel_sql() -> str:
    return insert_row("cleaning_cycle_record", {
        "id": 930000001, "cycle_code": "UPGRADE-GUARD-CYCLE",
        "system_id": "legacy-system", "device_id": "legacy-plc",
        "first_observed_at": "2026-10-26 00:00:00.000",
        "last_observed_at": "2026-10-26 00:00:00.000",
        "created_at": "2026-10-26 00:00:00.000", "updated_at": "2026-10-26 00:00:00.000",
    })


def verify_rejected_upgrades(server: IsolatedMysql, baseline: str, migration_sql: str,
                             table_source: str, schema_sql: str, legacy_cleanup_sql: str,
                             expected_tables: set[str], report: dict) -> None:
    """Each bad input has its own database, within this owned temporary server."""
    create_test_database(server, REJECT_SLOT_DB)
    server.sql(baseline + "\n" + legacy_seed_sql(), REJECT_SLOT_DB)
    server.sql("INSERT INTO cleaning_winch (winch_id,system_id,slot_no) "
               "VALUES ('legacy-winch-5','legacy-system',5);", REJECT_SLOT_DB)
    before = database_snapshot(server, REJECT_SLOT_DB)
    error = server.sql(migration_sql, REJECT_SLOT_DB, expected_error=1644)
    if "Out-of-range slot in cleaning_winch" not in error:
        raise VerificationError("Slot-5 upgrade failed for an unexpected preflight reason: " + error)
    require_snapshot_unchanged(server, REJECT_SLOT_DB, before, "Slot-5 upgrade rejection")
    report["checks"].append("slot 5 rejects upgrade before any table definition or legacy value changes")

    match = re.search(
        r"\bCREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+cleaning_cycle_record\s*\(.*?"
        r"\)\s*ENGINE\s*=[^;]+;", table_source, re.I | re.S,
    )
    if match is None:
        raise VerificationError("Cannot extract the canonical cycle DDL for malformed-target fixtures.")
    canonical = match.group(0)
    if canonical.count("PRIMARY KEY (id)") != 1 or canonical.count("AUTO_INCREMENT") != 1:
        raise VerificationError("Cycle DDL fixture assumptions changed; review malformed-target tests.")
    malformed = (
        (REJECT_PRIMARY_DB,
         canonical.replace("PRIMARY KEY (id)", "PRIMARY KEY (cycle_code), KEY fixture_auto_id (id)"),
         "Incompatible primary key: cleaning_cycle_record",
         "same-name V2 target with a wrong primary key rejects before any table or data changes"),
        (REJECT_AUTO_DB, canonical.replace(" AUTO_INCREMENT", ""),
         "Missing AUTO_INCREMENT: cleaning_cycle_record.id",
         "same-name V2 target without AUTO_INCREMENT rejects before any table or data changes"),
    )
    for database, target_ddl, expected_message, pass_message in malformed:
        create_test_database(server, database)
        server.sql(baseline + "\n" + legacy_seed_sql(), database)
        collation = server.sql(
            "SELECT COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
            "AND TABLE_NAME='cleaning_system' AND COLUMN_NAME='system_id';", database).strip()
        if not re.fullmatch(r"utf8mb4_[a-zA-Z0-9_]+", collation):
            raise VerificationError("Unexpected parent collation in rejection fixture.")
        # Match the existing identity collation so an unrelated FK mismatch
        # cannot masquerade as a successful primary-key/auto-increment guard.
        server.sql(target_ddl.replace("utf8mb4_general_ci", collation)
                   + "\n" + cycle_sentinel_sql(), database)
        before = database_snapshot(server, database)
        error = server.sql(migration_sql, database, expected_error=1644)
        if expected_message not in error:
            raise VerificationError("Malformed-target upgrade failed for an unexpected reason: " + error)
        require_snapshot_unchanged(server, database, before, "Malformed-target upgrade rejection")
        report["checks"].append(pass_message)

    create_test_database(server, LEGACY_GUARD_DB)
    server.sql(schema_sql + "\n" + legacy_seed_sql() + "\n" + cycle_sentinel_sql(), LEGACY_GUARD_DB)
    server.sql(insert_row("cleaning_exception_event", {
        "event_code": "LEGACY-CLEANUP-GUARD-EVENT", "system_id": "legacy-system",
        "device_id": "legacy-plc", "cycle_id": 930000001, "source_kind": "PLC",
        "event_type": "MOTOR_FAULT", "fault_code": 10,
        "first_observed_at": "2026-10-26 00:00:00.000",
        "created_at": "2026-10-26 00:00:00.000", "updated_at": "2026-10-26 00:00:00.000",
    }), LEGACY_GUARD_DB)
    check_schema(server, LEGACY_GUARD_DB, expected_tables)
    before = database_snapshot(server, LEGACY_GUARD_DB)
    # This historical script contains DROP statements. Execute it only here,
    # against our dedicated fixture database, to prove its V2 guard fires first.
    error = server.sql(legacy_cleanup_sql, LEGACY_GUARD_DB, expected_error=1644)
    if "V2 schema detected; legacy cleanup is disabled" not in error:
        raise VerificationError("Legacy cleanup failed without demonstrating its V2 guard: " + error)
    require_snapshot_unchanged(server, LEGACY_GUARD_DB, before, "Legacy cleanup V2 rejection")
    check_schema(server, LEGACY_GUARD_DB, expected_tables)
    report["checks"].append(
        f"legacy cleanup rejects a V2 database; all {V2_TABLE_COUNT} table definitions and data remain unchanged")


def verify_v2_features(server: IsolatedMysql, report: dict) -> None:
    """Exercise real storage and enforced indexes/checks, not application logic."""
    server.sql(legacy_seed_sql(), FRESH_DB)
    now = "2026-10-26 00:00:00.000"
    cycle = {
        "cycle_code": "V2-CYCLE-1", "system_id": "legacy-system", "device_id": "legacy-plc",
        "first_observed_at": now, "last_observed_at": now,
        "completion_source_key": b"fixture-completion-1", "m204_value": 1,
        "completion_observed_at": now, "created_at": now, "updated_at": now,
    }
    cycle_id = int(server.sql(insert_row("cleaning_cycle_record", cycle)
                              + "SELECT LAST_INSERT_ID();", FRESH_DB).strip())
    server.sql(insert_row("cleaning_cycle_record", dict(cycle, cycle_code="V2-CYCLE-DUPLICATE")),
               FRESH_DB, expected_error=1062)
    server.sql(insert_row("cleaning_cycle_record", dict(
        cycle, cycle_code="V2-CYCLE-BAD-M204", completion_source_key=b"fixture-bad", m204_value=2,
    )), FRESH_DB, expected_error=3819)
    parameters = {"startTime": "08:30", "intervalSeconds": 1800, "groupSpeedMetersPerMinute": 10}
    run = {
        "record_code": "V2-RUN-1", "system_id": "legacy-system", "device_id": "legacy-plc",
        "cycle_id": cycle_id, "first_observed_at": now, "last_observed_at": now,
        "height_step_m": 0.5, "selection_rule_version": "height-0.5-v1",
        "statistics_rule_version": "full-stream-v1", "all_sample_count": 200,
        "saved_history_batch_count": 2, "parameter_snapshot": parameters,
        "created_at": now, "updated_at": now,
    }
    run_id = int(server.sql(insert_row("cleaning_run_record", run)
                            + "SELECT LAST_INSERT_ID();", FRESH_DB).strip())
    require_scalar(server,
        "SELECT (JSON_TYPE(parameter_snapshot)='OBJECT' AND "
        "JSON_UNQUOTE(JSON_EXTRACT(parameter_snapshot,'$.startTime'))='08:30' AND "
        "JSON_EXTRACT(parameter_snapshot,'$.intervalSeconds')=1800 AND "
        "all_sample_count=200 AND saved_history_batch_count=2) "
        "FROM cleaning_run_record WHERE record_code='V2-RUN-1';", "1",
        "Run parameters or full-stream versus retained-point counts were not preserved.")
    server.sql(insert_row("cleaning_run_record", dict(
        run, record_code="V2-RUN-BAD-STEP", height_step_m=0.25,
    )), FRESH_DB, expected_error=3819)
    report["checks"].append("run/cycle relation, M204 completion uniqueness, JSON parameters and fixed 0.5m rule")

    sample = {
        "system_id": "legacy-system", "device_id": "legacy-plc",
        "capture_id": bytes.fromhex("0123456789abcdef0123456789abcdef"), "sample_seq": 1,
        "sampled_at": now, "received_at": now, "selection_mask": 1,
        "height_step_m": 0.5, "selection_rule_version": "height-0.5-v1",
        "system_state_code": 1, "m3_fault_value": 0, "fault_code": 0,
        "up_complete_count_raw": 7, "auto_elapsed_raw": 12, "auto_elapsed_unit": "s",
        "system_force_kg": 204,
    }
    for slot in range(1, 5):
        prefix = f"w{slot}_"
        sample.update({prefix + "run_id": run_id, prefix + "cycle_id": cycle_id,
                       prefix + "winch_id": f"legacy-winch-{slot}",
                       prefix + "rope1_id": f"legacy-rope-{slot}-1",
                       prefix + "sensor1_id": f"legacy-sensor-{slot}-1",
                       prefix + "rope2_id": f"legacy-rope-{slot}-2",
                       prefix + "sensor2_id": f"legacy-sensor-{slot}-2",
                       prefix + "height_selected": 1 if slot == 1 else 0,
                       prefix + "selection_reason_mask": 1 if slot == 1 else 0,
                       prefix + "target_height_m": 0.5 if slot == 1 else None,
                       prefix + "height_m": 0.52, prefix + "state_code": 1,
                       prefix + "up_value": 1, prefix + "down_value": 0,
                       prefix + "direction": "UP", prefix + "motion_role": "PRODUCTION_UP",
                       prefix + "segment_id": bytes([slot]) * 16,
                       prefix + "encoder_raw": slot * 8192,
                       prefix + "axis_elapsed_s": 3,
                       prefix + "rope1_raw": 100 + slot, prefix + "rope2_raw": 200 + slot,
                       prefix + "rope1_kg": slot * 10, prefix + "rope2_kg": slot * 10 + 1,
                       prefix + "pair_force_kg": slot * 20 + 1,
                       prefix + "motor_raw": slot, prefix + "motor_force_kg": slot * 36.3789,
                       prefix + "speed_mpm": 6.5})
    server.sql(insert_row("cleaning_up_sample", sample), FRESH_DB)
    server.sql(insert_row("cleaning_up_sample", sample), FRESH_DB, expected_error=1062)
    server.sql(insert_row("cleaning_up_sample", sample)[:-1] + " ON DUPLICATE KEY UPDATE id=id;", FRESH_DB)
    require_scalar(server, "SELECT COUNT(*) FROM cleaning_up_sample;", "1",
                   "Replayed sample identity was not deduplicated.")
    server.sql(insert_row("cleaning_up_sample", dict(sample, sample_seq=2)), FRESH_DB)
    require_scalar(server, "SELECT COUNT(*) FROM cleaning_up_sample WHERE w1_height_m=0.52;", "2",
                   "Repeated actual heights in different samples must coexist.")
    require_scalar(server,
        "SELECT (height_step_m=0.5 AND w1_target_height_m=0.5 AND w1_height_m=0.52 "
        "AND w1_pair_force_kg=w1_rope1_kg+w1_rope2_kg AND "
        "system_force_kg=w1_pair_force_kg+w2_pair_force_kg+w3_pair_force_kg+w4_pair_force_kg) "
        "FROM cleaning_up_sample WHERE sample_seq=1;", "1",
        "Actual height, same-capture sensor force totals or conversion columns were not preserved.")
    for mask in (0, 16):
        server.sql(insert_row("cleaning_up_sample", dict(sample, sample_seq=100 + mask,
                                                        selection_mask=mask)), FRESH_DB, expected_error=3819)
    for slot in range(1, 5):
        selected = dict(sample, sample_seq=10 + slot, selection_mask=1 << (slot - 1))
        for other in range(1, 5):
            selected[f"w{other}_height_selected"] = int(slot == other)
            selected[f"w{other}_selection_reason_mask"] = int(slot == other)
        server.sql(insert_row("cleaning_up_sample", selected), FRESH_DB)
        mismatched = dict(selected, sample_seq=30 + slot)
        mismatched[f"w{slot}_selection_reason_mask"] = 0
        server.sql(insert_row("cleaning_up_sample", mismatched), FRESH_DB, expected_error=3819)
    boundary = dict(sample, sample_seq=50, w1_height_selected=0, w1_selection_reason_mask=8)
    server.sql(insert_row("cleaning_up_sample", boundary), FRESH_DB)
    server.sql(insert_row("cleaning_up_sample", dict(sample, sample_seq=51, height_step_m=1)),
               FRESH_DB, expected_error=3819)
    report["checks"].append("sample deduplication, repeated heights, actual 0.52m values and four-device selection masks")

    periods = (
        ("DAY", "2026-10-26", "2026-10-27"),
        ("MONTH", "2026-10-01", "2026-11-01"),
        ("QUARTER", "2026-10-01", "2027-01-01"),
        ("HALF_YEAR", "2026-07-01", "2027-01-01"),
        ("YEAR", "2026-01-01", "2027-01-01"),
    )
    for scope, scope_id in (("SYSTEM", "legacy-system"), ("WINCH", "legacy-winch-1")):
        for kind, start, end in periods:
            period = {
                "system_id": "legacy-system", "scope_kind": scope, "scope_id": scope_id,
                "period_type": kind, "period_start": start, "period_end": end,
                "run_started_count": 1, "run_completed_count": 1, "cycle_completed_count": 0,
                "rule_version": "full-stream-v1", "computed_version": 1,
                "needs_rebuild": 0, "updated_at": now,
            }
            statement = insert_row("cleaning_period_summary", period)
            server.sql(statement, FRESH_DB)
            server.sql(statement, FRESH_DB, expected_error=1062)
    for changes, error in (({"scope_id": None}, 1048), ({"scope_id": ""}, 3819),
                           ({"scope_kind": "DEVICE"}, 3819), ({"period_type": "WEEK"}, 3819),
                           ({"period_end": "2026-01-01"}, 3819)):
        server.sql(insert_row("cleaning_period_summary", dict(period, **changes)),
                   FRESH_DB, expected_error=error)
    require_scalar(server, "SELECT COUNT(*) FROM cleaning_period_summary;", "10",
                   "Expected one row per natural period and system/winch scope.")
    report["checks"].append("DAY/MONTH/QUARTER/HALF_YEAR/YEAR scope uniqueness and enforced non-null/range rules")

    day = {
        "run_id": run_id, "winch_id": "legacy-winch-1", "system_id": "legacy-system",
        "stat_date": "2026-10-26", "motion_role": "PRODUCTION_UP",
        "all_sample_count": 200, "saved_height_point_count": 4,
        "rope1_sum_kg": 20500, "rope1_sample_count": 200,
        "rule_version": "full-stream-v1", "updated_at": now,
    }
    server.sql(insert_row("cleaning_run_winch_day", day), FRESH_DB)
    server.sql(insert_row("cleaning_run_winch_day", day), FRESH_DB, expected_error=1062)
    bucket = {
        "system_id": "legacy-system", "winch_id": "legacy-winch-1", "source_role": "ROPE_TOTAL",
        "period_type": "DAY", "period_start": "2026-10-26", "period_end": "2026-10-27",
        "rule_version": "height-0.5-v1", "bucket_index": 1,
        "height_from_m": 0.5, "height_to_m": 1, "sum_kg": 42, "sample_count": 2, "built_at": now,
    }
    server.sql(insert_row("cleaning_height_bucket", bucket), FRESH_DB)
    server.sql(insert_row("cleaning_height_bucket", bucket), FRESH_DB, expected_error=1062)
    require_scalar(server,
        "SELECT (basis='SAVED_HEIGHT_POINTS' AND sample_count=2) FROM cleaning_height_bucket;", "1",
        "Height buckets must retain the saved-point basis.")
    report["checks"].append("per-run day summary and saved-height bucket keys retain distinct statistical bases")

    event = {
        "event_code": "V2-EVENT-1", "system_id": "legacy-system", "device_id": "legacy-plc",
        "record_id": run_id, "cycle_id": cycle_id, "winch_id": "legacy-winch-1",
        "source_kind": "PLC", "source_key": b"fixture-event-1", "event_type": "MOTOR_FAULT",
        "fault_code": 10, "fault_detail_snapshot": {"D300": 10}, "first_observed_at": now,
        "created_at": now, "updated_at": now,
    }
    server.sql(insert_row("cleaning_exception_event", event), FRESH_DB)
    server.sql("UPDATE cleaning_exception_event SET event_state='ENDED', ended_at='2026-10-26 00:01:00',"
               "revision=revision+1 WHERE event_code='V2-EVENT-1';", FRESH_DB)
    require_scalar(server,
        "SELECT (COUNT(*)=1 AND MAX(revision)=2) FROM cleaning_exception_event WHERE event_code='V2-EVENT-1';",
        "1", "A continuing event must remain one persisted event.")
    export = {
        "task_code": "V2-EXPORT-1", "creator_user_id": 920000001, "system_id": "legacy-system",
        "export_type": "RUN_LIST", "filter_snapshot": {"recordCodes": ["V2-RUN-1"]},
        "unit": "kg", "column_version": "v2-test", "created_at": now, "updated_at": now,
    }
    server.sql(insert_row("cleaning_export_task", export), FRESH_DB)
    report["checks"].append("event revision and export task JSON fixtures persist without copying raw data into logs")

    requested = {"speedMPerMinute": 6.5}
    request_hash = hashlib.sha256(json.dumps(requested, sort_keys=True).encode("utf-8")).digest()
    audit = {
        "operation_code": "V2-OP-1", "system_id": "legacy-system", "device_id": "legacy-plc",
        "request_id": b"request-one", "request_hash": request_hash,
        "operator_user_id": 920000001, "operator_name": "schema_fixture_user",
        "operation_type": "UPDATE_PARAMETERS", "phase": "ACCEPTED", "state": "PENDING",
        "requested_snapshot": requested, "requested_at": now, "updated_at": now,
    }
    server.sql(insert_row("cleaning_operation_audit", audit), FRESH_DB)
    server.sql(insert_row("cleaning_operation_audit", dict(audit, operation_code="V2-OP-DUPLICATE")),
               FRESH_DB, expected_error=1062)
    server.sql(insert_row("cleaning_operation_audit", dict(audit, operation_code="V2-OP-CASE",
                                                         request_id=b"REQUEST-ONE")), FRESH_DB)
    require_scalar(server,
        "SELECT (COUNT(*)=2 AND SUM(write_acknowledged)=0 AND SUM(execution_confirmed)=0) "
        "FROM cleaning_operation_audit;", "1",
        "Audit request identity or write-versus-execution flags were not preserved.")
    report["checks"].append("remote-operation request uniqueness uses original bytes and does not claim PLC execution")
    verify_cross_day_system_flow(server, run, sample, report)


def verify_cross_day_system_flow(server: IsolatedMysql, run_template: dict,
                                 sample_template: dict, report: dict) -> None:
    """Verify storage of independent full-flow system statistics across midnight.

    The seven known inputs include peaks between the two retained height points.
    This verifies DDL storage/aggregation semantics, not an unimplemented collector.
    """
    capture_id = bytes.fromhex("99999999999999999999999999999999")
    pair_values = (
        (25, 25, 25, 25), (50, 50, 50, 50), (75, 75, 75, 75),
        (100, 100, 100, 100), (125, 125, 125, 125),
        (300, 100, 100, 100), (100, 250, 200, 150),
    )
    utc_times = (
        "2026-10-25 15:59:56.000", "2026-10-25 15:59:57.000",
        "2026-10-25 15:59:58.000", "2026-10-25 15:59:59.000",
        "2026-10-25 16:00:00.000", "2026-10-25 16:00:01.000", "2026-10-25 16:00:02.000",
    )
    frames = [
        {"seq": index + 1, "at": utc_times[index], "pairs": values, "force": sum(values),
         "heights": [round(0.52 + index * 0.125 + slot * 0.01, 4) for slot in range(4)]}
        for index, values in enumerate(pair_values)
    ]
    minimum, maximum = min(frames, key=lambda item: item["force"]), max(frames, key=lambda item: item["force"])
    run = dict(run_template, record_code="V2-RUN-CROSS-DAY", first_observed_at=utc_times[0],
               last_observed_at=utc_times[-1], started_at=utc_times[0], ended_at=utc_times[-1],
               business_date="2026-10-25", all_sample_count=7, saved_history_batch_count=2,
               system_force_sum_kg=2800, system_force_sample_count=7,
               system_force_min_kg=100, system_force_min_at=minimum["at"],
               system_force_max_kg=700, system_force_max_at=maximum["at"])
    for slot in range(1, 5):
        run[f"system_force_min_w{slot}_height_m"] = minimum["heights"][slot - 1]
        run[f"system_force_max_w{slot}_height_m"] = maximum["heights"][slot - 1]
    run_id = int(server.sql(insert_row("cleaning_run_record", run)
                            + "SELECT LAST_INSERT_ID();", FRESH_DB).strip())

    for frame in (frames[0], frames[4]):
        retained = dict(sample_template, capture_id=capture_id, sample_seq=frame["seq"],
                        sampled_at=frame["at"], received_at=frame["at"], system_force_kg=frame["force"])
        for slot in range(1, 5):
            prefix = f"w{slot}_"
            pair = frame["pairs"][slot - 1]
            retained.update({prefix + "run_id": run_id, prefix + "height_m": frame["heights"][slot - 1],
                             prefix + "target_height_m": (0.5 if frame["seq"] == 1 else 1.0) if slot == 1 else None,
                             prefix + "rope1_kg": pair / 2, prefix + "rope2_kg": pair / 2,
                             prefix + "pair_force_kg": pair})
        server.sql(insert_row("cleaning_up_sample", retained), FRESH_DB)

    for stat_date, group in (("2026-10-25", frames[:4]), ("2026-10-26", frames[4:])):
        low = min(group, key=lambda item: item["force"])
        high = max(group, key=lambda item: item["force"])
        day = {
            "run_id": run_id, "system_id": "legacy-system", "stat_date": stat_date,
            "rule_version": "full-stream-v1", "first_sample_at": group[0]["at"],
            "last_sample_at": group[-1]["at"], "all_sample_count": len(group),
            "system_force_sum_kg": sum(item["force"] for item in group),
            "system_force_sample_count": len(group), "system_force_min_kg": low["force"],
            "system_force_min_at": low["at"], "system_force_min_capture_id": capture_id,
            "system_force_min_sample_seq": low["seq"], "system_force_max_kg": high["force"],
            "system_force_max_at": high["at"], "system_force_max_capture_id": capture_id,
            "system_force_max_sample_seq": high["seq"], "last_capture_id": capture_id,
            "last_sample_seq": group[-1]["seq"],
            "last_committed_batch_key": f"system-flow-{stat_date}".encode("ascii"),
            "revision": 1, "updated_at": utc_times[-1],
        }
        for slot in range(1, 5):
            day[f"system_force_min_w{slot}_height_m"] = low["heights"][slot - 1]
            day[f"system_force_max_w{slot}_height_m"] = high["heights"][slot - 1]
            values = [frame["pairs"][slot - 1] for frame in group]
            server.sql(insert_row("cleaning_run_winch_day", {
                "run_id": run_id, "winch_id": f"legacy-winch-{slot}", "system_id": "legacy-system",
                "stat_date": stat_date, "motion_role": "PRODUCTION_UP", "all_sample_count": len(group),
                "saved_height_point_count": 1 if slot == 1 else 0,
                "pair_force_sum_kg": sum(values), "pair_force_sample_count": len(values),
                "pair_force_min_kg": min(values), "pair_force_max_kg": max(values),
                "rule_version": "full-stream-v1", "updated_at": utc_times[-1],
            }), FRESH_DB)
        statement = insert_row("cleaning_run_system_day", day)
        server.sql(statement, FRESH_DB)
        server.sql(statement, FRESH_DB, expected_error=1062)
        server.sql(statement[:-1] + " ON DUPLICATE KEY UPDATE revision=revision;", FRESH_DB)
        server.sql(insert_row("cleaning_run_system_day", dict(day, rule_version="bad-revision", revision=0)),
                   FRESH_DB, expected_error=3819)

    require_scalar(server,
        "SELECT (COUNT(*)=2 AND SUM(all_sample_count)=7 AND SUM(system_force_sum_kg)=2800 "
        "AND SUM(system_force_sample_count)=7 AND "
        "SUM(system_force_sum_kg)/SUM(system_force_sample_count)=400 "
        "AND AVG(system_force_sum_kg/system_force_sample_count)=425 "
        "AND MIN(system_force_min_kg)=100 AND MAX(system_force_max_kg)=700) "
        f"FROM cleaning_run_system_day WHERE run_id={run_id};", "1",
        "Cross-day full-flow system sums/counts/extrema or unique replay key were not preserved.")
    require_scalar(server,
        "SELECT (COUNT(*)=2 AND AVG(system_force_kg)=300 AND MAX(system_force_kg)=500) "
        f"FROM cleaning_up_sample WHERE w1_run_id={run_id};", "1",
        "Retained height points should differ from the seven-frame full-flow statistics.")
    require_scalar(server,
        "SELECT (SUM(peak)=900) FROM (SELECT MAX(pair_force_max_kg) AS peak "
        f"FROM cleaning_run_winch_day WHERE run_id={run_id} GROUP BY winch_id) AS per_winch;", "1",
        "Fixture must demonstrate that adding individual winch peaks is not the 700kg system peak.")
    require_scalar(server,
        "SELECT (stat_date='2026-10-26' AND first_sample_at='2026-10-25 16:00:00.000' "
        "AND system_force_max_at='2026-10-25 16:00:02.000' AND system_force_max_sample_seq=7 "
        "AND system_force_max_w1_height_m=1.27 AND system_force_max_w2_height_m=1.28 "
        "AND system_force_max_w3_height_m=1.29 AND system_force_max_w4_height_m=1.30 "
        "AND last_sample_seq=7 AND revision=1 AND "
        "last_committed_batch_key=X'73797374656d2d666c6f772d323032362d31302d3236' "
        f"AND system_force_max_capture_id={sql_value(capture_id)}) "
        f"FROM cleaning_run_system_day WHERE run_id={run_id} AND stat_date='2026-10-26';", "1",
        "System extrema source, four simultaneous heights, UTC time or persisted checkpoint was lost.")
    report["checks"].append(
        "cross-midnight system full-flow storage: 7 frames, weighted 400kg mean, 700kg peak; "
        "distinct from 2 retained points and summed individual peaks")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mysql-bin-dir", type=Path, help="Directory containing mysqld/mysql/mysqladmin.")
    parser.add_argument("--workspace", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--temp-parent", type=Path,
                        help="Optional temporary parent (an ASCII path is recommended for MySQL on Windows); only an owned child is removed.")
    parser.add_argument("--baseline-ref", help="Optional Git revision for the seven-table baseline; default uses db/legacy-compatible-base.sql.")
    parser.add_argument("--startup-timeout", type=int, default=45, choices=range(20, 61), metavar="20..60")
    parser.add_argument("--report", type=Path, help="Optional JSON result path.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    report: dict = {"status": "failed", "checks": [], "cleanup": "not-started"}
    server: IsolatedMysql | None = None
    try:
        workspace = args.workspace.resolve()
        schema_path = workspace / "src/main/resources/schema.sql"
        migration_path = workspace / "docs/database/migrations/V20261026__upgrade_cleaning_v2.sql"
        table_source_path = workspace / "src/main/resources/db/cleaning-v2-tables.sql"
        legacy_cleanup_path = workspace / "docs/database/migrate_legacy.sql"
        schema_sql = schema_path.read_text(encoding="utf-8-sig")
        migration_sql = migration_path.read_text(encoding="utf-8-sig")
        table_source = table_source_path.read_text(encoding="utf-8-sig")
        legacy_cleanup_sql = legacy_cleanup_path.read_text(encoding="utf-8-sig")
        assert_additive_sql("schema.sql", schema_sql)
        assert_additive_sql("upgrade migration", migration_sql)
        expected_tables = table_names_from_sql(schema_sql)
        if (len(expected_tables) != V2_TABLE_COUNT
                or expected_tables != set(LEGACY_TABLES) | table_names_from_sql(table_source)
                or "cleaning_run_system_day" not in expected_tables):
            raise VerificationError(
                f"schema.sql is not the ready {V2_TABLE_COUNT}-table v2 schema; refusing to start MySQL.")
        if "V2 schema detected; legacy cleanup is disabled" not in legacy_cleanup_sql:
            raise VerificationError("Historical cleanup lacks the V2 guard required by its rejection test.")
        if args.baseline_ref:
            git = shutil.which("git")
            if not git:
                raise VerificationError("Git is required when --baseline-ref is supplied.")
            baseline = run_command([git, "show", f"{args.baseline_ref}:src/main/resources/schema.sql"],
                                   env=os.environ.copy(), cwd=workspace, timeout=10).stdout
            report["baseline"] = args.baseline_ref
        else:
            baseline_path = workspace / "src/main/resources/db/legacy-compatible-base.sql"
            baseline = baseline_path.read_text(encoding="utf-8-sig")
            report["baseline"] = str(baseline_path)
        if table_names_from_sql(baseline) != set(LEGACY_TABLES):
            raise VerificationError("Baseline ref is not the original seven-table schema; pass --baseline-ref.")
        server = IsolatedMysql(args.mysql_bin_dir, args.startup_timeout, args.temp_parent)
        report.update(temporary_parent=server.temp_root.as_posix(),
                      temporary_directory=server.root.as_posix())
        server.start()
        report.update(mysql_version=server.version, port=server.port)
        report["checks"].append("owned MySQL 8 loopback instance initialized without existing configuration")
        create_test_database(server, FRESH_DB)
        create_test_database(server, UPGRADE_DB)
        for _ in range(2):
            server.sql(schema_sql, FRESH_DB)
            check_schema(server, FRESH_DB, expected_tables)
        report["checks"].append(f"fresh schema executed twice; exactly {V2_TABLE_COUNT} tables")
        server.sql(baseline, UPGRADE_DB)
        server.sql(legacy_seed_sql(), UPGRADE_DB)
        columns = {
            table: [row.split("\t")[0] for row in server.sql(f"SHOW COLUMNS FROM `{table}`;", UPGRADE_DB).splitlines()]
            for table in LEGACY_TABLES
        }
        before = fingerprints(server, UPGRADE_DB, columns)
        for _ in range(2):
            server.sql(migration_sql, UPGRADE_DB)
            check_schema(server, UPGRADE_DB, expected_tables)
            if fingerprints(server, UPGRADE_DB, columns) != before:
                raise VerificationError("Upgrade modified or lost one or more legacy sentinel values.")
            check_public_codes_unfilled(server)
        report["checks"].append("upgrade executed twice; all seven legacy-table sentinels retained")
        report["checks"].append("legacy public codes remain NULL; no fabricated code backfill")
        report["checks"].append("input SQL has no destructive table/data operations")
        verify_v2_features(server, report)
        verify_rejected_upgrades(server, baseline, migration_sql, table_source, schema_sql,
                                 legacy_cleanup_sql, expected_tables, report)
        import build_schema_v2 as builder
        old=builder.V2_BASELINE.read_text(encoding="utf-8-sig")
        runtime_sql=builder.RUNTIME_UPGRADE.read_text(encoding="utf-8-sig")
        assert_additive_sql("17-to-19 runtime migration",runtime_sql)
        create_test_database(server,RUNTIME_UPGRADE_DB)
        server.sql(builder.render_fresh(baseline,old)+"\n"+legacy_seed_sql()+"\n"+cycle_sentinel_sql(),RUNTIME_UPGRADE_DB)
        before_runtime=fingerprints(server,RUNTIME_UPGRADE_DB,columns)
        for _ in range(2):
            server.sql(runtime_sql,RUNTIME_UPGRADE_DB)
            check_schema(server,RUNTIME_UPGRADE_DB,expected_tables)
            if fingerprints(server,RUNTIME_UPGRADE_DB,columns)!=before_runtime:
                raise VerificationError("Runtime migration modified original legacy values")
            require_scalar(server,"SELECT cycle_code FROM cleaning_cycle_record WHERE id=930000001;","UPGRADE-GUARD-CYCLE","Runtime migration lost draft history",RUNTIME_UPGRADE_DB)
        report["checks"].append("17-to-19 runtime upgrade reruns safely and preserves original topology and draft history")
        report["status"] = "passed"
    except (OSError, ValueError, VerificationError, subprocess.SubprocessError) as failure:
        report["error"] = str(failure)
        if server is not None:
            report["instance_diagnostics"] = server.diagnostic_state()
    finally:
        if server is not None:
            try:
                server.stop_and_cleanup()
                report["cleanup"] = "owned process stopped and owned temporary directory removed"
            except (OSError, VerificationError, subprocess.SubprocessError) as failure:
                report["status"] = "failed"
                report["cleanup"] = "failed: " + str(failure)
                report["cleanup_diagnostics"] = server.diagnostic_state()
        for check in report["checks"]:
            print("PASS: " + check)
        print("RESULT: " + report["status"].upper())
        if "error" in report:
            print("ERROR: " + report["error"])
        print("CLEANUP: " + report["cleanup"])
        if args.report:
            destination = args.report.resolve()
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
