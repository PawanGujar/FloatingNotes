package com.example.floatingnotes.service;

import com.example.floatingnotes.model.Note;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages notes: create, list, load, save, delete.
 *
 * Notes are stored under a folder "floating_notes" adjacent to working dir.
 * Each note file is named {id}.note with a small header:
 *
 */
public class NoteManager {

    private final Path baseDir;

    /** Create a manager using default base dir "./floating_notes". */
    public NoteManager() {
        this(Paths.get("./floating_notes"));
    }

    public NoteManager(Path baseDir) {
        this.baseDir = baseDir;
        try {
            if (!Files.exists(baseDir)) Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create base dir", e);
        }
    }

    /** Create and save a new note. */
    public synchronized Note createNote(String title, String project, String body) throws IOException {
        Note n = new Note(title, project, body);
        saveNote(n);
        return n;
    }

    /** Save / overwrite note to disk. */
    public synchronized void saveNote(Note note) throws IOException {
        Path p = baseDir.resolve(note.getId() + ".note");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            pw.println("Title: " + note.getTitle());
            pw.println("Project: " + note.getProject());
            pw.println("LastModified: " + note.getLastModified().getEpochSecond());
            pw.println();
            pw.println(note.getBody() == null ? "" : note.getBody());
        }
    }

    /** Load a single note file. */
    private Note loadNoteFile(Path p) throws IOException {
        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        String title = "";
        String project = "";
        Instant lm = Instant.now();
        int i = 0;
        for (; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) { i++; break; }
            if (line.startsWith("Title: ")) title = line.substring(7);
            else if (line.startsWith("Project: ")) project = line.substring(9);
            else if (line.startsWith("LastModified: ")) {
                try { lm = Instant.ofEpochSecond(Long.parseLong(line.substring(14).trim())); } catch (Exception ignored) {}
            }
        }
        String body = lines.stream().skip(i).collect(Collectors.joining("\n"));
        String id = p.getFileName().toString();
        if (id.endsWith(".note")) id = id.substring(0, id.length()-5);
        return new Note(id, title, project, body, lm);
    }

    /** Load all notes. */
    public synchronized List<Note> loadAll() throws IOException {
        if (!Files.exists(baseDir)) return Collections.emptyList();
        try {
            return Files.list(baseDir)
                    .filter(f -> f.getFileName().toString().endsWith(".note"))
                    .map(p -> {
                        try { return loadNoteFile(p); } catch (IOException e) { return null; }
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Note::getLastModified).reversed())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw e;
        }
    }

    /** Delete a note by id. */
    public synchronized boolean deleteNote(Note note) throws IOException {
        Path p = baseDir.resolve(note.getId() + ".note");
        return Files.deleteIfExists(p);
    }

    /** Read a note by id (if exists). */
    public synchronized Optional<Note> readNoteById(String id) throws IOException {
        Path p = baseDir.resolve(id + ".note");
        if (!Files.exists(p)) return Optional.empty();
        return Optional.of(loadNoteFile(p));
    }

    /** For testing convenience: clear all notes. */
    public synchronized void clearAll() throws IOException {
        if (!Files.exists(baseDir)) return;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(baseDir, "*.note")) {
            for (Path p : ds) Files.deleteIfExists(p);
        }
    }
}

