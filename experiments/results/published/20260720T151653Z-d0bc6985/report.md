# 合成数据实验报告

- 运行编号：`20260720T151653Z-d0bc6985`
- 状态：**PASS**
- 数据版本：`synthetic-fixture-v1`
- 范围：仅验证链路可执行、评分可审计和本地性能可测量。

## E1 专题装配

- 知识组织：候选文献 10；返回 {'papers': 10, 'scholars': 20, 'institutions': 8, 'keywords': 9}；公式校验 True；范围校验 True。
- 数字人文：候选文献 10；返回 {'papers': 10, 'scholars': 20, 'institutions': 8, 'keywords': 9}；公式校验 True；范围校验 True。

## E2 AI 解析与确认

- 三次解析字段一致：True
- 三次确认结果量：[4, 4, 4]
- 三次确认顺序一致：True
- 脱敏别名警告：['SYNTHETIC_FIXTURE_ALIAS_APPLIED']

## E3 性能与缓存

- 冷/热缓存：{'coldStatus': 'MISS', 'coldMs': 18.064, 'warmStatus': 'HIT', 'warmMs': 14.924, 'resultEquivalent': True}

| 场景 | 平均 ms | 观察 P95 ms | RPS | 成功率 |
|---|---:|---:|---:|---:|
| 文献检索 | 17.925 | 28.183 | 1176.923 | 1.0000 |
| 学者检索 | 23.189 | 31.968 | 999.757 | 1.0000 |
| 专题热缓存 | 10.354 | 14.758 | 1300.864 | 1.0000 |

本报告不提供检索质量优越性、真实用户效果或生产性能结论。
