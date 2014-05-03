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
 * Created on Oct 8, 2005
 */
package org.lobobrowser.util;

import java.util.*;
import java.lang.ref.*;

public class WeakValueHashMap implements Map {
  private final Map<Object, Object> map = new HashMap<Object, Object>();
  private final ReferenceQueue queue = new ReferenceQueue();

  public WeakValueHashMap() {
    super();
  }

  public int size() {
    return this.map.size();
  }

  public boolean isEmpty() {
    return this.map.isEmpty();
  }

  public boolean containsKey(final Object key) {
    final WeakReference wf = (WeakReference) this.map.get(key);
    return wf != null && wf.get() != null;
  }

  public boolean containsValue(final Object value) {
    throw new UnsupportedOperationException();
  }

  public Object get(final Object key) {
    this.checkQueue();
    final WeakReference wf = (WeakReference) this.map.get(key);
    return wf == null ? null : wf.get();
  }

  public Object put(final Object key, final Object value) {
    this.checkQueue();
    return this.putImpl(key, value);
  }

  private final Object putImpl(final Object key, final Object value) {
    if (value == null) {
      throw new IllegalArgumentException("null values not accepted");
    }
    final Reference ref = new LocalWeakReference(key, value, this.queue);
    final WeakReference oldWf = (WeakReference) this.map.put(key, ref);
    return oldWf == null ? null : oldWf.get();
  }

  public Object remove(final Object key) {
    this.checkQueue();
    final WeakReference wf = (WeakReference) this.map.remove(key);
    return wf == null ? null : wf.get();
  }

  public void putAll(final Map t) {
    this.checkQueue();
    final Iterator i = t.entrySet().iterator();
    while (i.hasNext()) {
      final Map.Entry entry = (Map.Entry) i.next();
      this.putImpl(entry.getKey(), entry.getValue());
    }
  }

  public void clear() {
    this.checkQueue();
    this.map.clear();
  }

  public Set<Object> keySet() {
    return this.map.keySet();
  }

  private final void checkQueue() {
    final ReferenceQueue queue = this.queue;
    LocalWeakReference ref;
    while ((ref = (LocalWeakReference) queue.poll()) != null) {
      this.map.remove(ref.getKey());
    }
  }

  public Collection values() {
    return new FilteredCollection(this.map.values(), new LocalFilter());
  }

  public Set entrySet() {
    throw new UnsupportedOperationException();
  }

  private class LocalFilter implements ObjectFilter {
    /*
     * (non-Javadoc)
     * 
     * @see org.xamjwg.util.ObjectFilter#decode(java.lang.Object)
     */
    public Object decode(final Object source) {
      final WeakReference wf = (WeakReference) source;
      return wf == null ? null : wf.get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.xamjwg.util.ObjectFilter#encode(java.lang.Object)
     */
    public Object encode(final Object source) {
      throw new java.lang.UnsupportedOperationException("Read-only collection.");
    }
  }

  private static class LocalWeakReference extends WeakReference {
    private final Object key;

    public LocalWeakReference(final Object key, final Object target, final ReferenceQueue queue) {
      super(target, queue);
      this.key = key;
    }

    public Object getKey() {
      return key;
    }

    public boolean equals(final Object other) {
      final Object target1 = this.get();
      final Object target2 = other instanceof LocalWeakReference ? ((LocalWeakReference) other).get() : null;
      return Objects.equals(target1, target2);
    }

    public int hashCode() {
      final Object target = this.get();
      return target == null ? 0 : target.hashCode();
    }
  }
}
