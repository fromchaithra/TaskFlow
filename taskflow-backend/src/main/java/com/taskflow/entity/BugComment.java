package com.taskflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "bug_comments")
public class BugComment extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bug_id", nullable = false)
    private Bug bug;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_edited", nullable = false)
    private boolean edited = false;

    protected BugComment() {
    }

    public BugComment(Bug bug, User author, String content) {
        this.bug = bug;
        this.author = author;
        this.content = content;
        this.edited = false;
    }

    public Long getId() {
        return id;
    }

    public Bug getBug() {
        return bug;
    }

    public User getAuthor() {
        return author;
    }

    public String getContent() {
        return content;
    }

    public void editContent(String newContent) {
        this.content = newContent;
        this.edited = true;
    }

    public boolean isEdited() {
        return edited;
    }
}
