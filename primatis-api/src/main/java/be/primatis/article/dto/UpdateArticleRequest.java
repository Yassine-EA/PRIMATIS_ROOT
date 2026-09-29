package be.primatis.article.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Requête de modification partielle d'un article.
 *
 * Chaque champ peut être fourni indépendamment afin de modifier uniquement
 * les informations souhaitées.
 */
public class UpdateArticleRequest {

    private String title;
    private boolean titlePresent;

    private String content;
    private boolean contentPresent;

    private String summary;
    private boolean summaryPresent;

    public UpdateArticleRequest() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.titlePresent = true;
    }

    @JsonIgnore
    public boolean isTitlePresent() {
        return titlePresent;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.contentPresent = true;
    }

    @JsonIgnore
    public boolean isContentPresent() {
        return contentPresent;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
        this.summaryPresent = true;
    }

    @JsonIgnore
    public boolean isSummaryPresent() {
        return summaryPresent;
    }
}
