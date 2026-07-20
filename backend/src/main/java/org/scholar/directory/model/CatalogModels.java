package org.scholar.directory.model;

import java.util.List;
import java.util.Map;

public final class CatalogModels {
    private CatalogModels() {}

    public record Institution(String id, String name, String parentId, int level) {}
    public record Scholar(String id, String name, String institutionId, List<String> researchKeywords) {}
    public record Publication(
            String id,
            String title,
            int year,
            String journal,
            String discipline,
            int citations,
            List<String> authorIds,
            List<String> keywords) {}

    public record EntityRef(String type, String id, String label) {}
    public record PublicationHit(
            EntityRef entity,
            String title,
            int year,
            String journal,
            int citationCount,
            List<EntityRef> scholars,
            List<EntityRef> institutions,
            List<EntityRef> keywords) {}
    public record SearchResult(List<PublicationHit> items, long total, String nextCursor) {}
    public record ScholarHit(
            EntityRef entity,
            int matchedPublicationCount,
            int matchedCitationCount,
            double fieldScore,
            List<PublicationHit> evidencePublications,
            Map<String, Double> profileMetrics) {}
    public record ScanStatus(boolean complete, int scannedPublicationCount) {}
    public record ScholarSearchResult(List<ScholarHit> items, long total, String nextCursor, ScanStatus scan) {}
    public record ScoredEntity(EntityRef entity, int rank, double rel, double str, double inf,
                               double score, List<String> evidencePublicationIds) {}
    public record ScoreDefinition(String version, double relWeight, double strWeight, double infWeight) {}
    public record GraphNode(String id, String type, String label, double value) {}
    public record GraphLink(String source, String target, double value) {}
    public record TopicGraph(List<GraphNode> nodes, List<GraphLink> links) {}
    public record TopicResult(
            int candidatePublicationCount,
            List<ScoredEntity> papers,
            List<ScoredEntity> scholars,
            List<ScoredEntity> institutions,
            List<ScoredEntity> keywords,
            List<String> subtopics,
            TopicGraph graph,
            ScoreDefinition scoreDefinition,
            String cacheStatus) {}
}
