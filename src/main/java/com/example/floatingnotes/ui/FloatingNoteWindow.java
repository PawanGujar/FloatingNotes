package com.example.floatingnotes.ui;

import com.example.floatingnotes.model.Note;
import com.example.floatingnotes.service.NoteManager;
import com.example.floatingnotes.util.MarkdownRenderer;
import com.example.floatingnotes.util.SimpleDocListener;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FloatingNoteWindow - a single floating/resizable note window (card).
 *
 * <p>
 * Features:
 * - Always-on-top, resizable, lightly transparent (if supported)
 * - Edit area (markdown) with live preview (HTML via MarkdownRenderer)
 * - Debounced autosave (800ms after last edit)
 * - Toggle TODO checkboxes in preview (clickable links)
 * - Save/Delete buttons, Title and Project fields
 * </p>
 *
 * Usage:
 * <pre>
 *   FloatingNoteWindow w = new FloatingNoteWindow(note, noteManager);
 *   w.showWindow();
 * </pre>
 *
 * @author Pawan Gujar
 * @version 1.0
 */
public class FloatingNoteWindow {

    private final Note note;
    private final NoteManager manager;

    private final JDialog dialog;
    private final JTextField titleField;
    private final JTextField projectField;
    private final JTextArea editor;
    private final JEditorPane preview;

    // autosave debounce
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "note-autosave");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> pendingSave;

    private static final long AUTOSAVE_DELAY_MS = 800;

    /**
     * Construct a floating window for a note.
     *
     * @param note    the Note model instance (will be mutated on save)
     * @param manager the NoteManager used to persist changes
     */
    public FloatingNoteWindow(Note note, NoteManager manager) {
        this.note = note;
        this.manager = manager;

        dialog = new JDialog((Window) null);
        dialog.setTitle(note.getTitle().isEmpty() ? "Untitled" : note.getTitle());
        dialog.setModalityType(Dialog.ModalityType.MODELESS);
        dialog.setAlwaysOnTop(true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setSize(480, 360);
        dialog.setLocationRelativeTo(null);
        dialog.setLayout(new BorderLayout());

        // Top bar: title, project, Save, Delete, Preview toggle
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        titleField = new JTextField(note.getTitle(), 18);
        projectField = new JTextField(note.getProject(), 10);
        JButton saveBtn = new JButton("Save");
        JButton deleteBtn = new JButton("Delete");
        JButton togglePreviewBtn = new JButton("Toggle Preview");

        top.add(new JLabel("Title:"));
        top.add(titleField);
        top.add(new JLabel("Project:"));
        top.add(projectField);
        top.add(saveBtn);
        top.add(deleteBtn);
        top.add(togglePreviewBtn);

        dialog.add(top, BorderLayout.NORTH);

        // Editor & Preview split pane
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        editor = new JTextArea(note.getBody());
        editor.setLineWrap(true);
        editor.setWrapStyleWord(true);
        preview = new JEditorPane();
        preview.setEditable(false);
        preview.setContentType("text/html");

        JScrollPane editScroll = new JScrollPane(editor);
        JScrollPane previewScroll = new JScrollPane(preview);

        split.setTopComponent(editScroll);
        split.setBottomComponent(previewScroll);
        split.setDividerLocation(180);
        dialog.add(split, BorderLayout.CENTER);

        // Try set window opacity (best effort)
        try {
            dialog.setOpacity(0.94f);
        } catch (UnsupportedOperationException | SecurityException ignored) { }

        // Wire actions
        saveBtn.addActionListener(e -> saveNow());
        deleteBtn.addActionListener(e -> onDelete());
        togglePreviewBtn.addActionListener(e -> togglePreview(split));

        // preview hyperlink (for todo toggles)
        preview.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                String desc = e.getDescription();
                if (desc != null && desc.startsWith("todo:")) {
                    try {
                        int idx = Integer.parseInt(desc.substring(5));
                        String updated = toggleTodoAtIndex(editor.getText(), idx);
                        editor.setText(updated);
                        scheduleAutoSave(); // schedule save & preview update
                        updatePreviewAsync();
                    } catch (Exception ex) {
                        // ignore parse errors
                    }
                }
            }
        });

        // editor changes -> debounce preview update + autosave
        editor.getDocument().addDocumentListener(new SimpleDocListener() {
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                schedulePreviewUpdate();
                scheduleAutoSave();
            }
        });

        // title/project changes -> schedule save
        titleField.getDocument().addDocumentListener(new SimpleDocListener() {
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { scheduleAutoSave(); dialog.setTitle(titleField.getText()); }
        });
        projectField.getDocument().addDocumentListener(new SimpleDocListener() {
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { scheduleAutoSave(); }
        });

        // ensure preview is initially rendered
        updatePreviewAsync();

        // On dialog close, shutdown scheduler and ensure saved
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                shutdownAndSave();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                shutdownAndSave();
            }
        });

        // make window resizable, draggable by decorations (default)
        dialog.setResizable(true);
    }

    /**
     * Show the floating note window.
     */
    public void showWindow() {
        dialog.setVisible(true);
    }

    /**
     * Hide/close the window programmatically.
     */
    public void closeWindow() {
        dialog.dispose();
    }

    /** Toggle preview visibility by adjusting split divider. */
    private void togglePreview(JSplitPane split) {
        boolean previewVisible = preview.isShowing();
        preview.setVisible(!previewVisible);
        split.setDividerLocation(previewVisible ? split.getHeight() : 180);
    }

    /** Immediately save note to disk (synchronous). */
    private synchronized void saveNow() {
        note.setTitle(titleField.getText());
        note.setProject(projectField.getText());
        note.setBody(editor.getText());
        try {
            manager.saveNote(note);
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(dialog, "Save failed: " + ex.getMessage()));
        }
    }

    /** Schedule an autosave after debounce period. */
    private synchronized void scheduleAutoSave() {
        if (pendingSave != null && !pendingSave.isDone()) {
            pendingSave.cancel(false);
        }
        pendingSave = scheduler.schedule(this::saveNow, AUTOSAVE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /** Schedule preview update (debounced) */
    private ScheduledFuture<?> pendingPreview;
    private synchronized void schedulePreviewUpdate() {
        if (pendingPreview != null && !pendingPreview.isDone()) pendingPreview.cancel(false);
        pendingPreview = scheduler.schedule(this::updatePreviewAsync, 250, TimeUnit.MILLISECONDS);
    }

    /** Update preview on EDT */
    private void updatePreviewAsync() {
        String md = editor.getText();
        String html = MarkdownRenderer.toHtml(md);
        SwingUtilities.invokeLater(() -> {
            preview.setText(html);
            preview.setCaretPosition(0);
        });
    }

    /** Delete note (asks for confirmation). */
    private void onDelete() {
        int ok = JOptionPane.showConfirmDialog(dialog, "Delete this note?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            try {
                manager.deleteNote(note);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(dialog, "Delete failed: " + e.getMessage());
            } finally {
                closeWindow();
            }
        }
    }

    /** Toggle nth TODO item in markdown (0-based) */
    private String toggleTodoAtIndex(String md, int index) {
        String[] lines = md.split("\n", -1);
        int todoCount = 0;
        Pattern p = Pattern.compile("^(\\s*[-\\*]\\s*)\\[( |x|X)\\]\\s*(.*)$");
        for (int i = 0; i < lines.length; i++) {
            Matcher m = p.matcher(lines[i]);
            if (m.matches()) {
                if (todoCount == index) {
                    String prefix = m.group(1);
                    String state = m.group(2);
                    String text = m.group(3);
                    boolean checked = !state.trim().equalsIgnoreCase(" ");
                    String newLine = prefix + (checked ? "[ ] " + text : "[x] " + text);
                    lines[i] = newLine;
                    break;
                }
                todoCount++;
            }
        }
        return String.join("\n", lines);
    }

    /** Ensure pending saves complete, stop scheduler. */
    private synchronized void shutdownAndSave() {
        try {
            if (pendingSave != null && !pendingSave.isDone()) {
                pendingSave.cancel(false);
            }
            // do a final save synchronously
            saveNow();
        } finally {
            try { scheduler.shutdownNow(); } catch (Exception ignored) {}
        }
    }
}
