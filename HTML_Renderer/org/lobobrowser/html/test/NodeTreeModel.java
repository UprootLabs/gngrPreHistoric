/*
    GNU LESSER GENERAL PUBLIC LICENSE
    Copyright (C) 2006 The Lobo Project

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Contact info: lobochief@users.sourceforge.net
 */
/*
 * Created on Oct 22, 2005
 */
package org.lobobrowser.html.test;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.*;

import org.w3c.dom.*;

class NodeTreeModel implements TreeModel {
  private final Node rootNode;

  /**
   * @param node
   */
  public NodeTreeModel(final Node node) {
    super();
    rootNode = node;
  }

  public Object getRoot() {
    return this.rootNode;
  }

  public Object getChild(final Object parent, final int index) {
    final Node parentNode = (Node) parent;
    return parentNode == null ? null : parentNode.getChildNodes().item(index);
  }

  public int getChildCount(final Object parent) {
    final Node parentNode = (Node) parent;
    return parentNode == null ? 0 : parentNode.getChildNodes().getLength();
  }

  public boolean isLeaf(final Object node) {
    if (node == this.rootNode) {
      return false;
    }
    final Node domNode = (Node) node;
    return domNode == null ? true : domNode.getChildNodes().getLength() == 0;
  }

  public void valueForPathChanged(final TreePath path, final Object newValue) {
  }

  public int getIndexOfChild(final Object parent, final Object child) {
    final Node parentNode = (Node) parent;
    final NodeList nodeList = parentNode == null ? null : parentNode.getChildNodes();
    if (nodeList == null) {
      return -1;
    }
    final int length = nodeList.getLength();
    for (int i = 0; i < length; i++) {
      if (nodeList.item(i) == child) {
        return i;
      }
    }
    return -1;
  }

  public void addTreeModelListener(final TreeModelListener l) {
    // nop
  }

  public void removeTreeModelListener(final TreeModelListener l) {
    // nop
  }
}
