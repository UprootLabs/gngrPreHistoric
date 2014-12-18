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

import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

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

  public static <T> Enumeration<T> getIteratorEnumeration(final Iterator<T> i) {
    return new Enumeration<T>() {
      public boolean hasMoreElements() {
        return i.hasNext();
      }

      public T nextElement() {
        return i.next();
      }
    };
  }

  public static <T> Enumeration<T> getEmptyEnumeration() {
    return new Enumeration<T>() {
      public boolean hasMoreElements() {
        return false;
      }

      public T nextElement() {
        throw new NoSuchElementException("Trying to get element of an empty enumeration");
      }
    };
  }

  public static <T> Iterator<T> iteratorUnion(final Iterator<T>[] iterators) {
    return new Iterator<T>() {
      private int iteratorIndex = 0;
      private Iterator<T> current = iterators.length > 0 ? iterators[0] : null;

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

      public T next() {
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

  public static <T> Collection<T> reverse(final Collection<T> collection) {
    final LinkedList<T> newCollection = new LinkedList<>();
    final Iterator<T> i = collection.iterator();
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
