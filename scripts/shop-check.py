#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""本机独立TEST库门店闭环及真实SMTP捕获验收，不访问外部收件人。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse
import datetime
import email
import json
import re
import time
from pathlib import Path
import smoke


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--base", required=True)
    p.add_argument("--state", required=True)
    p.add_argument("--smtp-capture")
    p.add_argument("--failure-marker")
    a = p.parse_args()
    state = json.loads(Path(a.state).read_text())
    smoke.check(
        set(["owner", "provider", "other", "provider3"]).issubset(state["identities"]),
        "TEST seed state required",
    )
    cs = {}
    for name in ["owner", "provider", "other", "provider2", "provider3", "outsider"]:
        c = smoke.Client(a.base)
        me = c.login("test_" + name, state["memberPassword"])
        smoke.check(me["displayName"].startswith("TEST "), "TEST accounts required")
        cs[name] = c
    owner, provider, staff, manager, second, outsider = [
        cs[k]
        for k in ["owner", "provider", "other", "provider3", "provider2", "outsider"]
    ]
    shop = state["providerIds"]["provider"]
    shop2 = state["providerIds"]["provider2"]
    today = owner.call("/options")["today"]
    tomorrow = (
        datetime.date.fromisoformat(today) + datetime.timedelta(days=1)
    ).isoformat()
    smoke.check(
        provider.call("/provider")["provider"]["displayName"].startswith("TEST"),
        "TEST provider required",
    )
    provider.call(f"/shops/{shop}/policy", "POST", {"workforceEnabled": True})
    memberships = {}
    for name, role in [("other", "CARER"), ("provider3", "MANAGER")]:
        invitation = provider.call(
            f"/shops/{shop}/invite", "POST", {"username": "test_" + name, "role": role}
        )
        memberships[name] = invitation
        cs[name].fail(f"/shops/{shop}/calendar", "GET", None, 403, "OUT_OF_SCOPE")
        cs[name].call(
            f'/staff/{invitation["id"]}/accept',
            "POST",
            {"version": invitation["version"]},
        )
        provider.call(
            f"/shops/{shop}/rosters",
            "POST",
            {
                "accountId": state["identities"][name],
                "startsOn": today,
                "endsOn": tomorrow,
                "visitWindow": "DAY",
            },
        )

    def pet(name):
        return owner.call(
            "/pets",
            "POST",
            {
                "name": "TEST " + name,
                "species": "CAT",
                "weightKg": "4.2",
                "careNotes": "TEST synthetic care requirements",
                "enabled": True,
            },
        )["id"]

    def book(animal, kind, win, service):
        req = owner.call(
            "/requests",
            "POST",
            {
                "petId": animal,
                "kind": kind,
                "visitWindow": win,
                "startsOn": today,
                "endsOn": tomorrow,
                "city": "上海",
                "district": "闵行区",
                "title": "TEST 1.2 " + kind,
                "publicNote": "TEST synthetic service",
                "address": "TEST synthetic address",
                "careNotes": "TEST synthetic requirements",
            },
        )
        req = owner.call(
            f'/requests/{req["id"]}/publish', "POST", {"version": req["version"]}
        )
        quote = service.call(
            f'/requests/{req["id"]}/propose',
            "POST",
            {"version": req["version"], "note": "TEST actual fixed quote"},
        )
        return owner.call(
            f'/requests/{req["id"]}/select',
            "POST",
            {
                "version": req["version"],
                "offerId": quote["id"],
                "offerVersion": quote["version"],
                "note": "TEST confirmed",
            },
        )["id"]

    def detail(c, id):
        return c.call(f"/orders/{id}")

    def action(c, id, verb, **extra):
        return c.call(
            f"/orders/{id}/{verb}",
            "POST",
            {
                "version": detail(c, id)["order"]["version"],
                "note": "TEST actual " + verb,
                **extra,
            },
        )

    board = book(pet("store boarding"), "BOARDING", "NIGHT", provider)
    staff.fail(f"/orders/{board}", "GET", None, 403, "OUT_OF_SCOPE")
    provider.fail(
        f"/orders/{board}/start",
        "POST",
        {
            "version": detail(provider, board)["order"]["version"],
            "note": "TEST no staffing",
        },
        409,
        "ASSIGNMENT_REQUIRED",
    )
    action(
        provider,
        board,
        "assign",
        accountId=state["identities"]["other"],
        taskDate=today,
    )
    smoke.check(
        any(x["id"] == board for x in staff.call("/orders/page")["items"]),
        "Assigned staff has authorized paging",
    )
    outsider.fail(f"/orders/{board}", "GET", None, 403, "OUT_OF_SCOPE")
    outsider.fail(f"/shops/{shop}/calendar", "GET", None, 403, "OUT_OF_SCOPE")
    roster = provider.call(f"/shops/{shop}/calendar")["rosters"][0]
    provider.fail(
        f'/rosters/{roster["id"]}',
        "DELETE",
        {"version": roster["version"]},
        409,
        "ASSIGNMENTS_PENDING",
    )
    provider.fail(
        f'/staff/{memberships["other"]["id"]}/revoke',
        "POST",
        {"version": memberships["other"]["version"] + 1},
        409,
        "ASSIGNMENTS_PENDING",
    )
    action(manager, board, "start", belongings="TEST carrier and feeding bowl")
    for task in detail(staff, board)["tasks"]:
        provider.fail(
            f'/orders/{board}/tasks/{task["id"]}/complete',
            "POST",
            {
                "version": detail(provider, board)["order"]["version"],
                "note": "TEST wrong assignee",
            },
            403,
            "OUT_OF_SCOPE",
        )
        staff.call(
            f'/orders/{board}/tasks/{task["id"]}/complete',
            "POST",
            {
                "version": detail(staff, board)["order"]["version"],
                "note": "TEST actual employee care",
            },
        )
    smoke.check(
        len(detail(owner, board)["handovers"]) == 1, "Actual check-in persisted"
    )
    manager.fail(
        f"/orders/{board}/finish",
        "POST",
        {
            "version": detail(manager, board)["order"]["version"],
            "note": "TEST not collected",
        },
        409,
        "CHECK_OUT_REQUIRED",
    )
    action(
        manager,
        board,
        "check-out",
        recipient="TEST authorized owner",
        belongings="TEST carrier and bowl returned",
    )
    action(
        provider,
        board,
        "money",
        kind="RECEIPT",
        amountMinor=detail(provider, board)["order"]["totalMinor"],
        reference="TEST_SHOP_BOARD_RECEIPT",
    )
    action(manager, board, "finish")
    action(owner, board, "accept")
    d = detail(owner, board)
    smoke.check(
        d["order"]["status"] == "COMPLETED"
        and d["order"]["stayStatus"] == "DEPARTED"
        and len(d["handovers"]) == 2,
        "Physical collection and service confirmation complete",
    )
    # Create another managed order for live schedule and employee GUI verification.
    visit = book(pet("staff visit"), "HOME_VISIT", "AM", provider)
    action(
        provider,
        visit,
        "assign",
        accountId=state["identities"]["other"],
        taskDate=today,
    )
    second.call(f"/shops/{shop2}/policy", "POST", {"workforceEnabled": True})
    inv = second.call(
        f"/shops/{shop2}/invite", "POST", {"username": "test_other", "role": "CARER"}
    )
    staff.call(f'/staff/{inv["id"]}/accept', "POST", {"version": inv["version"]})
    second.call(
        f"/shops/{shop2}/rosters",
        "POST",
        {
            "accountId": state["identities"]["other"],
            "startsOn": today,
            "endsOn": tomorrow,
            "visitWindow": "AM",
        },
    )
    conflicting = book(pet("cross shop visit"), "HOME_VISIT", "AM", second)
    second.fail(
        f"/orders/{conflicting}/assign",
        "POST",
        {
            "version": detail(second, conflicting)["order"]["version"],
            "accountId": state["identities"]["other"],
            "taskDate": today,
            "note": "TEST overlapping cross-shop visit",
        },
        409,
        "STAFF_CONFLICT",
    )
    staff.fail(f"/orders/{conflicting}", "GET", None, 403, "OUT_OF_SCOPE")
    # Freeze guarantees: disabling new-booking mode cannot bypass already-required staff.
    pol = provider.call(f"/shops/{shop}/calendar")["policy"]
    provider.call(
        f"/shops/{shop}/policy",
        "POST",
        {"version": pol["version"], "workforceEnabled": False},
    )
    smoke.check(
        detail(provider, visit)["order"]["managedCare"], "Existing staff mode retained"
    )
    pol = provider.call(f"/shops/{shop}/calendar")["policy"]
    provider.call(
        f"/shops/{shop}/policy",
        "POST",
        {"version": pol["version"], "workforceEnabled": True},
    )
    if a.smtp_capture:
        capture = Path(a.smtp_capture)
        owner.call(
            "/notification-email/request",
            "POST",
            {"email": "test-petstay-owner@example.invalid"},
        )
        messages = [json.loads(x) for x in capture.read_text().splitlines()]
        raw = next(
            x["message"]
            for x in reversed(messages)
            if "test-petstay-owner@example.invalid" in x["recipient"]
        )
        message = email.message_from_string(raw)
        body = message.get_payload(decode=True).decode(
            message.get_content_charset() or "utf-8"
        )
        code = re.search(r"code: (\d{6})", body).group(1)
        owner.call("/notification-email/verify", "POST", {"code": code})
        pref = owner.call("/notification-preferences")
        owner.call(
            "/notification-preferences",
            "POST",
            {**pref, "emailEnabled": True, "reminderHour": 0},
        )
        marker = Path(a.failure_marker) if a.failure_marker else None
        if marker:
            marker.touch()
        action(manager, visit, "start")
        deadline = time.monotonic() + 60
        record = None
        while time.monotonic() < deadline:
            record = next(
                (
                    n
                    for n in owner.call("/notifications")["items"]
                    if n["orderId"] == visit and n["event"] == "START"
                ),
                None,
            )
            if record and record["deliveryStatus"] == ("RETRY" if marker else "SENT"):
                break
            time.sleep(2)
        smoke.check(
            record and record["deliveryStatus"] == ("RETRY" if marker else "SENT"),
            "Actual SMTP failure or success recorded",
        )
        if marker:
            marker.unlink()
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                record = next(
                    n
                    for n in owner.call("/notifications")["items"]
                    if n["orderId"] == visit and n["event"] == "START"
                )
                if record["deliveryStatus"] == "SENT":
                    break
                time.sleep(3)
            smoke.check(
                record["deliveryStatus"] == "SENT" and record["attempts"] == 2,
                "Actual SMTP rejection then retry accepted",
            )
        messages = [json.loads(x) for x in capture.read_text().splitlines()]
        raw = next(
            x["message"]
            for x in reversed(messages)
            if "test-petstay-owner@example.invalid" in x["recipient"]
        )
        body = email.message_from_string(raw).get_payload(decode=True).decode("utf-8")
        smoke.check(
            "TEST synthetic address" not in body
            and "TEST synthetic requirements" not in body,
            "Email excludes private customer data",
        )
        pref = owner.call("/notification-preferences")
        owner.call("/notification-preferences", "POST", {**pref, "emailEnabled": False})
    elif detail(provider, visit)["order"]["status"] == "CONFIRMED":
        action(manager, visit, "start")
    inbox = owner.call("/notifications")
    smoke.check(
        inbox["total"] > 0 and owner.call("/notifications/unread")["unread"] > 0,
        "In-app inbox and unread navigation persisted",
    )
    n = inbox["items"][0]
    outsider.fail(
        f'/notifications/{n["id"]}/read',
        "POST",
        {"version": n["version"]},
        403,
        "OUT_OF_SCOPE",
    )
    owner.call(f'/notifications/{n["id"]}/read', "POST", {"version": n["version"]})
    state["shopChecks"] = {
        "shopId": shop,
        "boardingId": board,
        "visitId": visit,
        "conflictingId": conflicting,
    }
    smoke.private_write(a.state, state)
    print(
        json.dumps(
            {
                "result": "passed",
                "checks": smoke.CHECKS,
                "scope": "TEST shop workflow; SMTP acceptance only, not public mailbox delivery",
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
