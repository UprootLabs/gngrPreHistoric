/*
    GNU GENERAL PUBLIC LICENSE
    Copyright (C) 2006 The Lobo Project

    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public
    License as published by the Free Software Foundation; either
    verion 2 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    General Public License for more details.

    You should have received a copy of the GNU General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Contact info: lobochief@users.sourceforge.net
 */
package org.lobobrowser.primary.ext;

import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;

import org.lobobrowser.gui.DefaultWindowFactory;
import org.lobobrowser.ua.UserAgentContext;
import org.lobobrowser.util.gui.WrapperLayout;

public class TextViewerWindow extends JFrame {
  private final JTextArea textArea;
  private boolean scrollsOnAppends;

  public TextViewerWindow() {
    super("Lobo Text Viewer");
    final UserAgentContext uaContext = null; // TODO
    this.setIconImage(DefaultWindowFactory.getInstance().getDefaultImageIcon(uaContext).getImage());
    final JMenuBar menuBar = this.createMenuBar();
    this.setJMenuBar(menuBar);
    final Container contentPane = this.getContentPane();
    final JTextArea textArea = createTextArea();
    this.textArea = textArea;
    contentPane.setLayout(WrapperLayout.getInstance());
    contentPane.add(new JScrollPane(textArea));
    this.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosed(final WindowEvent e) {
        final DocumentListener cl = cachedListener;
        if (cl != null) {
          final Document prevDocument = textArea.getDocument();
          if (prevDocument != null) {
            prevDocument.removeDocumentListener(cl);
          }
        }
      }
    });
  }

  public void setText(final String text) {
    this.textArea.setText(text);
  }

  public void setScrollsOnAppends(final boolean flag) {
    this.scrollsOnAppends = flag;
  }

  private DocumentListener cachedListener;

  private DocumentListener getDocumentListener() {
    // Expected in GUI thread.
    DocumentListener cl = this.cachedListener;
    if (cl == null) {
      cl = new LocalDocumentListener();
      this.cachedListener = cl;
    }
    return cl;
  }

  public void setSwingDocument(final javax.swing.text.Document document) {
    final Document prevDocument = this.textArea.getDocument();
    final javax.swing.event.DocumentListener listener = this.getDocumentListener();
    if (prevDocument != null) {
      prevDocument.removeDocumentListener(listener);
    }
    document.addDocumentListener(listener);
    this.textArea.setDocument(document);
  }

  private static JTextArea createTextArea() {
    final JTextArea textArea = new JTextArea();
    textArea.setEditable(false);
    return textArea;
  }

  private JMenuBar createMenuBar() {
    final JMenuBar menuBar = new JMenuBar();
    menuBar.add(this.createFileMenu());
    menuBar.add(this.createEditMenu());
    return menuBar;
  }

  private JMenu createFileMenu() {
    final JMenu fileMenu = new JMenu("File");
    fileMenu.setMnemonic('F');
    fileMenu.add(ComponentSource.menuItem("Close", 'C', new CloseAction()));
    return fileMenu;
  }

  private JMenu createEditMenu() {
    final JMenu fileMenu = new JMenu("Edit");
    fileMenu.setMnemonic('E');
    fileMenu.add(ComponentSource.menuItem("Copy", 'C', "ctrl c", new CopyAction()));
    fileMenu.add(ComponentSource.menuItem("Select All", 'A', new SelectAllAction()));
    return fileMenu;
  }

  private class CloseAction extends javax.swing.AbstractAction {
    public void actionPerformed(final ActionEvent e) {
      TextViewerWindow.this.dispose();
    }
  }

  private class CopyAction extends javax.swing.AbstractAction {
    public void actionPerformed(final ActionEvent e) {
      textArea.copy();
    }
  }

  private class SelectAllAction extends javax.swing.AbstractAction {
    public void actionPerformed(final ActionEvent e) {
      textArea.selectAll();
    }
  }

  private class LocalDocumentListener implements DocumentListener {
    public void changedUpdate(final DocumentEvent e) {
      // nop
    }

    public void insertUpdate(final DocumentEvent e) {
      EventQueue.invokeLater(() -> {
        if (scrollsOnAppends) {
          textArea.scrollRectToVisible(new Rectangle(0, Short.MAX_VALUE, 1, Short.MAX_VALUE));
        }
      });
    }

    public void removeUpdate(final DocumentEvent e) {
      // nop
    }
  }
}
