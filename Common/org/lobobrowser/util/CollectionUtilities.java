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
 * Created on Jun 9, 2005
 */
package org.lobobrowser.util;

import java.util.*;

/**
 * @author J. H. S.
 */
public class CollectionUtilities {
  /**
	 * 
	 */
  private CollectionUtilities() {
    super();
  }

  public static Enumeration getIteratorEnumeration(final Iterator i) {
    return new Enumeration() {
      public boolean hasMoreElements() {
        return i.hasNext();
      }

      public Object nextElement() {
        return i.next();
      }
    };
  }

  public static Iterator<Object> iteratorUnion(final Iterator[] iterators) {
    return new Iterator() {
      private int iteratorIndex = 0;
      private Iterator<Object> current = iterators.length > 0 ? iterators[0] : null;

      public boolean hasNext() {
        for (;;) {
          if (current == null) {
            return false;
          }
          if (current.hasNext()) {
            return true;
          }
          iteratorIndex++;
          current = iteratorIndex >= iterators.length ? null : iterators[iteratorIndex];
        }
      }

      public Object next() {
        for (;;) {
          if (this.current == null) {
            throw new NoSuchElementException();
          }
          try {
            return this.current.next();
          } catch (final NoSuchElementException nse) {
            this.iteratorIndex++;
            this.current = this.iteratorIndex >= iterators.length ? null : iterators[this.iteratorIndex];
          }
        }
      }

      public void remove() {
        if (this.current == null) {
          throw new NoSuchElementException();
        }
        this.current.remove();
      }
    };
  }

  public static Collection reverse(final Collection collection) {
    final LinkedList newCollection = new LinkedList();
    final Iterator i = collection.iterator();
    while (i.hasNext()) {
      newCollection.addFirst(i.next());
    }
    return newCollection;
  }

  public static <T> Iterator<T> singletonIterator(final T item) {
    return new Iterator<T>() {
      private boolean gotItem = false;

      public boolean hasNext() {
        return !this.gotItem;
      }

      public T next() {
        if (this.gotItem) {
          throw new NoSuchElementException();
        }
        this.gotItem = true;
        return item;
      }

      public void remove() {
        if (!this.gotItem) {
          this.gotItem = true;
        } else {
          throw new NoSuchElementException();
        }
      }
    };
  }
}
