package service;

import com.example.floatingnotes.model.Note;
import com.example.floatingnotes.service.NoteManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NoteManager save/load/delete
 */
public class NoteManagerTest {

    @TempDir
    Path tmp;

    NoteManager mgr;

    @BeforeEach
    void setup() {
        mgr = new NoteManager(tmp);
    }

    @Test
    void testCreateAndLoad() throws IOException {
        Note n = mgr.createNote("T", "P", "body");
        List<Note> all = mgr.loadAll();
        assertEquals(1, all.size());
        assertEquals("T", all.get(0).getTitle());
    }

    @Test
    void testSaveAndReadById() throws IOException {
        Note n = mgr.createNote("Hello", "Proj", "abc");
        mgr.saveNote(n);
        var read = mgr.readNoteById(n.getId());
        assertTrue(read.isPresent());
        assertEquals("Proj", read.get().getProject());
    }

    @Test
    void testDelete() throws IOException {
        Note n = mgr.createNote("T", "", "b");
        boolean ok = mgr.deleteNote(n);
        assertTrue(ok);
        assertTrue(mgr.loadAll().isEmpty());
    }
}
