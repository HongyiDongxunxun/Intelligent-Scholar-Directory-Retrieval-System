package org.scholar.directory.web;

import org.scholar.directory.api.ApiResponse;
import org.scholar.directory.api.Requests;
import org.scholar.directory.data.FixtureCatalog;
import org.scholar.directory.model.CatalogModels.*;
import org.scholar.directory.service.AiParserService;
import org.scholar.directory.service.SearchService;
import org.scholar.directory.service.TopicService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class CoreController {
    private final SearchService search;
    private final TopicService topics;
    private final AiParserService ai;
    private final FixtureCatalog catalog;
    private final String dataVersion;
    private final String algorithmVersion;

    public CoreController(SearchService search, TopicService topics, AiParserService ai, FixtureCatalog catalog,
                          @Value("${app.data-version}") String dataVersion,
                          @Value("${app.algorithm-version}") String algorithmVersion) {
        this.search = search;
        this.topics = topics;
        this.ai = ai;
        this.catalog = catalog;
        this.dataVersion = dataVersion;
        this.algorithmVersion = algorithmVersion;
    }

    @PostMapping("/publications/search")
    public ApiResponse<SearchResult> searchPublications(@Valid @RequestBody Requests.PublicationSearch request) {
        return ApiResponse.ok(search.search(request), meta());
    }

    @PostMapping("/scholars/search")
    public ApiResponse<ScholarSearchResult> searchScholars(@Valid @RequestBody Requests.ScholarSearch request) {
        return ApiResponse.ok(search.searchScholars(request), meta());
    }

    @PostMapping("/topics/assemble")
    public ApiResponse<TopicResult> assembleTopic(@Valid @RequestBody Requests.TopicAssembly request) {
        return ApiResponse.ok(topics.assemble(request), meta());
    }

    @PostMapping("/ai/article-search/parse")
    public ApiResponse<Map<String, Object>> parseArticle(@Valid @RequestBody Requests.AiParse request) {
        return ApiResponse.ok(ai.parseArticle(request.request), meta());
    }

    @PostMapping("/ai/article-search/confirm")
    public ApiResponse<SearchResult> confirmArticle(@Valid @RequestBody Requests.ConfirmArticle request) {
        if (request.approvedQuery == null) throw new IllegalArgumentException("approvedQuery is required");
        return ApiResponse.ok(search.search(request.approvedQuery), meta());
    }

    @PostMapping("/ai/scholar-search/parse")
    public ApiResponse<Map<String, Object>> parseScholar(@Valid @RequestBody Requests.AiParse request) {
        return ApiResponse.ok(ai.parseScholar(request.request), meta());
    }

    @PostMapping("/ai/scholar-search/confirm")
    public ApiResponse<ScholarSearchResult> confirmScholar(@Valid @RequestBody Requests.ConfirmScholar request) {
        if (request.approvedQuery == null) throw new IllegalArgumentException("approvedQuery is required");
        return ApiResponse.ok(search.searchScholars(request.approvedQuery), meta());
    }

    @GetMapping("/entities/{type}/{id}")
    public ApiResponse<Map<String, Object>> entity(@PathVariable String type, @PathVariable String id) {
        String normalized = type.toUpperCase(Locale.ROOT);
        Map<String, Object> result = new LinkedHashMap<>();
        switch (normalized) {
            case "PUBLICATION" -> {
                Publication p = require(catalog.publications().get(id));
                result.put("entity", search.ref(p));
                result.put("attributes", p);
                result.put("relations", search.toHit(p));
            }
            case "SCHOLAR" -> {
                Scholar s = require(catalog.scholars().get(id));
                result.put("entity", search.ref(s));
                result.put("attributes", s);
                result.put("relations", catalog.publications().values().stream()
                        .filter(p -> p.authorIds().contains(id)).map(search::toHit).toList());
            }
            case "INSTITUTION" -> {
                Institution u = require(catalog.institutions().get(id));
                result.put("entity", search.ref(u));
                result.put("attributes", u);
                result.put("relations", catalog.scholars().values().stream()
                        .filter(s -> s.institutionId().equals(id) || Objects.equals(catalog.rootInstitution(s.institutionId()).id(), id))
                        .map(search::ref).toList());
            }
            case "KEYWORD" -> {
                List<Publication> publications = catalog.publications().values().stream()
                        .filter(p -> p.keywords().stream().anyMatch(k -> search.keywordId(k).equals(id))).toList();
                if (publications.isEmpty()) throw new NoSuchElementException("entity not found");
                String label = publications.stream().flatMap(p -> p.keywords().stream())
                        .filter(k -> search.keywordId(k).equals(id)).findFirst().orElse(id);
                result.put("entity", new EntityRef("KEYWORD", id, label));
                result.put("attributes", Map.of("publicationCount", publications.size()));
                result.put("relations", publications.stream().map(search::toHit).toList());
            }
            default -> throw new IllegalArgumentException("unsupported entity type");
        }
        result.put("actions", List.of(Map.of("action", "APPEND_CONDITION", "resetCursor", true)));
        return ApiResponse.ok(result, meta());
    }

    @GetMapping("/system/index-status")
    public ApiResponse<Map<String, Object>> indexStatus() {
        int count = catalog.publications().size();
        return ApiResponse.ok(Map.of(
                "consistent", true,
                "databasePublicationCount", count,
                "indexDocumentCount", count,
                "indexVersion", "fixture-index-v1",
                "dataMode", "SYNTHETIC"), meta());
    }

    @PostMapping("/system/cache/topic/clear")
    public ApiResponse<Map<String, Object>> clearTopicCache() {
        return ApiResponse.ok(Map.of("clearedEntries", topics.clearCache()), meta());
    }

    private Map<String, Object> meta() {
        return Map.of("dataVersion", dataVersion, "indexVersion", "fixture-index-v1",
                "algorithmVersion", algorithmVersion);
    }
    private static <T> T require(T value) {
        if (value == null) throw new NoSuchElementException("entity not found");
        return value;
    }
}
