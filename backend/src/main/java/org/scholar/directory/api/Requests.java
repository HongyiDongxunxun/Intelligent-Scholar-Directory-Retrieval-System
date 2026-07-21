package org.scholar.directory.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
        @NotBlank @Size(max = 100) public String value;
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
        @Size(max = 20)
        public List<String> journalNames = new ArrayList<>();
        @Size(max = 20)
        public List<String> disciplineIds = new ArrayList<>();
    }

    public static class Page {
        @Min(1) @Max(100) public int size = 20;
        @Size(max = 16) @Pattern(regexp = "[0-9]*")
        public String cursor;
    }

    public static class PublicationSearch {
        @Valid @NotEmpty @Size(max = 20) public List<Condition> conditions = new ArrayList<>();
        @Valid public Filters filters = new Filters();
        @Pattern(regexp = "RELEVANCE|CITATIONS|YEAR") public String sort = "RELEVANCE";
        @Valid public Page page = new Page();
    }

    public static class ScholarSearch extends PublicationSearch {
        public boolean strictInstitution = false;
        public boolean includeCoauthors = true;
        @Min(10) @Max(500) public int scanBatchSize = 100;
        @Min(1) @Max(20) public int maxScanRounds = 5;
    }

    public static class TopicAssembly {
        @NotBlank @Size(max = 100) public String topic;
        @Valid @Size(max = 20) public List<Condition> conditions = new ArrayList<>();
        @Valid public Filters filters = new Filters();
        @Min(1) @Max(1000) public int candidateLimit = 500;
        @Min(1) @Max(100) public int topSize = 20;
        @Size(max = 4)
        public List<String> assembleTypes = List.of("PUBLICATION", "SCHOLAR", "INSTITUTION", "KEYWORD");
        public boolean includeSubtopics = true;
        public boolean includeGraph = false;
    }

    public static class AiParse {
        @NotBlank @Size(max = 500) public String request;
        @Size(max = 10) public String locale = "zh-CN";
    }

    public static class ConfirmArticle {
        @Valid public PublicationSearch approvedQuery;
    }

    public static class ConfirmScholar {
        @Valid public ScholarSearch approvedQuery;
    }
}
