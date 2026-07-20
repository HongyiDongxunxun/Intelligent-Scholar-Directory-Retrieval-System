package org.scholar.directory.api;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

public final class Requests {
    private Requests() {}

    public enum Operator { AND, OR, NOT }
    public enum Field { TITLE, SCHOLAR, KEYWORD, INSTITUTION, DISCIPLINE, JOURNAL }
    public enum Match { EXACT, FUZZY }

    public static class Condition {
        public Operator operator = Operator.AND;
        public Field field = Field.KEYWORD;
        @NotBlank public String value;
        public Match match = Match.FUZZY;

        public Condition() {}
        public Condition(Operator operator, Field field, String value, Match match) {
            this.operator = operator;
            this.field = field;
            this.value = value;
            this.match = match;
        }
    }

    public static class Filters {
        public Integer yearStart;
        public Integer yearEnd;
        public List<String> journalNames = new ArrayList<>();
        public List<String> disciplineIds = new ArrayList<>();
    }

    public static class Page {
        @Min(1) @Max(100) public int size = 20;
        public String cursor;
    }

    public static class PublicationSearch {
        @Valid @NotEmpty public List<Condition> conditions = new ArrayList<>();
        public Filters filters = new Filters();
        public String sort = "RELEVANCE";
        public Page page = new Page();
    }

    public static class ScholarSearch extends PublicationSearch {
        public boolean strictInstitution = false;
        public boolean includeCoauthors = true;
        @Min(10) @Max(500) public int scanBatchSize = 100;
        @Min(1) @Max(20) public int maxScanRounds = 5;
    }

    public static class TopicAssembly {
        @NotBlank public String topic;
        @Valid public List<Condition> conditions = new ArrayList<>();
        public Filters filters = new Filters();
        @Min(1) @Max(1000) public int candidateLimit = 500;
        @Min(1) @Max(100) public int topSize = 20;
        public List<String> assembleTypes = List.of("PUBLICATION", "SCHOLAR", "INSTITUTION", "KEYWORD");
        public boolean includeSubtopics = true;
        public boolean includeGraph = false;
    }

    public static class AiParse {
        @NotBlank public String request;
        public String locale = "zh-CN";
    }

    public static class ConfirmArticle {
        @Valid public PublicationSearch approvedQuery;
    }

    public static class ConfirmScholar {
        @Valid public ScholarSearch approvedQuery;
    }
}
