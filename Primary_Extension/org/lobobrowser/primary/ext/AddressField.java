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

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.util.*;
import java.awt.event.*;

public class AddressField extends JComboBox {
	private final ComponentSource componentSource;
	
	public AddressField(ComponentSource cs) {
		this.componentSource = cs;
		this.setEditable(true);
		TextFieldComboBoxEditor editor = new TextFieldComboBoxEditor();
		this.setEditor(editor);
		editor.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				onKeyReleased(e);
			}

			@Override
			public void keyPressed(KeyEvent e) {
				onKeyPressed(e);
			}			
		});
		this.addPopupMenuListener(new PopupMenuListener() {
        	public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        		onBeforePopupVisible();
        	}
        	
        	public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        	}
        	
        	public void popupMenuCanceled(PopupMenuEvent e) {
        	}
        });
		this.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				String cmd = event.getActionCommand();
				if("comboBoxEdited".equals(cmd)) {
					onEdited(event.getModifiers());
				}
				else if("comboBoxChanged".equals(cmd)) {
				}
			}
		});	
		// This needed the first time to set a reasonable
		// popup size.
		this.onBeforePopupVisible();
	}

    public String getText() {
    	if(this.isEditable()) {
    		return (String) this.getEditor().getItem();
    	}
    	else {
    		return String.valueOf(this.getSelectedItem());
    	}
    }    

    public void setText(String text) {
    	JComboBox combo = this;
    	boolean editable = this.isEditable();
    	if(editable) {
    		combo.getEditor().setItem(text);
    	}
    }

    public void setUrl(java.net.URL url) {
    	this.setText(url == null ? "" : url.toExternalForm());
    }
    
	private void onBeforePopupVisible() {
		if((comboInvalid || comboHasHeadMatches) && !populatingMatches) {
			populateCombo(this.getText());
		}		
	}
	
	private boolean comboInvalid = true;
	private boolean comboHasHeadMatches = false;
	private boolean populatingMatches = false;
	
	private void populateCombo(String comboBoxText) {
		// Expected to be called in GUI thread.
		this.populatingMatches = true;
		try {
			JComboBox urlComboBox = this;
			urlComboBox.removeAllItems();
			Collection<String> recentUrls = this.componentSource.getRecentLocations(30);
			Iterator i = recentUrls.iterator();
			while(i.hasNext()) {
				String matchUrl = (String) i.next();
				urlComboBox.addItem(matchUrl);
			}						
			this.setText(comboBoxText);
			this.comboHasHeadMatches = false;
			this.comboInvalid = false;
		} finally {
			this.populatingMatches = false;
		}
	}

	private void onEdited(int modifiers) {
//		if(this.getText().length() != 0) {
//			this.componentSource.navigateOrSearch();
//		}
	}
	
	private void onKeyReleased(KeyEvent event) {
		AddressField urlComboBox = this;
		char releasedChar = event.getKeyChar();
		if(validPopupChar(releasedChar)) {
			String urlText = urlComboBox.getText();
			Collection headMatches = this.componentSource.getPotentialMatches(urlText, 30);
			if(headMatches.size() == 0) {
				if(urlComboBox.isPopupVisible()) {
					urlComboBox.hidePopup();
				}
			}
			else {
				populatingMatches = true;
				try {
					urlComboBox.removeAllItems();
					Iterator i = headMatches.iterator();
					while(i.hasNext()) {
						String matchUrl = (String) i.next();
						urlComboBox.addItem(matchUrl);
					}
					comboHasHeadMatches = true;
					if(!urlComboBox.isPopupVisible()) {
						urlComboBox.showPopup();
					}
					urlComboBox.setSelectedItem(null);
					urlComboBox.setText(urlText);					
				} finally {
					populatingMatches = false;
				}
			}
		}
		
	}
	
	private void onKeyPressed(KeyEvent event) {
		AddressField urlComboBox = this;
		if(event.getKeyCode() == KeyEvent.VK_ENTER) {
			String urlText = urlComboBox.getText();
			if(urlText.length() != 0) {
				this.componentSource.navigateOrSearch();
			}
		}		
	}

	private boolean validPopupChar(char ch) {
		return Character.isLetterOrDigit(ch) || ch == '.' || ch == '/';
	}
}
