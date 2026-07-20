# 核心接口示例

## 文献检索

```http
POST /api/v1/publications/search
Content-Type: application/json

{
  "conditions": [{"operator":"AND","field":"KEYWORD","value":"知识组织","match":"FUZZY"}],
  "filters": {"yearStart":2016,"yearEnd":2025,"journalNames":[],"disciplineIds":[]},
  "sort": "RELEVANCE",
  "page": {"size":20,"cursor":null}
}
```

## 学者发现

```http
POST /api/v1/scholars/search
Content-Type: application/json

{
  "conditions": [{"operator":"AND","field":"KEYWORD","value":"数字人文","match":"FUZZY"}],
  "filters": {"journalNames":[],"disciplineIds":[]},
  "sort": "RELEVANCE",
  "page": {"size":20,"cursor":null},
  "strictInstitution": false,
  "includeCoauthors": true,
  "scanBatchSize": 100,
  "maxScanRounds": 5
}
```

## 专题装配

```http
POST /api/v1/topics/assemble
Content-Type: application/json

{
  "topic": "知识组织",
  "conditions": [{"operator":"AND","field":"KEYWORD","value":"知识组织","match":"FUZZY"}],
  "filters": {"journalNames":[],"disciplineIds":[]},
  "candidateLimit": 500,
  "topSize": 10,
  "assembleTypes": ["PUBLICATION","SCHOLAR","INSTITUTION","KEYWORD"],
  "includeSubtopics": true,
  "includeGraph": true
}
```

## AI 解析与确认

先调用 `POST /api/v1/ai/scholar-search/parse`，检查返回的 `draftQuery`，再将它作为 `approvedQuery` 提交到 `POST /api/v1/ai/scholar-search/confirm`。确认接口不会再次解析或改变条件。
