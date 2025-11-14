package com.example.floatingnotes.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Very small markdown -> HTML renderer to support:
 * - headers: #, ##, ###
 * - bold: **text**
 * - italic: *text*
 * - unordered lists starting with '-' or '*'
 * - todo items: - [ ] and - [x] rendered to checkboxes with links to toggle (href contains index)
 *
 * Note: renderer produces HTML fragment suitable for JEditorPane (text/html).
 */
public final class MarkdownRenderer {

    private MarkdownRenderer() {}

    /**
     * Convert markdown text to simple HTML fragment.
     *
     * @param md the markdown input
     * @return HTML string
     */
    public static String toHtml(String md) {
        if (md == null) md = "";

        // Escape HTML special characters
        String html = htmlEscape(md);

        // Headers: ###, ##, #
        html = html.replaceAll("(?m)^###\\s*(.+)$", "<h3>$1</h3>");
        html = html.replaceAll("(?m)^##\\s*(.+)$", "<h2>$1</h2>");
        html = html.replaceAll("(?m)^#\\s*(.+)$", "<h1>$1</h1>");

        // TODO items: - [ ] text  or - [x] text -> produce checkbox with link anchor "todo:index:state"
        StringBuilder sb = new StringBuilder();
        String[] lines = html.split("\n", -1);
        int todoIndex = 0;
        boolean inList = false;
        for (String line : lines) {
            // todo pattern
            Matcher mTodo = Pattern.compile("^\\s*[-\\*]\\s*\\[( |x|X)\\]\\s*(.*)$").matcher(line);
            Matcher mList = Pattern.compile("^\\s*[-\\*]\\s+(.*)$").matcher(line);
            if (mTodo.find()) {
                if (!inList) { sb.append("<ul>"); inList = true; }
                String checked = mTodo.group(1).trim().equalsIgnoreCase("x") ? "checked" : "";
                String text = mTodo.group(2);
                // make a link to toggle: href="todo:idx"
                sb.append("<li><a href='todo:").append(todoIndex).append("'><input type='checkbox' ")
                        .append(checked).append(" onclick='return false;'/> ").append(text).append("</a></li>");
                todoIndex++;
            } else if (mList.find()) {
                if (!inList) { sb.append("<ul>"); inList = true; }
                sb.append("<li>").append(mList.group(1)).append("</li>");
            } else {
                if (inList) { sb.append("</ul>"); inList = false; }
                // bold **text**
                line = line.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
                // italic *text*
                line = line.replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "<i>$1</i>");
                // paragraphs
                if (line.trim().isEmpty()) sb.append("<p></p>");
                else sb.append("<p>").append(line).append("</p>");
            }
        }
        if (inList) sb.append("</ul>");

        // wrap with small styling
        return "<html><body style='font-family: sans-serif; font-size: 12px;'>" + sb.toString() + "</body></html>";
    }

    private static String htmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

