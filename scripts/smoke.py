#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""独立本机TEST撮合、照护、隐私和持久化验证；状态含凭据，不得发布。"""
import argparse
import concurrent.futures
import datetime
import hashlib
import http.cookiejar
import json
import os
import secrets
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHECKS = 0


def check(condition, message):
    """核对真实返回，失败立即停止。知华科技 https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。"""
    global CHECKS
    CHECKS += 1
    if not condition:
        raise AssertionError(message)


def private_write(path, value):
    """私有状态以0600存储，不打印密码或Cookie。知华科技 https://www.zhuatech.cn/。"""
    fd = os.open(Path(path), os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
    Path(path).chmod(0o600)


class Client:
    """独立Cookie会话与实际CSRF；不重试写入，不访问生产域名。知华科技 https://www.zhuatech.cn/。"""

    def __init__(self, base):
        parsed = urllib.parse.urlparse(base)
        if parsed.scheme != "http" or parsed.hostname not in [
            "127.0.0.1",
            "localhost",
            "::1",
        ]:
            raise ValueError("TEST client supports localhost HTTP only")
        self.base = base.rstrip("/")
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
        )

    def response(self, path, method="GET", body=None, csrf=True):
        headers = {}
        if method not in ["GET", "HEAD"] and csrf:
            token = self.call("/auth/csrf")
            headers[token["header"]] = token["token"]
        data = None
        if body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(
            self.base + "/api" + path, data=data, headers=headers, method=method
        )
        try:
            with self.opener.open(req, timeout=30) as r:
                return r.status, r.read(), r.headers.get("Content-Type", "")
        except urllib.error.HTTPError as e:
            return e.code, e.read(), e.headers.get("Content-Type", "")

    def call(self, path, method="GET", body=None):
        status, data, mime = self.response(path, method, body)
        check(status == 200, f"{method} {path} returned {status}")
        return json.loads(data) if "json" in mime else data

    def fail(self, path, method, body, status, code):
        actual, data, _ = self.response(path, method, body)
        check(actual == status, f"Expected {status}, got {actual}: {path}")
        if method != "HEAD":
            check(json.loads(data)["code"] == code, f"Unexpected error code: {path}")

    def login(self, username, password):
        return self.call(
            "/auth/login", "POST", {"username": username, "password": password}
        )

    def photo(self, order, version, caption, data):
        boundary = "PetStayTEST" + secrets.token_hex(12)
        body = bytearray()
        for key, value in [("version", str(version)), ("caption", caption)]:
            body.extend(
                f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode()
            )
        body.extend(
            f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="TEST.jpg"\r\nContent-Type: image/jpeg\r\n\r\n'.encode()
        )
        body.extend(data)
        body.extend(f"\r\n--{boundary}--\r\n".encode())
        token = self.call("/auth/csrf")
        req = urllib.request.Request(
            self.base + f"/api/orders/{order}/photos",
            data=bytes(body),
            headers={
                "Content-Type": "multipart/form-data; boundary=" + boundary,
                token["header"]: token["token"],
            },
            method="POST",
        )
        with self.opener.open(req, timeout=30) as r:
            check(r.status == 200, "Private photo upload")
            return json.load(r)


def seed(base, state_path, env_file):
    """仅空测试库录入明确TEST数据，验证双方闭环；不向他人发送任何消息。知华科技 https://www.zhuatech.cn/。"""
    env = dict(
        line.split("=", 1)
        for line in Path(env_file).read_text().splitlines()
        if line and not line.startswith("#") and "=" in line
    )
    root = Client(base)
    root.login(env.get("ADMIN_USERNAME", "admin"), env["ADMIN_PASSWORD"])
    check(root.call("/orders") == [], "Seed requires an empty isolated TEST database")
    check(Client(base).call("/public/providers") == [], "No pretend initial providers")
    pw = "Aa9" + secrets.token_hex(20)
    clients, identities = {}, {}
    for name in ["owner", "provider", "provider2", "provider3", "other", "outsider"]:
        c = Client(base)
        c.call(
            "/auth/register",
            "POST",
            {
                "username": "test_" + name,
                "password": pw,
                "displayName": "TEST " + name,
                "roleId": 1,
                "departmentId": 999,
            },
        )
        me = c.login("test_" + name, pw)
        check(
            me["permissions"] == ["portal"] and me["scope"] == "ASSIGNED",
            "Public signup does not elevate",
        )
        check(me["departmentId"] == 1, "Public signup cannot pick a team")
        identities[name] = me["id"]
        member = c.call("/profile")
        c.call(
            "/profile",
            "POST",
            {
                **member,
                "contactName": "TEST " + name,
                "contact": "TEST联系渠道，不对应真实人员",
                "city": "上海",
                "district": "闵行区",
            },
        )
        clients[name] = c
    owner, provider, other, outsider = [
        clients[k] for k in ["owner", "provider", "other", "outsider"]
    ]
    today = owner.call("/options")["today"]
    day = datetime.date.fromisoformat(today)
    date = lambda n: (day + datetime.timedelta(days=n)).isoformat()
    provider_ids = {}
    for name, title, city, district, capacity, rate in [
        ("provider", "TEST青禾寄养与上门照护", "上海", "闵行区", 1, 4500),
        ("provider2", "TEST暖窝猫犬照护", "上海", "闵行区", 2, 6000),
        ("provider3", "TEST西湖猫咪寄养", "杭州", "西湖区", 2, 5500),
    ]:
        c = clients[name]
        p = c.call(
            "/provider",
            "POST",
            {
                "displayName": title,
                "city": city,
                "district": district,
                "address": "TEST寄养地点，不对应真实门店",
                "intro": "TEST验收服务资料，提供寄养、喂养与每日照护记录；并非真实营业门店。",
                "species": "CAT" if name == "provider3" else "BOTH",
                "maxWeightKg": 40,
                "boarding": True,
                "homeVisit": True,
                "boardingCapacity": capacity,
                "visitCapacity": capacity,
                "boardingPriceMinor": 9000,
                "visitPriceMinor": rate,
            },
        )
        provider_ids[name] = p["id"]
        check(
            not any(x["id"] == p["id"] for x in Client(base).call("/public/providers")),
            "Pending provider hidden",
        )
        root.call(
            f'/moderate/providers/{p["id"]}',
            "POST",
            {
                "version": p["version"],
                "status": "APPROVED",
                "note": "TEST运营审阅资料，非实名认证",
            },
        )
        for kind, win in [
            ("BOARDING", "NIGHT"),
            ("HOME_VISIT", "AM"),
            ("HOME_VISIT", "PM"),
            ("HOME_VISIT", "EVENING"),
        ]:
            c.call(
                "/availability",
                "POST",
                {
                    "kind": kind,
                    "visitWindow": win,
                    "startsOn": today,
                    "endsOn": date(20),
                },
            )
    public = Client(base).call("/public/providers")
    check(len(public) == 3, "Three real persisted TEST profiles")
    check(
        "address" not in json.dumps(public) and "contact" not in json.dumps(public),
        "Public catalog privacy",
    )
    owner.fail("/admin/users", "GET", None, 403, "FORBIDDEN")
    provider.fail("/moderate", "GET", None, 403, "FORBIDDEN")
    pet = owner.call(
        "/pets",
        "POST",
        {
            "name": "TEST圆圆",
            "species": "CAT",
            "weightKg": "4.20",
            "careNotes": "TEST每日换水，按双方约定照护。",
            "enabled": True,
        },
    )
    pet2 = other.call(
        "/pets",
        "POST",
        {
            "name": "TEST小白",
            "species": "DOG",
            "weightKg": "8.50",
            "careNotes": "TEST照护说明",
            "enabled": True,
        },
    )

    def request(c, animal, kind="HOME_VISIT", window="AM", start=0, end=1):
        r = c.call(
            "/requests",
            "POST",
            {
                "petId": animal,
                "kind": kind,
                "visitWindow": window,
                "startsOn": date(start),
                "endsOn": date(end),
                "city": "上海",
                "district": "闵行区",
                "title": (
                    "TEST圆圆照护安排" if animal == pet["id"] else "TEST小白照护安排"
                ),
                "publicNote": "TEST需求，期待收到匹配的照护报价。",
                "address": "TEST上门交接地点，不对应真实住宅",
                "careNotes": "TEST交接物品与照护要求，不涉及真实客户。",
            },
        )
        return c.call(f'/requests/{r["id"]}/publish', "POST", {"version": r["version"]})

    def quote(c, r):
        return c.call(
            f'/requests/{r["id"]}/propose',
            "POST",
            {"version": r["version"], "note": "TEST按公开资料提供照护，固定报价。"},
        )

    def select(c, r, q):
        return c.call(
            f'/requests/{r["id"]}/select',
            "POST",
            {
                "version": r["version"],
                "offerId": q["id"],
                "offerVersion": q["version"],
                "note": "TEST双方确认日期、地点与固定金额",
            },
        )

    def order(c, id):
        return c.call(f"/orders/{id}")

    def action(c, id, name):
        return c.call(
            f"/orders/{id}/{name}",
            "POST",
            {
                "version": order(c, id)["order"]["version"],
                "note": "TEST " + name + "实际交接记录",
            },
        )

    home = request(owner, pet["id"])
    matches = owner.call(f'/requests/{home["id"]}')["candidates"]
    check(len(matches) == 2, "Real area, species and date matching")
    opportunities = provider.call("/opportunities")
    check(
        "address" not in json.dumps(opportunities)
        and "careNotes" not in json.dumps(opportunities),
        "Candidate privacy",
    )
    q1, q2 = quote(provider, home), quote(clients["provider2"], home)
    chosen = select(owner, home, q1)
    oid = chosen["id"]
    check(chosen["totalMinor"] == 4500, "Fixed visit quotation")
    check(
        owner.call(f'/requests/{home["id"]}')["offers"][1]["status"] == "NOT_SELECTED",
        "Other quote not selected",
    )
    outsider.fail(f"/orders/{oid}", "GET", None, 403, "OUT_OF_SCOPE")
    outsider.fail(f"/orders/{oid}/export.csv", "HEAD", None, 403, "OUT_OF_SCOPE")
    root.fail(
        f"/orders/{oid}/start",
        "POST",
        {"version": 1, "note": "TEST root cannot impersonate"},
        403,
        "OUT_OF_SCOPE",
    )
    frozen = order(owner, oid)["snapshot"]
    owner.call(
        f'/pets/{pet["id"]}',
        "PUT",
        {**pet, "name": "TEST圆圆更新档案", "careNotes": "TEST新的档案说明"},
    )
    check(
        order(owner, oid)["snapshot"] == frozen,
        "Frozen pet and contact context retained",
    )
    action(provider, oid, "start")
    task = order(provider, oid)["tasks"][0]
    provider.call(
        f'/orders/{oid}/tasks/{task["id"]}/complete',
        "POST",
        {
            "version": order(provider, oid)["order"]["version"],
            "note": "TEST实际喂养、换水和环境核对完成。",
        },
    )
    photo = provider.photo(
        oid,
        order(provider, oid)["order"]["version"],
        "TEST图像格式验收：品牌素材，不是宠物照片",
        (ROOT / "frontend/public/brand/logo.jpg").read_bytes(),
    )
    status, pixels, mime = owner.response(f'/orders/{oid}/photos/{photo["id"]}')
    check(status == 200 and mime == "image/jpeg", "Authorized photo type")
    check(
        hashlib.sha256(pixels).hexdigest() == photo["sha256"],
        "Sanitized photo bytes equal persisted hash",
    )
    outsider.fail(
        f'/orders/{oid}/photos/{photo["id"]}', "GET", None, 403, "OUT_OF_SCOPE"
    )
    status, _, _ = outsider.response(f'/orders/{oid}/photos/{photo["id"]}', "HEAD")
    check(status == 403, "Photo HEAD shares privacy authorization")
    done = next(
        e for e in order(provider, oid)["entries"] if e["action"] == "TASK_DONE"
    )
    provider.call(
        f'/orders/{oid}/tasks/{task["id"]}/correct',
        "POST",
        {
            "version": order(provider, oid)["order"]["version"],
            "entryId": done["id"],
            "note": "TEST追加说明：原记录仍保留。",
        },
    )
    check(done in order(owner, oid)["entries"], "Original care entry unchanged")
    receipt = provider.call(
        f"/orders/{oid}/money",
        "POST",
        {
            "version": order(provider, oid)["order"]["version"],
            "kind": "RECEIPT",
            "amountMinor": 4500,
            "reference": "TEST_RECEIPT_01",
            "note": "TEST已核实线下收款，不发起扣款",
        },
    )
    action(provider, oid, "finish")
    action(owner, oid, "dispute")
    action(provider, oid, "respond")
    action(owner, oid, "accept")
    review = owner.call(
        f"/orders/{oid}/review",
        "POST",
        {
            "version": order(owner, oid)["order"]["version"],
            "rating": 5,
            "comment": "TEST完成订单评价，不是真实消费者评价。",
        },
    )
    check(
        Client(base).call(f'/public/providers/{provider_ids["provider"]}/reviews')
        == [],
        "Pending review not public",
    )
    root.call(
        f'/moderate/reviews/{review["id"]}',
        "POST",
        {
            "version": review["version"],
            "status": "APPROVED",
            "note": "TEST评价审核与明确标识",
        },
    )
    check(
        len(Client(base).call(f'/public/providers/{provider_ids["provider"]}/reviews'))
        == 1,
        "Moderated review displayed",
    )
    provider.call(
        f"/orders/{oid}/money",
        "POST",
        {
            "version": order(provider, oid)["order"]["version"],
            "kind": "REFUND",
            "amountMinor": 500,
            "originalId": receipt["id"],
            "reference": "TEST_REFUND_01",
            "note": "TEST原款部分退款；不改写完成状态",
        },
    )
    provider.fail(
        f"/orders/{oid}/money",
        "POST",
        {
            "version": order(provider, oid)["order"]["version"],
            "kind": "REFUND",
            "amountMinor": 4500,
            "originalId": receipt["id"],
            "reference": "TEST_OVER_REFUND",
            "note": "TEST over refund",
        },
        409,
        "REFUND_EXCEEDS",
    )
    check(
        order(owner, oid)["order"]["status"] == "COMPLETED",
        "Refund preserves order history",
    )
    boarding = request(owner, pet["id"], "BOARDING", "NIGHT", 1, 3)
    board = select(owner, boarding, quote(provider, boarding))
    check(
        len(order(owner, board["id"])["tasks"]) == 6, "Boarding per-day task creation"
    )
    check(
        order(owner, board["id"])["snapshot"]["serviceAddress"].startswith("TEST寄养"),
        "Boarding uses provider address",
    )
    provider.fail(
        f'/orders/{board["id"]}/start',
        "POST",
        {"version": 1, "note": "TEST future boarding"},
        409,
        "STATE_INVALID",
    )
    avail = provider.call("/provider")["availability"][0]
    provider.fail(
        f'/availability/{avail["id"]}',
        "DELETE",
        {"version": avail["version"]},
        409,
        "ACTIVE_ORDERS",
    )
    race1 = request(owner, pet["id"], "HOME_VISIT", "PM", 5, 6)
    race2 = request(other, pet2["id"], "HOME_VISIT", "PM", 5, 6)
    offers_race = [quote(provider, race1), quote(provider, race2)]

    def concurrent_select(args):
        c, r, q = args
        return c.response(
            f'/requests/{r["id"]}/select',
            "POST",
            {
                "version": r["version"],
                "offerId": q["id"],
                "offerVersion": q["version"],
                "note": "TEST容量并发确认",
            },
        )[0]

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(
            pool.map(
                concurrent_select,
                [(owner, race1, offers_race[0]), (other, race2, offers_race[1])],
            )
        )
    check(sorted(results) == [200, 409], "Capacity race exactly 200/409")
    status, _, _ = owner.response("/pets", "POST", {}, csrf=False)
    check(status == 403, "Actual server CSRF protects writes")
    check(root.call("/reports")["orders"] == 3, "Real persisted metrics")
    state = {
        "verificationDate": today,
        "adminUsername": env.get("ADMIN_USERNAME", "admin"),
        "adminPassword": env["ADMIN_PASSWORD"],
        "memberPassword": pw,
        "identities": identities,
        "providerIds": provider_ids,
        "petId": pet["id"],
        "homeOrderId": oid,
        "boardingOrderId": board["id"],
        "photoId": photo["id"],
    }
    private_write(state_path, state)
    print(
        json.dumps(
            {
                "mode": "seed",
                "checks": CHECKS,
                "result": "passed",
                "privateState": "created; credentials omitted",
            },
            ensure_ascii=False,
        )
    )


def snapshot(base, state_path):
    """采集全部授权目录、订单记录、媒体hash及CSV；排除重新登录产生的LOGIN审计。知华科技 https://www.zhuatech.cn/。"""
    state = json.loads(Path(state_path).read_text())
    root = Client(base)
    root.login(state["adminUsername"], state["adminPassword"])
    if "verificationDate" not in state:
        state["verificationDate"] = root.call("/options")["today"]
        private_write(state_path, state)
    data = {}
    for kind in [
        "users",
        "roles",
        "permissions",
        "departments",
        "menus",
        "dictionaries",
        "settings",
    ]:
        data["admin/" + kind] = root.call("/admin/" + kind)
    data["public"] = Client(base).call("/public/providers")
    data["moderate"] = root.call("/moderate")
    data["reports"] = root.call("/reports")
    data["audit"] = [r for r in root.call("/audit") if r["action"] != "LOGIN"]
    data["orders"] = root.call("/orders")
    for row in data["orders"]:
        id = row["id"]
        detail = root.call(f"/orders/{id}")
        # Runtime date is view metadata, not a persisted business field.
        detail.pop("today", None)
        data[f"order/{id}"] = detail
        data[f"csv/{id}"] = root.call(f"/orders/{id}/export.csv").decode("utf-8")
        for photo in detail["photos"]:
            status, media, _ = root.response(f'/orders/{id}/photos/{photo["id"]}')
            check(status == 200, "Restored private photo accessible")
            digest = hashlib.sha256(media).hexdigest()
            check(digest == photo["sha256"], "Private photo unchanged")
            data[f'photo/{photo["id"]}'] = digest
    for name in state["identities"]:
        c = Client(base)
        c.login("test_" + name, state["memberPassword"])
        for kind in [
            "profile",
            "pets",
            "requests",
            "provider",
            "orders",
            "shops",
            "notifications",
            "notification-preferences",
        ]:
            data[name + "/" + kind] = c.call("/" + kind)
        for shop in data[name + "/shops"]:
            if shop["status"] == "ACTIVE":
                today = state["verificationDate"]
                to = (
                    datetime.date.fromisoformat(today) + datetime.timedelta(days=7)
                ).isoformat()
                calendar = c.call(f'/shops/{shop["id"]}/calendar?from={today}&to={to}')
                calendar.pop("today", None)
                for order in calendar["orders"]:
                    order.pop("overdue", None)
                data[name + "/calendar/" + str(shop["id"])] = calendar
    return data


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--base", default="http://127.0.0.1:19141")
    p.add_argument("--env-file", default=str(ROOT / ".env"))
    p.add_argument("--state", required=True)
    p.add_argument("--mode", choices=["seed", "capture", "compare"], default="compare")
    p.add_argument("--snapshot")
    a = p.parse_args()
    if a.mode == "seed":
        seed(a.base, a.state, a.env_file)
        return
    if not a.snapshot:
        p.error("--snapshot required")
    result = snapshot(a.base, a.state)
    if a.mode == "capture":
        private_write(a.snapshot, result)
    else:
        check(
            result == json.loads(Path(a.snapshot).read_text()),
            "Persisted directories, orders, photos and CSV exactly match",
        )
    print(
        json.dumps(
            {"mode": a.mode, "checks": CHECKS, "sets": len(result), "result": "passed"}
        )
    )


if __name__ == "__main__":
    main()
