package org.lobobrowser.util;

import java.util.*;

public class Items {
  private Items() {
  }

  private static Map<Object, Map> sourceMap = new WeakHashMap<Object, Map>();

  public static Object getItem(Object source, String name) {
    Map<Object, Map> sm = sourceMap;
    synchronized (sm) {
      Map itemMap = sm.get(source);
      if (itemMap == null) {
        return null;
      }
      return itemMap.get(name);
    }
  }

  public static void setItem(Object source, String name, Object value) {
    Map<Object, Map> sm = sourceMap;
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
