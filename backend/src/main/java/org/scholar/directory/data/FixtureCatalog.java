package org.scholar.directory.data;

import org.scholar.directory.model.CatalogModels.Institution;
import org.scholar.directory.model.CatalogModels.Publication;
import org.scholar.directory.model.CatalogModels.Scholar;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FixtureCatalog {
    private final Map<String, Institution> institutions = new LinkedHashMap<>();
    private final Map<String, Scholar> scholars = new LinkedHashMap<>();
    private final Map<String, Publication> publications = new LinkedHashMap<>();

    public FixtureCatalog() {
        seedInstitutions();
        seedScholars();
        seedPublications();
    }

    private void seedInstitutions() {
        for (int i = 1; i <= 8; i++) {
            String id = "U%02d".formatted(i);
            institutions.put(id, new Institution(id, "合成大学%02d".formatted(i), null, 1));
        }
        for (int i = 1; i <= 12; i++) {
            String id = "D%02d".formatted(i);
            String parent = "U%02d".formatted(((i - 1) % 8) + 1);
            institutions.put(id, new Institution(id, "合成研究院%02d".formatted(i), parent, 2));
        }
    }

    private void seedScholars() {
        String[][] interests = {
                {"知识组织", "数字图书馆", "信息检索"},
                {"数字人文", "文化计算", "文本分析"},
                {"学术评价", "科学计量", "知识图谱"},
                {"公共文化", "社会治理", "数字记忆"}
        };
        for (int i = 1; i <= 40; i++) {
            String id = "A%03d".formatted(i);
            String institutionId = i <= 24
                    ? "D%02d".formatted(((i - 1) % 12) + 1)
                    : "U%02d".formatted(((i - 1) % 8) + 1);
            scholars.put(id, new Scholar(id, "合成学者%03d".formatted(i), institutionId,
                    List.of(interests[(i - 1) % interests.length])));
        }
    }

    private void seedPublications() {
        String[][] topics = {
                {"知识组织", "主题标引", "信息检索", "知识表示"},
                {"数字人文", "文化计算", "文本分析", "数字记忆"},
                {"学术评价", "科学计量", "引文分析", "指标解释"},
                {"知识图谱", "实体关联", "语义检索", "证据组织"},
                {"公共文化", "文化服务", "社会治理", "用户研究"},
                {"智能目录", "学者发现", "探索式检索", "交互设计"}
        };
        String[] journals = {"合成人文研究", "合成信息学刊", "合成社会科学", "合成数字文化"};
        String[] disciplines = {"图书情报", "历史文化", "社会学", "管理学"};
        for (int i = 1; i <= 60; i++) {
            String id = "P%03d".formatted(i);
            String[] topic = topics[(i - 1) % topics.length];
            List<String> authorIds = new ArrayList<>();
            int authorCount = 2 + (i % 2);
            for (int j = 0; j < authorCount; j++) {
                authorIds.add("A%03d".formatted(((i * 3 + j * 7) % 40) + 1));
            }
            List<String> keywords = List.of(topic[0], topic[1], topic[2], topic[3], "方法%02d".formatted(i % 10));
            publications.put(id, new Publication(
                    id,
                    "合成论文%03d：%s的%s研究".formatted(i, topic[0], topic[1]),
                    2014 + (i % 12),
                    journals[(i - 1) % journals.length],
                    disciplines[(i - 1) % disciplines.length],
                    (i * 17 + i * i) % 240,
                    authorIds,
                    keywords));
        }
    }

    public Map<String, Institution> institutions() { return institutions; }
    public Map<String, Scholar> scholars() { return scholars; }
    public Map<String, Publication> publications() { return publications; }

    public Institution rootInstitution(String institutionId) {
        Institution current = institutions.get(institutionId);
        if (current == null || current.parentId() == null) return current;
        return institutions.getOrDefault(current.parentId(), current);
    }
}
