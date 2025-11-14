package com.example.floatingnotes.ui;

import com.example.floatingnotes.model.Note;
import com.example.floatingnotes.service.NoteManager;
import com.example.floatingnotes.util.MarkdownRenderer;
import com.example.floatingnotes.util.SimpleDocListener;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * NotesApp - main application window.
 *
 * Left pane: notes list + project filter + create button
 * Right: nothing — notes open as floating windows (cards) which are independent.
 *
 * Each note window:
 * - has edit textarea (plain markdown)
 * - preview (rendered HTML)
 * - toggle edit/preview
 * - auto-save (debounce)
 * - semi-transparent, always on top, resizable
 */
public class NotesApp extends JFrame {

    private final NoteManager manager;
    private final DefaultListModel<Note> listModel = new DefaultListModel<>();
    private final JList<Note> noteJList = new JList<>(listModel);
    private final JComboBox<String> projectFilter = new JComboBox<>();
    private final ScheduledExecutorService autosaveScheduler = Executors.newSingleThreadScheduledExecutor();

    public NotesApp() {
        super("Floating Notes Panel");
        this.manager = new NoteManager(Path.of("./floating_notes"));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
        initUI();
        loadNotes();
    }

    private void initUI() {
        JSplitPane split = new JSplitPane();
        split.setDividerLocation(280);

        // Left panel
        JPanel left = new JPanel(new BorderLayout(6,6));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton createBtn = new JButton("New Note");
        JButton refreshBtn = new JButton("Refresh");
        top.add(createBtn);
        top.add(refreshBtn);
        left.add(top, BorderLayout.NORTH);

        projectFilter.addItem("All Projects");
        left.add(projectFilter, BorderLayout.SOUTH);

        noteJList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                Note n = (Note) value;
                String text = "<html><b>" + html(n.getTitle()) + "</b><br/><i>" + html(n.getProject()) + "</i></html>";
                return super.getListCellRendererComponent(list, text, index, sel, focus);
            }
        });
        left.add(new JScrollPane(noteJList), BorderLayout.CENTER);

        split.setLeftComponent(left);
        split.setRightComponent(new JPanel()); // notes open separately

        add(split, BorderLayout.CENTER);

        // actions
        createBtn.addActionListener(e -> onCreateNote());
        refreshBtn.addActionListener(e -> loadNotes());
        noteJList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Note n = noteJList.getSelectedValue();
                    if (n != null) openFloatingNoteWindow(n);
                }
            }
        });
        projectFilter.addActionListener(e -> applyProjectFilter());
    }

    private String html(String s) { return s == null ? "" : s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }

    private void onCreateNote() {
        try {
            Note n = manager.createNote("New Note", "", "");
            loadNotes();
            openFloatingNoteWindow(n);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Create failed: " + ex.getMessage());
        }
    }

    private void loadNotes() {
        listModel.clear();
        projectFilter.removeAllItems();
        projectFilter.addItem("All Projects");
        try {
            List<Note> all = manager.loadAll();
            for (Note n : all) {
                listModel.addElement(n);
                if (!n.getProject().isBlank()) projectFilter.addItem(n.getProject());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to load notes: " + e.getMessage());
        }
    }

    private void applyProjectFilter() {
        String sel = (String) projectFilter.getSelectedItem();
        if (sel == null || sel.equals("All Projects")) {
            loadNotes();
            return;
        }
        listModel.clear();
        try {
            for (Note n : manager.loadAll()) {
                if (sel.equals(n.getProject())) listModel.addElement(n);
            }
        } catch (Exception e) { /* ignore */ }
    }

    /** Open a floating note window for a note. */
    private void openFloatingNoteWindow(Note note) {
        JDialog dlg = new JDialog((Window) null);
        dlg.setTitle(note.getTitle());
        dlg.setAlwaysOnTop(true);
        dlg.setModalityType(Dialog.ModalityType.MODELESS);
        dlg.setSize(420, 320);
        dlg.setLocationRelativeTo(null);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        // transparency and decoration
        dlg.setUndecorated(false);
        try {
            // set opacity if supported
            dlg.setOpacity(0.92f);
        } catch (Exception ignored) {}

        // components
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField titleField = new JTextField(note.getTitle(), 20);
        JTextField projectField = new JTextField(note.getProject(), 10);
        JButton saveBtn = new JButton("Save");
        JButton deleteBtn = new JButton("Delete");
        JButton toggleBtn = new JButton("Preview");
        top.add(new JLabel("Title:")); top.add(titleField);
        top.add(new JLabel("Project:")); top.add(projectField);
        top.add(saveBtn); top.add(deleteBtn); top.add(toggleBtn);

        // editor and preview
        JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JTextArea editor = new JTextArea(note.getBody());
        JEditorPane preview = new JEditorPane();
        preview.setEditable(false);
        preview.setContentType("text/html");

        main.setTopComponent(new JScrollPane(editor));
        main.setBottomComponent(new JScrollPane(preview));
        main.setDividerLocation(180);

        // hyperlink handling for todo toggles: href="todo:index"
        preview.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    String desc = e.getDescription();
                    if (desc != null && desc.startsWith("todo:")) {
                        // toggle TODO at index
                        int idx = Integer.parseInt(desc.substring(5));
                        String updated = toggleTodoAtIndex(editor.getText(), idx);
                        editor.setText(updated);
                        scheduleAutoSave(note, titleField, projectField, editor);
                        updatePreview(preview, updated);
                    }
                }
            }
        });

        // live preview: update when editor changes (debounced)
        final ScheduledFuture<?>[] pending = new ScheduledFuture<?>[1];
        final Runnable previewUpdate = () -> SwingUtilities.invokeLater(() -> updatePreview(preview, editor.getText()));
        editor.getDocument().addDocumentListener(new SimpleDocListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                if (pending[0] != null) pending[0].cancel(false);
                pending[0] = autosaveScheduler.schedule(previewUpdate, 300, TimeUnit.MILLISECONDS);
                // also schedule autosave
                scheduleAutoSave(note, titleField, projectField, editor);
            }
        });

        // buttons
        toggleBtn.addActionListener(e -> {
            boolean showPreview = !preview.isShowing();
            preview.setVisible(showPreview);
            main.setDividerLocation(showPreview ? 180 : main.getHeight());
        });

        saveBtn.addActionListener(e -> {
            note.setTitle(titleField.getText());
            note.setProject(projectField.getText());
            note.setBody(editor.getText());
            try { manager.saveNote(note); JOptionPane.showMessageDialog(dlg, "Saved"); } catch (Exception ex) { JOptionPane.showMessageDialog(dlg, "Save failed: "+ex.getMessage()); }
        });

        deleteBtn.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(dlg, "Delete this note?");
            if (ok == JOptionPane.YES_OPTION) {
                try { manager.deleteNote(note); dlg.dispose(); loadNotes(); } catch (Exception ex) { JOptionPane.showMessageDialog(dlg, "Delete failed: "+ex.getMessage()); }
            }
        });

        // initial render
        updatePreview(preview, editor.getText());

        dlg.add(top, BorderLayout.NORTH);
        dlg.add(main, BorderLayout.CENTER);

        // when closing, ensure saved
        dlg.addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e) {
                // cancel scheduled tasks?
            }
        });

        dlg.setVisible(true);
    }

    /** Debounced auto-save: schedules a save 800ms after last change. */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> autosaveMap = new ConcurrentHashMap<>();

    private void scheduleAutoSave(Note note, JTextField titleField, JTextField projectField, JTextArea editor) {
        String key = note.getId();
        ScheduledFuture<?> prev = autosaveMap.get(key);
        if (prev != null) prev.cancel(false);
        Runnable saveTask = () -> {
            note.setTitle(titleField.getText());
            note.setProject(projectField.getText());
            note.setBody(editor.getText());
            try { manager.saveNote(note); SwingUtilities.invokeLater(this::loadNotes); } catch (Exception e) { /* ignore */ }
            autosaveMap.remove(key);
        };
        ScheduledFuture<?> f = autosaveScheduler.schedule(saveTask, 800, TimeUnit.MILLISECONDS);
        autosaveMap.put(key, f);
    }

    /** Toggle the nth todo item in markdown text (0-based). */
    private String toggleTodoAtIndex(String md, int index) {
        String[] lines = md.split("\n", -1);
        int todoCount = 0;
        for (int i = 0; i < lines.length; i++) {
            String ln = lines[i];
            MatcherTodo mt = new MatcherTodo(ln);
            if (mt.isTodo) {
                if (todoCount == index) {
                    // flip checked state
                    String flipped = mt.checked ? mt.prefix + "[ ] " + mt.text : mt.prefix + "[x] " + mt.text;
                    lines[i] = flipped;
                    break;
                }
                todoCount++;
            }
        }
        return String.join("\n", lines);
    }

    // simple helper to detect todo line parts
    private static class MatcherTodo {
        boolean isTodo = false;
        boolean checked = false;
        String prefix = "";
        String text = "";
        MatcherTodo(String line) {
            // match leading whitespace + [-*] + space + [ ] or [x]
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\s*[-\\*]\\s*)\\[( |x|X)\\]\\s*(.*)$").matcher(line);
            if (m.find()) {
                isTodo = true;
                prefix = m.group(1);
                checked = !m.group(2).trim().equalsIgnoreCase(" ");
                text = m.group(3);
            }
        }
    }

    private void updatePreview(JEditorPane preview, String md) {
        String html = MarkdownRenderer.toHtml(md);
        preview.setText(html);
        preview.setCaretPosition(0);
    }

    /** Clean shutdown for concurrency objects. */
    public void shutdown() {
        autosaveScheduler.shutdownNow();
    }

    /** Start the app. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            NotesApp app = new NotesApp();
            app.setVisible(true);
        });
    }
}
