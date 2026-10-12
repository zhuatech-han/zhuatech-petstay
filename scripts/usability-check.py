#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""仅指定独立TEST Compose验证历史分页和照片额度；状态含凭据，禁止发布。"""
import argparse
import concurrent.futures
import datetime
import json
import re
import subprocess
import urllib.error
import urllib.parse
from pathlib import Path

import smoke as flow


def main():
    """拒绝已有大量数据或非TEST身份，不对运营实例注入历史。知华科技 https://www.zhuatech.cn/。"""
    p = argparse.ArgumentParser()
    p.add_argument("--base", required=True)
    p.add_argument("--project", required=True)
    p.add_argument("--env-file", required=True)
    p.add_argument("--state", required=True)
    a = p.parse_args()
    if not re.fullmatch(r"petstay-usability-[a-z0-9-]{1,30}", a.project):
        p.error("Only the named isolated usability TEST project is supported")
    state = json.loads(Path(a.state).read_text())
    root = flow.Client(a.base)
    root.login(state["adminUsername"], state["adminPassword"])
    owner = flow.Client(a.base)
    owner.login("test_owner", state["memberPassword"])
    provider = flow.Client(a.base)
    provider.login("test_provider", state["memberPassword"])
    other = flow.Client(a.base)
    other.login("test_other", state["memberPassword"])
    rows = root.call("/orders")
    flow.check(len(rows) == 3, "Requires exactly the three TEST smoke orders")
    for row in rows:
        flow.check(
            root.call(f'/orders/{row["id"]}')["snapshot"]["petName"].startswith("TEST"),
            "Only synthetic TEST pets may be used",
        )
    prefix = [
        "docker",
        "compose",
        "--env-file",
        str(Path(a.env_file).resolve()),
        "-p",
        a.project,
    ]

    def sql(query):
        return subprocess.run(
            prefix
            + [
                "exec",
                "-T",
                "mysql",
                "sh",
                "-c",
                'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --batch --raw --skip-column-names -u"$MYSQL_USER" "$MYSQL_DATABASE"',
            ],
            cwd=flow.ROOT,
            input=query,
            text=True,
            check=True,
            capture_output=True,
        ).stdout

    flow.check(
        sql("select count(*) from care_order;").strip() == "3",
        "TEST database matches HTTP",
    )
    source = state["homeOrderId"]
    before = root.call(f"/orders/{source}")
    flow.check(
        before["order"]["status"] == "COMPLETED", "Historical source is completed"
    )
    request_id, offer_id = map(
        int,
        sql(
            f"select request_id,offer_id from care_order where id={int(source)};"
        ).split(),
    )
    columns = {}
    for table in ["care_request", "care_offer", "care_order"]:
        names = [
            line.split("\t")[0]
            for line in sql(f"show columns from {table};").splitlines()
        ]
        columns[table] = [name for name in names if name != "id"]
        flow.check(
            all(re.fullmatch(r"[a-z_]+", name) for name in columns[table]),
            "Fixed TEST columns",
        )

    def clone(table, original, changes):
        names = columns[table]
        values = [changes.get(name, name) for name in names]
        return f'insert into {table} ({",".join(names)}) select {",".join(values)} from {table} where id={int(original)};'

    statements = ["start transaction;"]
    for n in range(5000):
        statements += [
            clone("care_request", request_id, {}),
            "set @test_request=last_insert_id();",
            clone("care_offer", offer_id, {"request_id": "@test_request"}),
            "set @test_offer=last_insert_id();",
            clone(
                "care_order",
                source,
                {
                    "request_id": "@test_request",
                    "offer_id": "@test_offer",
                    "reference": f"'TEST_HIST_{n}'",
                },
            ),
        ]
    statements += ["commit;"]
    sql("\n".join(statements))
    first = owner.call("/orders/page?size=12&q=TEST_HIST_")
    flow.check(
        first["total"] == 5000 and len(first["items"]) == 12,
        "5000 historical records use bounded pages",
    )
    flow.check(
        not any("snapshot" in row for row in first["items"]),
        "Paging omits private snapshots",
    )
    second = owner.call("/orders/page?page=2&size=12&q=TEST_HIST_")
    flow.check(
        not (
            {row["id"] for row in first["items"]}
            & {row["id"] for row in second["items"]}
        ),
        "Pages are distinct",
    )
    flow.check(
        other.call("/orders/page?q=TEST_HIST_")["total"] == 0,
        "Foreign histories remain private",
    )
    flow.check(
        provider.call("/orders/page?q=TEST_HIST_")["total"] == 5000,
        "Assigned provider sees its history",
    )
    flow.check(
        owner.call("/orders/page?q=TEST_HIST_4999")["total"] == 1,
        "Literal reference search",
    )
    flow.check(
        owner.call("/orders/page?q=%25")["total"] == 0,
        "LIKE metacharacters do not widen search",
    )
    flow.check(
        root.call(f"/orders/{source}") == before,
        "Original snapshot, money and photos unchanged",
    )
    owner.fail("/orders/page?size=51", "GET", None, 400, "INVALID_INPUT")
    profile = owner.call("/profile")
    today = datetime.date.fromisoformat(owner.call("/options")["today"])
    request = owner.call(
        "/requests",
        "POST",
        {
            "petId": state["petId"],
            "kind": "HOME_VISIT",
            "visitWindow": "PM",
            "startsOn": today.isoformat(),
            "endsOn": (today + datetime.timedelta(days=1)).isoformat(),
            "city": profile["city"],
            "district": profile["district"],
            "title": "TEST历史分页后继续接单",
            "careNotes": "TEST格式验收，不是真实照护",
            "address": "TEST上门格式验证地址",
        },
    )
    request = owner.call(
        f'/requests/{request["id"]}/publish', "POST", {"version": request["version"]}
    )
    offer = provider.call(
        f'/requests/{request["id"]}/propose',
        "POST",
        {"version": request["version"], "note": "TEST本单格式验证"},
    )
    order = owner.call(
        f'/requests/{request["id"]}/select',
        "POST",
        {
            "version": request["version"],
            "offerId": offer["id"],
            "offerVersion": offer["version"],
            "note": "TEST确认验证",
        },
    )
    flow.check(
        order["status"] == "CONFIRMED",
        "History does not consume active-order allowance",
    )
    oid = order["id"]
    provider.call(
        f"/orders/{oid}/start",
        "POST",
        {"version": order["version"], "note": "TEST开始格式验证"},
    )
    image = (flow.ROOT / "frontend/public/brand/logo.jpg").read_bytes()
    for _ in range(4):
        current = provider.call(f"/orders/{oid}")["order"]
        provider.photo(
            oid, current["version"], "TEST品牌格式验收图，不是宠物照片", image
        )
    version = provider.call(f"/orders/{oid}")["order"]["version"]

    def fifth(_):
        c = flow.Client(a.base)
        c.login("test_provider", state["memberPassword"])
        try:
            c.photo(oid, version, "TEST并发格式验收图，不是宠物照片", image)
            return 200
        except urllib.error.HTTPError as error:
            return error.code

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        result = list(pool.map(fifth, range(2)))
    flow.check(
        sorted(result) == [200, 409], "Concurrent fifth photo persists exactly once"
    )
    quota = owner.call(f"/orders/{oid}/photo-allowance")
    flow.check(
        quota["dailyCount"] == 5 and quota["totalBytes"] > 0,
        "Daily count and sanitized bytes match real data",
    )
    current = provider.call(f"/orders/{oid}")["order"]
    try:
        provider.photo(oid, current["version"], "TEST第六张验证", image)
        raise AssertionError("Sixth photo must fail")
    except urllib.error.HTTPError as error:
        flow.check(
            error.code == 409 and json.load(error)["code"] == "PHOTO_DAILY_LIMIT",
            "Sixth photo rejected with current version",
        )
    other.fail(f"/orders/{oid}/photo-allowance", "GET", None, 403, "OUT_OF_SCOPE")
    state["usabilityOrderId"] = oid
    flow.private_write(a.state, state)
    print(
        json.dumps(
            {
                "result": "passed",
                "checks": flow.CHECKS,
                "historicalOrders": 5000,
                "photoRace": sorted(result),
                "scope": "isolated MySQL TEST; not production load or physical-device validation",
            }
        )
    )


if __name__ == "__main__":
    main()
