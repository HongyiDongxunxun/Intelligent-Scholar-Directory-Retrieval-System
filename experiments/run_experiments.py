#!/usr/bin/env python3
import argparse
import concurrent.futures
import datetime as dt
import hashlib
import json
import math
import os
import platform
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path


class Runner:
    def __init__(self, base_url, output):
        self.base_url = base_url.rstrip("/")
        self.output = output
        self.requests = []
        self.responses = []
        self.measurements = []
        self.lock = threading.Lock()

    def call(self, method, path, body=None, scenario="setup"):
        request_id = uuid.uuid4().hex
        raw = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(
            self.base_url + path,
            data=raw,
            method=method,
            headers={"Content-Type": "application/json"},
        )
        started = time.perf_counter()
        status = 0
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                status = response.status
                payload = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            status = exc.code
            payload = json.loads(exc.read().decode("utf-8"))
        except Exception as exc:
            payload = {"success": False, "message": str(exc)}
        elapsed_ms = (time.perf_counter() - started) * 1000
        data = payload.get("data") or {}
        result_size = data.get("total", data.get("candidatePublicationCount", 0)) if isinstance(data, dict) else 0
        measurement = {
            "requestId": request_id,
            "scenario": scenario,
            "method": method,
            "path": path,
            "httpStatus": status,
            "businessSuccess": payload.get("success") is True,
            "elapsedMs": round(elapsed_ms, 3),
            "resultSize": result_size,
            "cacheStatus": data.get("cacheStatus") if isinstance(data, dict) else None,
        }
        with self.lock:
            self.requests.append({"requestId": request_id, "method": method, "path": path, "body": body})
            self.responses.append({"requestId": request_id, "payload": payload})
            self.measurements.append(measurement)
        if status != 200 or payload.get("success") is not True:
            raise RuntimeError(f"{method} {path} failed: {payload.get('message', status)}")
        return payload, measurement


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * fraction) - 1)]


def summarize(rows, wall_seconds):
    latencies = [row[1]["elapsedMs"] for row in rows]
    return {
        "requests": len(rows),
        "successRate": sum(1 for _, row in rows if row["businessSuccess"]) / len(rows),
        "meanMs": round(statistics.mean(latencies), 3),
        "medianMs": round(statistics.median(latencies), 3),
        "observedP95Ms": round(percentile(latencies, 0.95), 3),
        "maxMs": round(max(latencies), 3),
        "rps": round(len(rows) / wall_seconds, 3),
    }


def concurrent_scenario(runner, name, path, body, workers, count):
    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [pool.submit(runner.call, "POST", path, body, name) for _ in range(count)]
        rows = [future.result() for future in futures]
    return summarize(rows, time.perf_counter() - started)


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def write_jsonl(path, values):
    path.write_text("".join(json.dumps(value, ensure_ascii=False) + "\n" for value in values), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description="Run synthetic E1/E2/E3 verification experiments.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8091")
    parser.add_argument("--workers", type=int, default=30)
    parser.add_argument("--requests", type=int, default=60)
    parser.add_argument("--results-dir", default=str(Path(__file__).parent / "results"))
    args = parser.parse_args()

    run_id = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
    output = Path(args.results_dir) / run_id
    output.mkdir(parents=True, exist_ok=False)
    runner = Runner(args.base_url, output)

    index, _ = runner.call("GET", "/api/v1/system/index-status", scenario="environment")
    status = index["data"]
    valid_environment = (
        status.get("consistent") is True
        and status.get("dataMode") == "SYNTHETIC"
        and status.get("databasePublicationCount") == status.get("indexDocumentCount") == 60
    )
    if not valid_environment:
        raise RuntimeError("INVALID_ENVIRONMENT: synthetic index gate failed")

    publication_query = {
        "conditions": [{"operator": "AND", "field": "KEYWORD", "value": "知识组织", "match": "FUZZY"}],
        "filters": {"yearStart": 2014, "yearEnd": 2025, "journalNames": [], "disciplineIds": []},
        "sort": "RELEVANCE", "page": {"size": 20, "cursor": None},
    }
    scholar_query = {
        "conditions": [{"operator": "AND", "field": "KEYWORD", "value": "数字人文", "match": "FUZZY"}],
        "filters": {"journalNames": [], "disciplineIds": []}, "sort": "RELEVANCE",
        "page": {"size": 20, "cursor": None}, "strictInstitution": False,
        "includeCoauthors": True, "scanBatchSize": 100, "maxScanRounds": 5,
    }

    e1 = {}
    for topic in ("知识组织", "数字人文"):
        topic_query = {
            "topic": topic,
            "conditions": [{"operator": "AND", "field": "KEYWORD", "value": topic, "match": "FUZZY"}],
            "filters": {"journalNames": [], "disciplineIds": []}, "candidateLimit": 500, "topSize": 20,
            "assembleTypes": ["PUBLICATION", "SCHOLAR", "INSTITUTION", "KEYWORD"],
            "includeSubtopics": True, "includeGraph": True,
        }
        payload, _ = runner.call("POST", "/api/v1/topics/assemble", topic_query, "e1_topic_assembly")
        result = payload["data"]
        entities = result["papers"] + result["scholars"] + result["institutions"] + result["keywords"]
        formula_ok = all(abs(item["score"] - (0.70 * item["rel"] + 0.20 * item["str"] + 0.10 * item["inf"])) <= 0.00011 for item in entities)
        range_ok = all(0 <= item[key] <= 1 for item in entities for key in ("rel", "str", "inf", "score"))
        e1[topic] = {
            "candidatePublications": result["candidatePublicationCount"],
            "returned": {key: len(result[key]) for key in ("papers", "scholars", "institutions", "keywords")},
            "graph": {"nodes": len(result["graph"]["nodes"]), "links": len(result["graph"]["links"])},
            "formulaCheck": formula_ok,
            "rangeCheck": range_ok,
        }

    parse_outputs = []
    for _ in range(3):
        parsed, _ = runner.call(
            "POST", "/api/v1/ai/scholar-search/parse",
            {"request": "寻找近十年研究数字人文的受限机构甲学者", "locale": "zh-CN"}, "e2_parse",
        )
        parse_outputs.append(parsed["data"])
    parser_stable = all(item["draftQuery"] == parse_outputs[0]["draftQuery"] for item in parse_outputs[1:])
    confirm_ids = []
    confirm_totals = []
    for _ in range(3):
        confirmed, _ = runner.call(
            "POST", "/api/v1/ai/scholar-search/confirm",
            {"approvedQuery": parse_outputs[0]["draftQuery"]}, "e2_confirm",
        )
        confirm_totals.append(confirmed["data"]["total"])
        confirm_ids.append([item["entity"]["id"] for item in confirmed["data"]["items"]])
    e2 = {
        "parserConfigVersion": parse_outputs[0]["parserConfigVersion"],
        "parseStableAcrossThreeRuns": parser_stable,
        "confirmTotals": confirm_totals,
        "confirmOrderStable": all(ids == confirm_ids[0] for ids in confirm_ids[1:]),
        "warnings": parse_outputs[0]["warnings"],
    }

    topic_perf_query = {
        "topic": "知识组织",
        "conditions": [{"operator": "AND", "field": "KEYWORD", "value": "知识组织", "match": "FUZZY"}],
        "filters": {"journalNames": [], "disciplineIds": []}, "candidateLimit": 500, "topSize": 20,
        "assembleTypes": ["PUBLICATION", "SCHOLAR", "INSTITUTION", "KEYWORD"],
        "includeSubtopics": True, "includeGraph": False,
    }
    cold, cold_measurement = runner.call("POST", "/api/v1/topics/assemble", topic_perf_query, "e3_topic_cold")
    warm, warm_measurement = runner.call("POST", "/api/v1/topics/assemble", topic_perf_query, "e3_topic_warmup")
    e3 = {
        "workers": args.workers,
        "requestsPerScenario": args.requests,
        "cacheSequence": {
            "coldStatus": cold["data"]["cacheStatus"], "coldMs": cold_measurement["elapsedMs"],
            "warmStatus": warm["data"]["cacheStatus"], "warmMs": warm_measurement["elapsedMs"],
            "resultEquivalent": {**cold["data"], "cacheStatus": None} == {**warm["data"], "cacheStatus": None},
        },
        "publicationSearch": concurrent_scenario(runner, "e3_publication", "/api/v1/publications/search", publication_query, args.workers, args.requests),
        "scholarSearch": concurrent_scenario(runner, "e3_scholar", "/api/v1/scholars/search", scholar_query, args.workers, args.requests),
        "topicWarmCache": concurrent_scenario(runner, "e3_topic_warm", "/api/v1/topics/assemble", topic_perf_query, args.workers, args.requests),
    }

    summary = {
        "runId": run_id,
        "status": "PASS" if all([
            all(case["formulaCheck"] and case["rangeCheck"] for case in e1.values()),
            e2["parseStableAcrossThreeRuns"], e2["confirmOrderStable"],
            e3["cacheSequence"]["coldStatus"] == "MISS",
            e3["cacheSequence"]["warmStatus"] == "HIT",
            e3["cacheSequence"]["resultEquivalent"],
            all(e3[key]["successRate"] == 1 for key in ("publicationSearch", "scholarSearch", "topicWarmCache")),
        ]) else "FAIL",
        "scope": "Synthetic functional, auditability, and local performance verification",
        "e1": e1, "e2": e2, "e3": e3,
    }
    environment = {
        "runId": run_id, "baseUrl": args.base_url, "utcTime": dt.datetime.now(dt.timezone.utc).isoformat(),
        "platform": platform.platform(), "python": sys.version, "cpuCount": os.cpu_count(),
        "indexStatus": status,
    }
    manifest = {
        "runId": run_id, "status": summary["status"], "dataVersion": "synthetic-fixture-v1",
        "algorithmVersion": "topic-score-v1", "immutableDirectory": True,
    }
    write_json(output / "manifest.json", manifest)
    write_json(output / "environment.json", environment)
    write_json(output / "data_fingerprint.json", {"datasetId": "synthetic-fixture-v1", "counts": {"publications": 60, "scholars": 40, "institutions": 20}})
    write_jsonl(output / "requests.jsonl", runner.requests)
    write_jsonl(output / "responses.jsonl", runner.responses)
    write_jsonl(output / "measurements.jsonl", runner.measurements)
    write_json(output / "summary.json", summary)

    report = f"""# 合成数据实验报告\n\n- 运行编号：`{run_id}`\n- 状态：**{summary['status']}**\n- 数据版本：`synthetic-fixture-v1`\n- 范围：仅验证链路可执行、评分可审计和本地性能可测量。\n\n## E1 专题装配\n\n"""
    for topic, case in e1.items():
        report += f"- {topic}：候选文献 {case['candidatePublications']}；返回 {case['returned']}；公式校验 {case['formulaCheck']}；范围校验 {case['rangeCheck']}。\n"
    report += f"""\n## E2 AI 解析与确认\n\n- 三次解析字段一致：{e2['parseStableAcrossThreeRuns']}\n- 三次确认结果量：{e2['confirmTotals']}\n- 三次确认顺序一致：{e2['confirmOrderStable']}\n- 脱敏别名警告：{e2['warnings']}\n\n## E3 性能与缓存\n\n- 冷/热缓存：{e3['cacheSequence']}\n\n| 场景 | 平均 ms | 观察 P95 ms | RPS | 成功率 |\n|---|---:|---:|---:|---:|\n"""
    for label, key in (("文献检索", "publicationSearch"), ("学者检索", "scholarSearch"), ("专题热缓存", "topicWarmCache")):
        row = e3[key]
        report += f"| {label} | {row['meanMs']} | {row['observedP95Ms']} | {row['rps']} | {row['successRate']:.4f} |\n"
    report += "\n本报告不提供检索质量优越性、真实用户效果或生产性能结论。\n"
    (output / "report.md").write_text(report, encoding="utf-8")

    checksum_lines = []
    for path in sorted(output.iterdir()):
        if path.name != "checksums.sha256":
            checksum_lines.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}")
    (output / "checksums.sha256").write_text("\n".join(checksum_lines) + "\n", encoding="ascii")
    print(json.dumps({"runId": run_id, "status": summary["status"], "report": str(output / "report.md")}, ensure_ascii=False))
    return 0 if summary["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
