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
 * Created on Sep 3, 2005
 */
package org.lobobrowser.util;

import java.util.*;

// TOOD: remove if unused
public class ListSet implements List, Set {
  private final List<Object> list = new ArrayList<Object>();
  private final Set<Object> set = new HashSet<Object>();

  public ListSet() {
    super();
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#add(int, E)
   */
  public void add(final int index, final Object element) {
    if (this.set.add(element)) {
      list.add(index, element);
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#add(E)
   */
  public boolean add(final Object o) {
    if (this.set.add(o)) {
      return this.list.add(o);
    } else {
      return false;
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#addAll(java.util.Collection)
   */
  public boolean addAll(final Collection c) {
    boolean changed = false;
    final Iterator i = c.iterator();
    while (i.hasNext()) {
      final Object element = i.next();
      if (this.add(element)) {
        changed = true;
      }
    }
    return changed;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#addAll(int, java.util.Collection)
   */
  public boolean addAll(final int index, final Collection c) {
    boolean changed = false;
    int insertIndex = index;
    final Iterator i = c.iterator();
    while (i.hasNext()) {
      final Object element = i.next();
      if (this.set.add(element)) {
        this.list.add(insertIndex++, element);
        changed = true;
      }
    }
    return changed;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#clear()
   */
  public void clear() {
    this.set.clear();
    this.list.clear();
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#contains(java.lang.Object)
   */
  public boolean contains(final Object o) {
    return this.set.contains(o);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#containsAll(java.util.Collection)
   */
  public boolean containsAll(final Collection c) {
    return this.set.containsAll(c);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#get(int)
   */
  public Object get(final int index) {
    return this.list.get(index);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#indexOf(java.lang.Object)
   */
  public int indexOf(final Object o) {
    return this.list.indexOf(o);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#isEmpty()
   */
  public boolean isEmpty() {
    return this.set.isEmpty();
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#iterator()
   */
  public Iterator<Object> iterator() {
    return this.list.iterator();
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#lastIndexOf(java.lang.Object)
   */
  public int lastIndexOf(final Object o) {
    return this.list.lastIndexOf(o);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#listIterator()
   */
  public ListIterator<Object> listIterator() {
    return this.list.listIterator();
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#listIterator(int)
   */
  public ListIterator<Object> listIterator(final int index) {
    return this.list.listIterator(index);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#remove(int)
   */
  public Object remove(final int index) {
    final Object element = this.list.remove(index);
    if (element != null) {
      this.set.remove(element);
    }
    return element;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#remove(java.lang.Object)
   */
  public boolean remove(final Object o) {
    if (this.set.remove(o)) {
      this.list.remove(o);
      return true;
    } else {
      return false;
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#removeAll(java.util.Collection)
   */
  public boolean removeAll(final Collection c) {
    if (this.set.removeAll(c)) {
      this.list.removeAll(c);
      return true;
    } else {
      return false;
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#retainAll(java.util.Collection)
   */
  public boolean retainAll(final Collection c) {
    if (this.set.retainAll(c)) {
      this.list.retainAll(c);
      return true;
    } else {
      return false;
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#set(int, E)
   */
  public Object set(final int index, final Object element) {
    this.set.add(element);
    return this.list.set(index, element);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#size()
   */
  public int size() {
    return this.list.size();
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#subList(int, int)
   */
  public List<Object> subList(final int fromIndex, final int toIndex) {
    return this.list.subList(fromIndex, toIndex);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#toArray()
   */
  public Object[] toArray() {
    return this.list.toArray();
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.List#toArray(T[])
   */
  public Object[] toArray(final Object[] a) {
    return this.list.toArray(a);
  }

  public boolean equals(final Object other) {
    return other instanceof ListSet && this.list.equals(((ListSet) other).list);
  }

  public int hashCode() {
    return this.list.hashCode();
  }
}
