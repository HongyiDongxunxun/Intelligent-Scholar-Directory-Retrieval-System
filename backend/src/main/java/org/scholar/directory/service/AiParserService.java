package org.scholar.directory.service;

import org.scholar.directory.api.Requests;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiParserService {
    private final String parserVersion;

    public AiParserService(@Value("${app.ai-parser-version}") String parserVersion) {
        this.parserVersion = parserVersion;
    }

    public Map<String, Object> parseArticle(String input) {
        Requests.PublicationSearch draft = new Requests.PublicationSearch();
        draft.conditions.add(new Requests.Condition(Requests.Operator.AND, Requests.Field.KEYWORD,
                detectTopic(input), Requests.Match.FUZZY));
        if (contains(input, "高被引")) draft.sort = "CITATIONS";
        applyYear(input, draft.filters);
        return draft("article", draft, input);
    }

    public Map<String, Object> parseScholar(String input) {
        Requests.ScholarSearch draft = new Requests.ScholarSearch();
        draft.conditions.add(new Requests.Condition(Requests.Operator.AND, Requests.Field.KEYWORD,
                detectTopic(input), Requests.Match.FUZZY));
        String institution = detectInstitution(input);
        List<String> warnings = new ArrayList<>();
        if (institution != null) {
            if (institution.equals("受限机构甲")) {
                institution = "合成大学01";
                warnings.add("SYNTHETIC_FIXTURE_ALIAS_APPLIED");
            }
            draft.conditions.add(new Requests.Condition(Requests.Operator.AND, Requests.Field.INSTITUTION,
                    institution, Requests.Match.FUZZY));
            draft.strictInstitution = true;
        }
        Map<String, Object> result = draft("scholar", draft, input);
        result.put("warnings", warnings);
        return result;
    }

    private Map<String, Object> draft(String type, Object query, String input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskType", type);
        result.put("rewrittenQuery", input == null ? "" : input.trim());
        result.put("needClarification", input == null || input.isBlank());
        result.put("clarificationQuestions", input == null || input.isBlank()
                ? List.of("请提供需要检索的主题。") : List.of());
        result.put("warnings", new ArrayList<>());
        result.put("parserConfigVersion", parserVersion);
        result.put("draftQuery", query);
        return result;
    }

    private static String detectTopic(String input) {
        if (contains(input, "数字人文")) return "数字人文";
        if (contains(input, "知识组织")) return "知识组织";
        if (contains(input, "学术评价")) return "学术评价";
        if (contains(input, "知识图谱")) return "知识图谱";
        if (contains(input, "公共文化")) return "公共文化";
        if (contains(input, "智能目录")) return "智能目录";
        return input == null || input.isBlank() ? "" : input.trim();
    }

    private static String detectInstitution(String input) {
        if (contains(input, "受限机构甲")) return "受限机构甲";
        if (input != null) {
            var matcher = java.util.regex.Pattern.compile("合成(?:大学|研究院)\\d{2}").matcher(input);
            if (matcher.find()) return matcher.group();
        }
        return null;
    }

    private static void applyYear(String input, Requests.Filters filters) {
        if (contains(input, "近十年")) {
            filters.yearStart = 2016;
            filters.yearEnd = 2025;
        }
    }
    private static boolean contains(String source, String target) { return source != null && source.contains(target); }
}
