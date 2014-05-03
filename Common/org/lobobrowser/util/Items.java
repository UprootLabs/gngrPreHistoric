package org.lobobrowser.util;

import java.util.*;

public class Items {
  private Items() {
  }

  private static Map<Object, Map> sourceMap = new WeakHashMap<Object, Map>();

  public static Object getItem(final Object source, final String name) {
    final Map<Object, Map> sm = sourceMap;
    synchronized (sm) {
      final Map itemMap = sm.get(source);
      if (itemMap == null) {
        return null;
      }
      return itemMap.get(name);
    }
  }

  public static void setItem(final Object source, final String name, final Object value) {
    final Map<Object, Map> sm = sourceMap;
    synchronized (sm) {
      Map<String, Object> itemMap = sm.get(source);
      if (itemMap == null) {
        itemMap = new HashMap<String, Object>(1);
        sm.put(source, itemMap);
      }
      itemMap.put(name, value);
    }
  }
}
