package org.scholar.directory.service;

import org.scholar.directory.api.Requests;
import org.scholar.directory.data.FixtureCatalog;
import org.scholar.directory.model.CatalogModels.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {
    private final FixtureCatalog catalog;

    public SearchService(FixtureCatalog catalog) {
        this.catalog = catalog;
    }

    public SearchResult search(Requests.PublicationSearch request) {
        List<Publication> matched = matchingPublications(request.conditions, request.filters);
        Comparator<Publication> comparator = switch (safe(request.sort).toUpperCase(Locale.ROOT)) {
            case "CITATIONS" -> Comparator.comparingInt(Publication::citations).reversed().thenComparing(Publication::id);
            case "YEAR" -> Comparator.comparingInt(Publication::year).reversed().thenComparing(Publication::id);
            default -> Comparator.comparingDouble((Publication p) -> relevance(p, request.conditions)).reversed()
                    .thenComparing(Comparator.comparingInt(Publication::citations).reversed())
                    .thenComparing(Publication::id);
        };
        matched.sort(comparator);
        int offset = decodeCursor(request.page == null ? null : request.page.cursor);
        int size = request.page == null ? 20 : Math.max(1, Math.min(100, request.page.size));
        List<PublicationHit> items = matched.stream().skip(offset).limit(size).map(this::toHit).toList();
        String next = offset + items.size() < matched.size() ? String.valueOf(offset + items.size()) : null;
        return new SearchResult(items, matched.size(), next);
    }

    public ScholarSearchResult searchScholars(Requests.ScholarSearch request) {
        List<Publication> matched = matchingPublications(request.conditions, request.filters);
        int scanLimit = Math.min(matched.size(), request.scanBatchSize * request.maxScanRounds);
        List<Publication> scanned = matched.stream().limit(scanLimit).toList();
        Map<String, List<Publication>> byScholar = new LinkedHashMap<>();
        Set<String> requestedInstitutions = request.conditions.stream()
                .filter(c -> c.field == Requests.Field.INSTITUTION && c.operator != Requests.Operator.NOT)
                .map(c -> normalize(c.value)).collect(Collectors.toSet());

        for (Publication publication : scanned) {
            List<String> authors = request.includeCoauthors
                    ? publication.authorIds()
                    : publication.authorIds().stream().limit(1).toList();
            for (String scholarId : authors) {
                Scholar scholar = catalog.scholars().get(scholarId);
                if (scholar == null || (request.strictInstitution && !requestedInstitutions.isEmpty()
                        && !matchesScholarInstitution(scholar, requestedInstitutions))) continue;
                byScholar.computeIfAbsent(scholarId, ignored -> new ArrayList<>()).add(publication);
            }
        }

        List<ScholarHit> hits = byScholar.entrySet().stream().map(entry -> {
            Scholar scholar = catalog.scholars().get(entry.getKey());
            List<Publication> evidence = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(Publication::citations).reversed())
                    .limit(3).toList();
            int citations = entry.getValue().stream().mapToInt(Publication::citations).sum();
            double score = round(entry.getValue().size() + Math.log1p(citations));
            Map<String, Double> metrics = Map.of(
                    "matchedPublications", (double) entry.getValue().size(),
                    "matchedCitations", (double) citations);
            return new ScholarHit(ref(scholar), entry.getValue().size(), citations, score,
                    evidence.stream().map(this::toHit).toList(), metrics);
        }).sorted(Comparator.comparingDouble(ScholarHit::fieldScore).reversed()
                .thenComparing(hit -> hit.entity().id())).toList();

        int offset = decodeCursor(request.page == null ? null : request.page.cursor);
        int size = request.page == null ? 20 : Math.max(1, Math.min(100, request.page.size));
        List<ScholarHit> page = hits.stream().skip(offset).limit(size).toList();
        String next = offset + page.size() < hits.size() ? String.valueOf(offset + page.size()) : null;
        return new ScholarSearchResult(page, hits.size(), next, new ScanStatus(scanLimit == matched.size(), scanLimit));
    }

    public List<Publication> matchingPublications(List<Requests.Condition> conditions, Requests.Filters filters) {
        List<Requests.Condition> safeConditions = conditions == null ? List.of() : conditions;
        return catalog.publications().values().stream()
                .filter(p -> matchesFilters(p, filters))
                .filter(p -> matchesConditions(p, safeConditions))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private boolean matchesConditions(Publication publication, List<Requests.Condition> conditions) {
        List<Requests.Condition> and = conditions.stream().filter(c -> c.operator == Requests.Operator.AND).toList();
        List<Requests.Condition> or = conditions.stream().filter(c -> c.operator == Requests.Operator.OR).toList();
        List<Requests.Condition> not = conditions.stream().filter(c -> c.operator == Requests.Operator.NOT).toList();
        return and.stream().allMatch(c -> matches(publication, c))
                && (or.isEmpty() || or.stream().anyMatch(c -> matches(publication, c)))
                && not.stream().noneMatch(c -> matches(publication, c));
    }

    private boolean matchesFilters(Publication p, Requests.Filters filters) {
        if (filters == null) return true;
        if (filters.yearStart != null && p.year() < filters.yearStart) return false;
        if (filters.yearEnd != null && p.year() > filters.yearEnd) return false;
        if (filters.journalNames != null && !filters.journalNames.isEmpty()
                && filters.journalNames.stream().noneMatch(j -> contains(p.journal(), j))) return false;
        return filters.disciplineIds == null || filters.disciplineIds.isEmpty()
                || filters.disciplineIds.stream().anyMatch(d -> contains(p.discipline(), d));
    }

    public boolean matches(Publication p, Requests.Condition c) {
        if (c == null || c.value == null || c.value.isBlank()) return false;
        return switch (c.field) {
            case TITLE -> textMatch(p.title(), c.value, c.match);
            case KEYWORD -> p.keywords().stream().anyMatch(v -> textMatch(v, c.value, c.match));
            case JOURNAL -> textMatch(p.journal(), c.value, c.match);
            case DISCIPLINE -> textMatch(p.discipline(), c.value, c.match);
            case SCHOLAR -> p.authorIds().stream().map(catalog.scholars()::get).filter(Objects::nonNull)
                    .anyMatch(s -> textMatch(s.name(), c.value, c.match));
            case INSTITUTION -> p.authorIds().stream().map(catalog.scholars()::get).filter(Objects::nonNull)
                    .anyMatch(s -> institutionLabels(s).stream().anyMatch(v -> textMatch(v, c.value, c.match)));
        };
    }

    public double relevance(Publication p, List<Requests.Condition> conditions) {
        if (conditions == null || conditions.isEmpty()) return 0;
        return conditions.stream().filter(c -> c.operator != Requests.Operator.NOT)
                .mapToDouble(c -> matches(p, c) ? (c.field == Requests.Field.KEYWORD ? 1.2 : 1.0) : 0).sum();
    }

    public PublicationHit toHit(Publication p) {
        List<EntityRef> scholarRefs = p.authorIds().stream().map(catalog.scholars()::get)
                .filter(Objects::nonNull).map(this::ref).toList();
        List<EntityRef> institutions = p.authorIds().stream().map(catalog.scholars()::get)
                .filter(Objects::nonNull).map(Scholar::institutionId).map(catalog.institutions()::get)
                .filter(Objects::nonNull).distinct().map(this::ref).toList();
        List<EntityRef> keywords = p.keywords().stream()
                .map(k -> new EntityRef("KEYWORD", keywordId(k), k)).toList();
        return new PublicationHit(new EntityRef("PUBLICATION", p.id(), p.title()), p.title(), p.year(),
                p.journal(), p.citations(), scholarRefs, institutions, keywords);
    }

    public EntityRef ref(Scholar scholar) { return new EntityRef("SCHOLAR", scholar.id(), scholar.name()); }
    public EntityRef ref(Institution institution) { return new EntityRef("INSTITUTION", institution.id(), institution.name()); }
    public EntityRef ref(Publication publication) { return new EntityRef("PUBLICATION", publication.id(), publication.title()); }
    public String keywordId(String keyword) { return "K" + Integer.toUnsignedString(keyword.hashCode(), 36).toUpperCase(Locale.ROOT); }

    private List<String> institutionLabels(Scholar scholar) {
        Institution institution = catalog.institutions().get(scholar.institutionId());
        if (institution == null) return List.of();
        Institution root = catalog.rootInstitution(institution.id());
        return root == null || root.id().equals(institution.id())
                ? List.of(institution.name()) : List.of(institution.name(), root.name());
    }

    private boolean matchesScholarInstitution(Scholar scholar, Set<String> values) {
        return institutionLabels(scholar).stream().map(SearchService::normalize)
                .anyMatch(label -> values.stream().anyMatch(label::contains));
    }

    private static boolean textMatch(String source, String target, Requests.Match match) {
        String left = normalize(source), right = normalize(target);
        return match == Requests.Match.EXACT ? left.equals(right) : left.contains(right);
    }

    private static boolean contains(String source, String target) { return normalize(source).contains(normalize(target)); }
    private static String normalize(String value) { return safe(value).trim().toLowerCase(Locale.ROOT); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static int decodeCursor(String cursor) {
        try { return Math.max(0, Integer.parseInt(cursor)); } catch (Exception ignored) { return 0; }
    }
    private static double round(double value) { return Math.round(value * 1000d) / 1000d; }
}
