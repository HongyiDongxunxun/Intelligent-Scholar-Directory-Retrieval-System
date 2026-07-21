package org.scholar.directory.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.scholar.directory.api.Requests;
import org.scholar.directory.service.AiParserService;
import org.scholar.directory.service.SearchService;
import org.scholar.directory.service.TopicService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@ConditionalOnProperty(name = "app.legacy-api-enabled", havingValue = "true")
public class LegacyController {
    private final SearchService search;
    private final TopicService topics;
    private final AiParserService ai;
    private final ObjectMapper mapper;

    public LegacyController(SearchService search, TopicService topics, AiParserService ai, ObjectMapper mapper) {
        this.search = search;
        this.topics = topics;
        this.ai = ai;
        this.mapper = mapper;
    }

    @PostMapping("/api/search/searchArticle")
    public Map<String, Object> searchArticle(@RequestBody Map<String, Object> body) {
        return legacy(search.search(fromLegacyArticle(body)));
    }

    @PostMapping("/api/fieldSearch/searchFieldAuthor")
    public Map<String, Object> fieldSearch(@RequestBody Map<String, Object> body) {
        return legacy(search.searchScholars(fromLegacyScholar(body)));
    }

    @PostMapping("/api/topicDiscovery/assemble")
    public Map<String, Object> topic(@RequestBody Map<String, Object> body) {
        Requests.TopicAssembly request = new Requests.TopicAssembly();
        request.topic = limitedString(body.get("topic"), 100, "topic");
        request.candidateLimit = boundedInteger(body.get("candidateLimit"), 500, 1, 1000, "candidateLimit");
        request.topSize = Math.max(
                boundedInteger(body.get("topPaperSize"), 10, 1, 100, "topPaperSize"),
                boundedInteger(body.get("topKeywordSize"), 20, 1, 100, "topKeywordSize"));
        request.includeGraph = bool(body.get("includeGraph"), false);
        request.includeSubtopics = bool(body.get("includeSubtopics"), true);
        return legacy(topics.assemble(request));
    }

    @PostMapping("/api/aiSearch/parseRequest")
    public Map<String, Object> parseArticle(@RequestBody Map<String, Object> body) {
        Map<String, Object> parsed = ai.parseArticle(limitedString(body.get("originRequest"), 500, "originRequest"));
        Requests.PublicationSearch query = (Requests.PublicationSearch) parsed.get("draftQuery");
        Map<String, Object> data = new LinkedHashMap<>(parsed);
        data.put("advancedSearchParam", toLegacy(query));
        return legacy(data);
    }

    @PostMapping("/api/aiSearch/confirmSearch")
    public Map<String, Object> confirmArticle(@RequestBody Map<String, Object> body) {
        return legacy(search.search(fromLegacyArticle(map(body.get("advancedSearchParam")))));
    }

    @PostMapping("/api/aiFieldSearch/parseRequest")
    public Map<String, Object> parseScholar(@RequestBody Map<String, Object> body) {
        Map<String, Object> parsed = ai.parseScholar(limitedString(body.get("originRequest"), 500, "originRequest"));
        Requests.ScholarSearch query = (Requests.ScholarSearch) parsed.get("draftQuery");
        Map<String, Object> data = new LinkedHashMap<>(parsed);
        data.put("fieldAuthorSearchParam", toLegacy(query));
        return legacy(data);
    }

    @PostMapping("/api/aiFieldSearch/confirmSearch")
    public Map<String, Object> confirmScholar(@RequestBody Map<String, Object> body) {
        return legacy(search.searchScholars(fromLegacyScholar(map(body.get("fieldAuthorSearchParam")))));
    }

    private Requests.PublicationSearch fromLegacyArticle(Map<String, Object> body) {
        Requests.PublicationSearch request = new Requests.PublicationSearch();
        request.conditions = conditions(body.get("articleSearchVO"));
        request.sort = switch (string(body.get("sortBy"))) {
            case "cited" -> "CITATIONS";
            case "year" -> "YEAR";
            default -> "RELEVANCE";
        };
        request.page.size = boundedInteger(body.get("pageSize"), 20, 1, 100, "pageSize");
        request.filters.yearStart = nullableInt(body.get("yearStart"));
        request.filters.yearEnd = nullableInt(body.get("yearEnd"));
        return request;
    }

    private Requests.ScholarSearch fromLegacyScholar(Map<String, Object> body) {
        Requests.ScholarSearch request = new Requests.ScholarSearch();
        Requests.PublicationSearch base = fromLegacyArticle(body);
        request.conditions = base.conditions;
        request.filters = base.filters;
        request.page = base.page;
        request.strictInstitution = bool(body.get("strictMode"), false);
        request.includeCoauthors = bool(body.get("expandMode"), true);
        request.scanBatchSize = boundedInteger(body.get("scanBatchSize"), 100, 10, 500, "scanBatchSize");
        request.maxScanRounds = boundedInteger(body.get("maxScanRounds"), 5, 1, 20, "maxScanRounds");
        return request;
    }

    private List<Requests.Condition> conditions(Object raw) {
        if (!(raw instanceof List<?> list)) return new ArrayList<>();
        if (list.size() > 20) throw new IllegalArgumentException("articleSearchVO exceeds 20 conditions");
        List<Requests.Condition> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> row = map(item);
            String value = limitedString(row.get("value"), 100, "condition value");
            if (value.isBlank()) continue;
            int key = integer(row.get("key"), 3);
            Requests.Field field = switch (key) {
                case 1 -> Requests.Field.TITLE;
                case 2 -> Requests.Field.SCHOLAR;
                case 4 -> Requests.Field.INSTITUTION;
                case 5 -> Requests.Field.DISCIPLINE;
                case 6 -> Requests.Field.JOURNAL;
                default -> Requests.Field.KEYWORD;
            };
            Requests.Operator operator = switch (integer(row.get("type"), 1)) {
                case 2 -> Requests.Operator.OR;
                case 3 -> Requests.Operator.NOT;
                default -> Requests.Operator.AND;
            };
            result.add(new Requests.Condition(operator, field, value,
                    bool(row.get("isAccurate"), false) ? Requests.Match.EXACT : Requests.Match.FUZZY));
        }
        return result;
    }

    private Map<String, Object> toLegacy(Requests.PublicationSearch query) {
        List<Map<String, Object>> rows = query.conditions.stream().map(c -> Map.<String, Object>of(
                "type", c.operator == Requests.Operator.AND ? 1 : c.operator == Requests.Operator.OR ? 2 : 3,
                "key", switch (c.field) { case TITLE -> 1; case SCHOLAR -> 2; case KEYWORD -> 3; case INSTITUTION -> 4; case DISCIPLINE -> 5; case JOURNAL -> 6; },
                "value", c.value,
                "isAccurate", c.match == Requests.Match.EXACT)).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pageIndex", 1);
        result.put("pageSize", query.page.size);
        result.put("articleSearchVO", rows);
        result.put("yearStart", query.filters.yearStart);
        result.put("yearEnd", query.filters.yearEnd);
        if (query instanceof Requests.ScholarSearch scholar) {
            result.put("strictMode", scholar.strictInstitution);
            result.put("expandMode", scholar.includeCoauthors);
        }
        return result;
    }

    private static Map<String, Object> legacy(Object data) {
        return Map.of("code", "2000", "msg", "请求成功", "data", data);
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> map) return mapper.convertValue(map, Map.class);
        return new LinkedHashMap<>();
    }
    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private static int integer(Object value, int fallback) { try { return Integer.parseInt(string(value)); } catch (Exception ignored) { return fallback; } }
    private static int boundedInteger(Object value, int fallback, int min, int max, String field) {
        int result = integer(value, fallback);
        if (result < min || result > max) throw new IllegalArgumentException(field + " is out of range");
        return result;
    }
    private static String limitedString(Object value, int maxLength, String field) {
        String result = string(value);
        if (result.length() > maxLength) throw new IllegalArgumentException(field + " is too long");
        return result;
    }
    private static Integer nullableInt(Object value) { String v = string(value); return v.isBlank() ? null : integer(v, 0); }
    private static boolean bool(Object value, boolean fallback) { return value == null ? fallback : Boolean.parseBoolean(string(value)); }
}
