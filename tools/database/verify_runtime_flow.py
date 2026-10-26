#!/usr/bin/env python3
"""Run Java runtime integration checks only against an owned temporary MySQL."""
from pathlib import Path
import argparse
import json
import os
import subprocess
import time
import xml.etree.ElementTree as ET
import verify_schema_v2 as schema

def quote(value):
    return "'" + str(value).replace("'", "''") + "'"

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mysql-bin-dir",type=Path,required=True)
    parser.add_argument("--maven-command",type=Path,required=True)
    parser.add_argument("--java-home",type=Path,required=True)
    parser.add_argument("--maven-settings",type=Path)
    parser.add_argument("--maven-repository",type=Path)
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[2]
    parent=root/"archive/database-validation"
    server=schema.IsolatedMysql(args.mysql_bin_dir,60,parent)
    report={"status":"failed","checks":[]}
    try:
        server.start()
        schema.create_test_database(server,schema.FRESH_DB)
        server.sql((root/"src/main/resources/schema.sql").read_text(encoding="utf-8-sig"),schema.FRESH_DB)
        url=f"jdbc:mysql://127.0.0.1:{server.port}/{schema.FRESH_DB}?connectionTimeZone=UTC"
        arguments=["-o","-B","-ntp","-Dtest=RuntimeDatabaseIT",
                   "-Druntime.it.url="+url,"-Druntime.it.datadir="+str(server.data_dir),
                   "-Druntime.it.owner="+server.token]
        if args.maven_settings: arguments+=["-s",str(args.maven_settings.resolve())]
        if args.maven_repository: arguments+=["-Dmaven.repo.local="+str(args.maven_repository.resolve())]
        arguments+=["test"]
        command="$env:JAVA_HOME="+quote(args.java_home.resolve())+"; & "+quote(args.maven_command.resolve())+" "+" ".join(quote(a) for a in arguments)
        log=root/"archive/runtime-integration.log"
        started=time.time()
        with log.open("w",encoding="utf-8") as output:
            result=subprocess.run(["powershell.exe","-NoProfile","-NonInteractive","-Command",command],
                                  cwd=root,stdout=output,stderr=subprocess.STDOUT,creationflags=schema.CREATE_FLAGS,timeout=300)
        if result.returncode:
            print(log.read_text(encoding="utf-8",errors="replace")[-12000:].encode("ascii","backslashreplace").decode("ascii"))
            raise schema.VerificationError("Java runtime integration checks failed")
        test_report=root/"target/surefire-reports/TEST-com.company.project.service.cleaning.RuntimeDatabaseIT.xml"
        if not test_report.exists() or test_report.stat().st_mtime<started:
            raise schema.VerificationError("Missing fresh integration test report")
        suite=ET.parse(test_report).getroot()
        if int(suite.get("tests","0"))<1 or any(int(suite.get(k,"0")) for k in ("errors","failures","skipped")):
            raise schema.VerificationError("Integration test did not execute successfully")
        report.update(status="passed",checks=[
            "owned MySQL port/datadir/token verified before Java writes",
            "real inbox and transaction-proxied DAO capture an M106 round",
            "idempotent replay, invalid sample rollback and source checkpoint",
            "serious equipment fault and stable run association",
            "history list/detail/point readback and five periodic height aggregates",
            "platform configuration saves without PLC points, retries are idempotent, version conflicts roll back",
            "real CSV and ZIP exports publish atomically and verify task ownership before downloads",
            "real Spring application starts without a PLC and serves authenticated home/history/trend/archive/configuration HTTP endpoints",
            "legacy administrator endpoint rejects unauthenticated requests",
        ])
        print("PASS: actual Java JDBC runtime flow")
    finally:
        server.stop_and_cleanup()
        report["cleanup"]="owned MySQL stopped and temporary directory removed"
        target=root/"archive/runtime-integration-validation.json"
        target.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
        print("REPORT:",target)

if __name__=="__main__":
    main()
