package com.example.floatingnotes.util;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A convenience class so you can override only changedUpdate() when needed.
 */
public abstract class SimpleDocListener implements DocumentListener {

    @Override
    public void insertUpdate(DocumentEvent e) {
        changedUpdate(e);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        changedUpdate(e);
    }

    @Override
    public abstract void changedUpdate(DocumentEvent e);
}

