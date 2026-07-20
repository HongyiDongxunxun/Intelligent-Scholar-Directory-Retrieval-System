package org.scholar.directory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CoreApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void syntheticIndexIsConsistentAndContainsNoOriginalDatabaseDependency() throws Exception {
        mvc.perform(get("/api/v1/system/index-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.consistent").value(true))
                .andExpect(jsonPath("$.data.dataMode").value("SYNTHETIC"))
                .andExpect(jsonPath("$.data.databasePublicationCount").value(60))
                .andExpect(jsonPath("$.data.indexDocumentCount").value(60));
    }

    @Test
    void publicationAndScholarChainsReturnEvidence() throws Exception {
        String publicationQuery = """
                {"conditions":[{"operator":"AND","field":"KEYWORD","value":"知识组织","match":"FUZZY"}],
                 "filters":{"yearStart":2014,"yearEnd":2025,"journalNames":[],"disciplineIds":[]},
                 "sort":"RELEVANCE","page":{"size":20}}
                """;
        mvc.perform(post("/api/v1/publications/search").contentType(MediaType.APPLICATION_JSON).content(publicationQuery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(10))
                .andExpect(jsonPath("$.data.items[0].scholars").isArray())
                .andExpect(jsonPath("$.data.items[0].institutions").isArray());

        String scholarQuery = """
                {"conditions":[{"operator":"AND","field":"KEYWORD","value":"数字人文","match":"FUZZY"}],
                 "filters":{"journalNames":[],"disciplineIds":[]},"sort":"RELEVANCE","page":{"size":20},
                 "strictInstitution":false,"includeCoauthors":true,"scanBatchSize":100,"maxScanRounds":5}
                """;
        mvc.perform(post("/api/v1/scholars/search").contentType(MediaType.APPLICATION_JSON).content(scholarQuery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.items[0].evidencePublications[0].entity.id").exists())
                .andExpect(jsonPath("$.data.scan.complete").value(true));
    }

    @Test
    void topicScoreIsAuditableAndSecondIdenticalRequestHitsCache() throws Exception {
        String body = """
                {"topic":"知识组织","conditions":[{"operator":"AND","field":"KEYWORD","value":"知识组织","match":"FUZZY"}],
                 "filters":{"journalNames":[],"disciplineIds":[]},"candidateLimit":500,"topSize":10,
                 "assembleTypes":["PUBLICATION","SCHOLAR","INSTITUTION","KEYWORD"],
                 "includeSubtopics":true,"includeGraph":true}
                """;
        mvc.perform(post("/api/v1/system/cache/topic/clear")).andExpect(status().isOk());
        String first = mvc.perform(post("/api/v1/topics/assemble").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.cacheStatus").value("MISS"))
                .andReturn().getResponse().getContentAsString();
        JsonNode paper = mapper.readTree(first).at("/data/papers/0");
        double expected = 0.70 * paper.get("rel").asDouble()
                + 0.20 * paper.get("str").asDouble()
                + 0.10 * paper.get("inf").asDouble();
        assertThat(paper.get("score").asDouble()).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.00011));

        mvc.perform(post("/api/v1/topics/assemble").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cacheStatus").value("HIT"))
                .andExpect(jsonPath("$.data.graph.nodes").isArray());
    }

    @Test
    void aiParserRequiresExplicitConfirmationAndSupportsSyntheticAlias() throws Exception {
        String parsed = mvc.perform(post("/api/v1/ai/scholar-search/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"寻找近十年研究数字人文的受限机构甲学者\",\"locale\":\"zh-CN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warnings[0]").value("SYNTHETIC_FIXTURE_ALIAS_APPLIED"))
                .andExpect(jsonPath("$.data.draftQuery.conditions[1].value").value("合成大学01"))
                .andReturn().getResponse().getContentAsString();
        JsonNode draft = mapper.readTree(parsed).at("/data/draftQuery");
        String confirm = mapper.writeValueAsString(java.util.Map.of("approvedQuery", draft));
        mvc.perform(post("/api/v1/ai/scholar-search/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(confirm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }
}
