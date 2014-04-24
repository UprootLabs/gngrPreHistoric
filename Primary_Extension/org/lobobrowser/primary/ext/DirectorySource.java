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

public class DirectorySource {	
	private final ActionPool actionPool;
	
	DirectorySource(ActionPool actionPool) {
		this.actionPool = actionPool;
	}
	
	public JMenu getDirectoryMenu() {
		JMenu searchMenu = new JMenu("Search");
		searchMenu.setMnemonic('S');
		searchMenu.add(this.getGoogleSearchMenu());
		searchMenu.add(this.getYahooSearchMenu());
		
		JMenu newsMenu = new JMenu("News & Blogs");
		newsMenu.setMnemonic('N');
		newsMenu.add(this.getTechNewsMenu());
		newsMenu.add(this.getYahooNewsMenu());
		newsMenu.add(this.getGoogleNewsMenu());
		newsMenu.add(this.getCnnMenu());
		newsMenu.add(this.getDiggMenu());
		
		JMenu infoMenu = new JMenu("Information");
		infoMenu.setMnemonic('I');
		infoMenu.add(this.getWikipediaMenu());
		infoMenu.add(this.getWiktionaryMenu());

		JMenu softwareMenu = new JMenu("Software");
		softwareMenu.setMnemonic('S');
		softwareMenu.add(this.getSourceforgeMenu());
		softwareMenu.add(this.getFreshmeatMenu());
		softwareMenu.add(this.getDownloadComMenu());

		JMenu menu = new JMenu("Directory");
		menu.setMnemonic('D');		
		menu.add(searchMenu);
		menu.add(newsMenu);
		menu.add(infoMenu);
		return menu;
	}
	
	private JMenuItem getGoogleSearchMenu() {
		return ComponentSource.menuItem("Google", this.actionPool.createNavigateAction("http://google.com"));
	}

	private JMenuItem getYahooSearchMenu() {
		return ComponentSource.menuItem("Yahoo!", this.actionPool.createNavigateAction("http://search.yahoo.com"));
	}

	private JMenuItem getYahooNewsMenu() {
		return ComponentSource.menuItem("Yahoo! News", this.actionPool.createNavigateAction("http://news.yahoo.com"));
	}

	private JMenuItem getGoogleNewsMenu() {
		return ComponentSource.menuItem("Google News", this.actionPool.createNavigateAction("http://news.google.com"));
	}
	
	private JMenuItem getCnnMenu() {
		return ComponentSource.menuItem("CNN", this.actionPool.createNavigateAction("http://cnn.com"));
	}
	
	private JMenu getTechNewsMenu() {
		JMenu menu = new JMenu("Tech News");
		menu.add(ComponentSource.menuItem("Slashdot", this.actionPool.createNavigateAction("http://slashdot.org")));
		menu.add(ComponentSource.menuItem("DZone", this.actionPool.createNavigateAction("http://dzone.com")));
		menu.add(ComponentSource.menuItem("Javalobby", this.actionPool.createNavigateAction("http://javalobby.org")));
		return menu;
	}

	private JMenuItem getDiggMenu() {
		return ComponentSource.menuItem("Digg.com", this.actionPool.createNavigateAction("http://digg.com"));		
	}

	private JMenuItem getWikipediaMenu() {
		return ComponentSource.menuItem("Wikipedia", this.actionPool.createNavigateAction("http://wikipedia.org"));		
	}

	private JMenuItem getWiktionaryMenu() {
		return ComponentSource.menuItem("Wiktionary", this.actionPool.createNavigateAction("http://wiktionary.org"));		
	}

	private JMenuItem getSourceforgeMenu() {
		return ComponentSource.menuItem("Wikipedia", this.actionPool.createNavigateAction("http://sourceforge.net"));		
	}

	private JMenuItem getFreshmeatMenu() {
		return ComponentSource.menuItem("Wikipedia", this.actionPool.createNavigateAction("http://freshmeat.net"));		
	}

	private JMenuItem getDownloadComMenu() {
		return ComponentSource.menuItem("Download.com", this.actionPool.createNavigateAction("http://download.com"));		
	}
}
