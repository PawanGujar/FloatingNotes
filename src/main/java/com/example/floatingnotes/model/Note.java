package com.example.floatingnotes.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single Note.
 *
 * Stored on disk as a small text file (simple header + body).
 */
public class Note {
    private final String id;
    private String title;
    private String project;
    private String body;
    private Instant lastModified;

    /**
     * Create a new Note with a generated id and timestamp.
     *
     * @param title   the note title
     * @param project the project/group name
     * @param body    markdown body
     */
    public Note(String title, String project, String body) {
        this.id = UUID.randomUUID().toString();
        this.title = title == null ? "" : title;
        this.project = project == null ? "" : project;
        this.body = body == null ? "" : body;
        this.lastModified = Instant.now();
    }

    /**
     * Create Note with explicit id (used when loading).
     */
    public Note(String id, String title, String project, String body, Instant lastModified) {
        this.id = id;
        this.title = title;
        this.project = project;
        this.body = body;
        this.lastModified = lastModified == null ? Instant.now() : lastModified;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getProject() { return project; }
    public String getBody() { return body; }
    public Instant getLastModified() { return lastModified; }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
        touch();
    }

    public void setProject(String project) {
        this.project = project == null ? "" : project;
        touch();
    }

    public void setBody(String body) {
        this.body = body == null ? "" : body;
        touch();
    }

    private void touch() { this.lastModified = Instant.now(); }
}
