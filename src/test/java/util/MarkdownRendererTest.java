package util;

import com.example.floatingnotes.util.MarkdownRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MarkdownRenderer.
 */
public class MarkdownRendererTest {

    @Test
    void testHeaderAndBoldItalic() {
        String md = "# Title\nThis is **bold** and *italic*.";
        String html = MarkdownRenderer.toHtml(md);
        assertTrue(html.contains("<h1>Title</h1>"));
        assertTrue(html.contains("<b>bold</b>"));
        assertTrue(html.contains("<i>italic</i>"));
    }

    @Test
    void testTodoRendering() {
        String md = "- [ ] task one\n- [x] done";
        String html = MarkdownRenderer.toHtml(md);
        assertTrue(html.contains("todo:0"));
        assertTrue(html.contains("todo:1"));
        assertTrue(html.contains("input type='checkbox'"));
    }
}
