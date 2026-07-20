package org.scholar.directory.service;

import org.scholar.directory.api.Requests;
import org.scholar.directory.data.FixtureCatalog;
import org.scholar.directory.model.CatalogModels.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;

@Service
public class TopicService {
    private static final double REL_WEIGHT = 0.70;
    private static final double STR_WEIGHT = 0.20;
    private static final double INF_WEIGHT = 0.10;
    private static final long CACHE_TTL_SECONDS = 1800;

    private final FixtureCatalog catalog;
    private final SearchService searchService;
    private final String algorithmVersion;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public TopicService(FixtureCatalog catalog, SearchService searchService,
                        @Value("${app.algorithm-version}") String algorithmVersion) {
        this.catalog = catalog;
        this.searchService = searchService;
        this.algorithmVersion = algorithmVersion;
    }

    public TopicResult assemble(Requests.TopicAssembly request) {
        String key = cacheKey(request);
        CacheEntry old = cache.get(key);
        if (old != null && old.expiresAt().isAfter(Instant.now())) return withCache(old.value(), "HIT");

        List<Requests.Condition> conditions = new ArrayList<>(request.conditions == null ? List.of() : request.conditions);
        conditions.add(new Requests.Condition(Requests.Operator.OR, Requests.Field.TITLE, request.topic, Requests.Match.FUZZY));
        conditions.add(new Requests.Condition(Requests.Operator.OR, Requests.Field.KEYWORD, request.topic, Requests.Match.FUZZY));
        List<Publication> candidates = searchService.matchingPublications(conditions, request.filters).stream()
                .sorted(Comparator.comparingDouble((Publication p) -> searchService.relevance(p, conditions)).reversed()
                        .thenComparing(Comparator.comparingInt(Publication::citations).reversed()))
                .limit(Math.max(1, Math.min(1000, request.candidateLimit))).toList();

        Map<String, Double> rel = normalized(candidates, p -> searchService.relevance(p, conditions));
        List<ScoreBucket> paperBuckets = new ArrayList<>();
        for (Publication p : candidates) {
            ScoreBucket b = new ScoreBucket(searchService.ref(p));
            b.relRaw = rel.getOrDefault(p.id(), 0d);
            b.strRaw = Math.log1p(p.authorIds().size()) + Math.log1p(p.keywords().size());
            b.infRaw = Math.log1p(p.citations());
            b.evidence.add(p.id());
            paperBuckets.add(b);
        }
        finalizeScores(paperBuckets, true);

        Map<String, ScoreBucket> scholarMap = new LinkedHashMap<>();
        Map<String, Set<String>> scholarPapers = new HashMap<>();
        Map<String, Set<String>> scholarKeywords = new HashMap<>();
        Map<String, Set<String>> scholarCoauthors = new HashMap<>();
        Map<String, ScoreBucket> institutionMap = new LinkedHashMap<>();
        Map<String, Set<String>> institutionPapers = new HashMap<>();
        Map<String, Set<String>> institutionScholars = new HashMap<>();
        Map<String, Set<String>> institutionKeywords = new HashMap<>();
        Map<String, ScoreBucket> keywordMap = new LinkedHashMap<>();
        Map<String, Set<String>> keywordPapers = new HashMap<>();
        Map<String, Set<String>> keywordNeighbors = new HashMap<>();

        for (Publication p : candidates) {
            double paperRel = rel.getOrDefault(p.id(), 0d);
            double citedLog = Math.log1p(p.citations());
            for (int i = 0; i < p.authorIds().size(); i++) {
                String scholarId = p.authorIds().get(i);
                Scholar scholar = catalog.scholars().get(scholarId);
                if (scholar == null) continue;
                double authorWeight = i == 0 ? 1.0 : i == 1 ? 0.8 : i == 2 ? 0.6 : 0.5;
                ScoreBucket b = scholarMap.computeIfAbsent(scholarId, ignored -> new ScoreBucket(searchService.ref(scholar)));
                b.relRaw += paperRel * authorWeight;
                b.infRaw += paperRel * citedLog * authorWeight;
                b.evidence.add(p.id());
                scholarPapers.computeIfAbsent(scholarId, ignored -> new LinkedHashSet<>()).add(p.id());
                scholarKeywords.computeIfAbsent(scholarId, ignored -> new LinkedHashSet<>()).addAll(p.keywords());
                Set<String> coauthors = scholarCoauthors.computeIfAbsent(scholarId, ignored -> new LinkedHashSet<>());
                coauthors.addAll(p.authorIds());
                coauthors.remove(scholarId);

                Institution institution = catalog.rootInstitution(scholar.institutionId());
                if (institution != null) {
                    ScoreBucket unit = institutionMap.computeIfAbsent(institution.id(), ignored -> new ScoreBucket(searchService.ref(institution)));
                    unit.relRaw += paperRel * authorWeight;
                    unit.infRaw += paperRel * citedLog * authorWeight;
                    unit.evidence.add(p.id());
                    institutionPapers.computeIfAbsent(institution.id(), ignored -> new LinkedHashSet<>()).add(p.id());
                    institutionScholars.computeIfAbsent(institution.id(), ignored -> new LinkedHashSet<>()).add(scholarId);
                    institutionKeywords.computeIfAbsent(institution.id(), ignored -> new LinkedHashSet<>()).addAll(p.keywords());
                }
            }
            for (String keyword : p.keywords()) {
                String keywordId = searchService.keywordId(keyword);
                ScoreBucket b = keywordMap.computeIfAbsent(keywordId,
                        ignored -> new ScoreBucket(new EntityRef("KEYWORD", keywordId, keyword)));
                b.relRaw += paperRel;
                b.infRaw += paperRel * citedLog;
                b.evidence.add(p.id());
                keywordPapers.computeIfAbsent(keywordId, ignored -> new LinkedHashSet<>()).add(p.id());
                Set<String> neighbors = keywordNeighbors.computeIfAbsent(keywordId, ignored -> new LinkedHashSet<>());
                for (String other : p.keywords()) if (!other.equals(keyword)) neighbors.add(searchService.keywordId(other));
            }
        }

        scholarMap.forEach((id, b) -> b.strRaw = 0.5 * size(scholarPapers, id)
                + 0.3 * size(scholarCoauthors, id) + 0.2 * size(scholarKeywords, id));
        institutionMap.forEach((id, b) -> b.strRaw = 0.5 * size(institutionPapers, id)
                + 0.3 * size(institutionScholars, id) + 0.2 * size(institutionKeywords, id));
        keywordMap.forEach((id, b) -> b.strRaw = 0.5 * size(keywordPapers, id) + 0.5 * size(keywordNeighbors, id));
        finalizeScores(new ArrayList<>(scholarMap.values()), false);
        finalizeScores(new ArrayList<>(institutionMap.values()), false);
        finalizeScores(new ArrayList<>(keywordMap.values()), false);

        int topSize = Math.max(1, Math.min(100, request.topSize));
        List<ScoredEntity> papers = toScored(paperBuckets, topSize);
        List<ScoredEntity> scholars = toScored(new ArrayList<>(scholarMap.values()), topSize);
        List<ScoredEntity> institutions = toScored(new ArrayList<>(institutionMap.values()), topSize);
        List<ScoredEntity> keywords = toScored(new ArrayList<>(keywordMap.values()), topSize);
        List<String> subtopics = request.includeSubtopics
                ? keywords.stream().limit(8).map(item -> item.entity().label()).toList() : List.of();
        TopicGraph graph = request.includeGraph ? buildGraph(papers, scholars, institutions, keywords) : null;
        TopicResult result = new TopicResult(candidates.size(), papers, scholars, institutions, keywords,
                subtopics, graph, new ScoreDefinition(algorithmVersion, REL_WEIGHT, STR_WEIGHT, INF_WEIGHT), "MISS");
        cache.put(key, new CacheEntry(Instant.now().plusSeconds(CACHE_TTL_SECONDS), result));
        return result;
    }

    public int clearCache() {
        int entries = cache.size();
        cache.clear();
        return entries;
    }

    private void finalizeScores(List<ScoreBucket> buckets, boolean relAlreadyNormalized) {
        if (!relAlreadyNormalized) normalizeInPlace(buckets, b -> b.relRaw, (b, v) -> b.rel = v);
        else buckets.forEach(b -> b.rel = b.relRaw);
        normalizeInPlace(buckets, b -> b.strRaw, (b, v) -> b.str = v);
        normalizeInPlace(buckets, b -> b.infRaw, (b, v) -> b.inf = v);
        buckets.forEach(b -> b.score = REL_WEIGHT * b.rel + STR_WEIGHT * b.str + INF_WEIGHT * b.inf);
    }

    private List<ScoredEntity> toScored(List<ScoreBucket> buckets, int limit) {
        buckets.sort(Comparator.comparingDouble((ScoreBucket b) -> b.score).reversed().thenComparing(b -> b.entity.id()));
        List<ScoredEntity> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, buckets.size()); i++) {
            ScoreBucket b = buckets.get(i);
            result.add(new ScoredEntity(b.entity, i + 1, round(b.rel), round(b.str), round(b.inf), round(b.score),
                    b.evidence.stream().limit(3).toList()));
        }
        return result;
    }

    private TopicGraph buildGraph(List<ScoredEntity> papers, List<ScoredEntity> scholars,
                                  List<ScoredEntity> institutions, List<ScoredEntity> keywords) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphLink> links = new ArrayList<>();
        List<ScoredEntity> selectedPapers = papers.stream().limit(6).toList();
        selectedPapers.forEach(p -> nodes.add(new GraphNode(p.entity().id(), "PUBLICATION", p.entity().label(), p.score())));
        keywords.stream().limit(5).forEach(k -> nodes.add(new GraphNode(k.entity().id(), "KEYWORD", k.entity().label(), k.score())));
        scholars.stream().limit(5).forEach(s -> nodes.add(new GraphNode(s.entity().id(), "SCHOLAR", s.entity().label(), s.score())));
        institutions.stream().limit(4).forEach(u -> nodes.add(new GraphNode(u.entity().id(), "INSTITUTION", u.entity().label(), u.score())));
        for (ScoredEntity keyword : keywords.stream().limit(5).toList()) {
            for (ScoredEntity paper : selectedPapers) {
                Publication source = catalog.publications().get(paper.entity().id());
                if (source != null && source.keywords().contains(keyword.entity().label()))
                    links.add(new GraphLink(keyword.entity().id(), paper.entity().id(), 1));
            }
        }
        for (ScoredEntity paper : selectedPapers) {
            Publication source = catalog.publications().get(paper.entity().id());
            if (source == null) continue;
            for (String authorId : source.authorIds()) if (scholars.stream().anyMatch(s -> s.entity().id().equals(authorId)))
                links.add(new GraphLink(paper.entity().id(), authorId, 1));
        }
        return new TopicGraph(nodes, links);
    }

    private Map<String, Double> normalized(List<Publication> items, ToDoubleFunction<Publication> getter) {
        double min = items.stream().mapToDouble(getter).min().orElse(0);
        double max = items.stream().mapToDouble(getter).max().orElse(0);
        Map<String, Double> result = new HashMap<>();
        for (Publication item : items) result.put(item.id(), normalize(getter.applyAsDouble(item), min, max));
        return result;
    }

    private void normalizeInPlace(List<ScoreBucket> items, ToDoubleFunction<ScoreBucket> getter, ScoreSetter setter) {
        double min = items.stream().mapToDouble(getter).min().orElse(0);
        double max = items.stream().mapToDouble(getter).max().orElse(0);
        for (ScoreBucket item : items) setter.set(item, normalize(getter.applyAsDouble(item), min, max));
    }

    private static double normalize(double value, double min, double max) {
        if (max <= min) return max > 0 ? 1 : 0;
        return (value - min) / (max - min);
    }
    private static int size(Map<String, ? extends Set<String>> map, String id) {
        Set<String> values = map.get(id);
        return values == null ? 0 : values.size();
    }
    private static double round(double value) { return Math.round(value * 10000d) / 10000d; }
    private static String cacheKey(Requests.TopicAssembly r) {
        String conditions = r.conditions.stream()
                .map(c -> String.join(":", c.operator.name(), c.field.name(), c.match.name(), c.value.trim()))
                .collect(java.util.stream.Collectors.joining(","));
        return String.join("|", String.valueOf(r.topic), conditions, String.valueOf(r.filters.yearStart),
                String.valueOf(r.filters.yearEnd), String.valueOf(r.filters.journalNames), String.valueOf(r.candidateLimit),
                String.valueOf(r.topSize), String.valueOf(r.assembleTypes), String.valueOf(r.includeSubtopics), String.valueOf(r.includeGraph));
    }
    private static TopicResult withCache(TopicResult r, String status) {
        return new TopicResult(r.candidatePublicationCount(), r.papers(), r.scholars(), r.institutions(), r.keywords(),
                r.subtopics(), r.graph(), r.scoreDefinition(), status);
    }

    private static class ScoreBucket {
        final EntityRef entity;
        final LinkedHashSet<String> evidence = new LinkedHashSet<>();
        double relRaw, strRaw, infRaw, rel, str, inf, score;
        ScoreBucket(EntityRef entity) { this.entity = entity; }
    }
    private interface ScoreSetter { void set(ScoreBucket bucket, double value); }
    private record CacheEntry(Instant expiresAt, TopicResult value) {}
}
