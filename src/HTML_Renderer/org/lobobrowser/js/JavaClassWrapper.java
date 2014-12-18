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
package org.lobobrowser.js;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.lobobrowser.html.js.NotGetterSetter;
import org.lobobrowser.html.js.PropertyName;
import org.mozilla.javascript.Function;

public class JavaClassWrapper {
  private final Class<?> javaClass;
  private final Map<String, JavaFunctionObject> functions = new HashMap<>();
  private final Map<String, PropertyInfo> properties = new HashMap<>();
  private PropertyInfo nameIndexer;
  private PropertyInfo integerIndexer;

  public JavaClassWrapper(final Class<?> class1) {
    super();
    this.javaClass = class1;
    this.scanMethods();
  }

  public Object newInstance() throws InstantiationException, IllegalAccessException {
    return this.javaClass.newInstance();
  }

  public String getClassName() {
    final String className = this.javaClass.getName();
    final int lastDotIdx = className.lastIndexOf('.');
    return lastDotIdx == -1 ? className : className.substring(lastDotIdx + 1);
  }

  public Function getFunction(final String name) {
    return this.functions.get(name);
  }

  public PropertyInfo getProperty(final String name) {
    return this.properties.get(name);
  }

  private void scanMethods() {
    final Method[] methods = this.javaClass.getMethods();
    final int len = methods.length;
    for (int i = 0; i < len; i++) {
      final Method method = methods[i];
      final String name = method.getName();
      if (isPropertyMethod(name, method)) {
        this.ensurePropertyKnown(name, method);
      } else {
        if (isNameIndexer(name, method)) {
          this.updateNameIndexer(name, method);
        } else if (isIntegerIndexer(name, method)) {
          this.updateIntegerIndexer(name, method);
        }
        JavaFunctionObject f = this.functions.get(name);
        if (f == null) {
          f = new JavaFunctionObject(name);
          this.functions.put(name, f);
        }
        f.addMethod(method);
      }
    }
  }

  private static boolean isNameIndexer(final String name, final Method method) {
    return ("namedItem".equals(name) && (method.getParameterTypes().length == 1))
        || ("setNamedItem".equals(name) && (method.getParameterTypes().length == 2));
  }

  private static boolean isIntegerIndexer(final String name, final Method method) {
    return ("item".equals(name) && (method.getParameterTypes().length == 1))
        || ("setItem".equals(name) && (method.getParameterTypes().length == 2));
  }

  private void updateNameIndexer(final String methodName, final Method method) {
    boolean getter = true;
    if (methodName.startsWith("set")) {
      getter = false;
    }
    PropertyInfo indexer = this.nameIndexer;
    if (indexer == null) {
      indexer = new PropertyInfo("$item", Object.class);
      this.nameIndexer = indexer;
    }
    if (getter) {
      indexer.setGetter(method);
    } else {
      indexer.setSetter(method);
    }
  }

  private void updateIntegerIndexer(final String methodName, final Method method) {
    boolean getter = true;
    if (methodName.startsWith("set")) {
      getter = false;
    }
    PropertyInfo indexer = this.integerIndexer;
    if (indexer == null) {
      final Class<?> pt = getter ? method.getReturnType() : method.getParameterTypes()[1];
      indexer = new PropertyInfo("$item", pt);
      this.integerIndexer = indexer;
    }
    if (getter) {
      indexer.setGetter(method);
    } else {
      indexer.setSetter(method);
    }
  }

  public PropertyInfo getIntegerIndexer() {
    return this.integerIndexer;
  }

  public PropertyInfo getNameIndexer() {
    return this.nameIndexer;
  }

  private static boolean isPropertyMethod(final String name, final Method method) {
    if (method.isAnnotationPresent(NotGetterSetter.class)) {
      return false;
    } else {
      if (name.startsWith("get") || name.startsWith("is")) {
        return method.getParameterTypes().length == 0;
      } else if (name.startsWith("set")) {
        return method.getParameterTypes().length == 1;
      } else {
        return false;
      }
    }
  }

  private static String propertyUncapitalize(final String text) {
    try {
      if ((text.length() > 1) && Character.isUpperCase(text.charAt(1))) {
        // If second letter is capitalized, don't uncapitalize,
        // e.g. getURL.
        return text;
      }
      return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    } catch (final IndexOutOfBoundsException iob) {
      return text;
    }
  }

  private void ensurePropertyKnown(final String methodName, final Method method) {
    String capPropertyName;
    boolean getter = false;
    if (methodName.startsWith("get")) {
      capPropertyName = methodName.substring(3);
      getter = true;
    } else if (methodName.startsWith("set")) {
      capPropertyName = methodName.substring(3);
    } else if (methodName.startsWith("is")) {
      capPropertyName = methodName.substring(2);
      getter = true;
    } else {
      throw new IllegalArgumentException("methodName=" + methodName);
    }

    final PropertyName propertyNameAnnotation = method.getAnnotation(PropertyName.class);
    final String propertyName = (propertyNameAnnotation != null) ? propertyNameAnnotation.value() : propertyUncapitalize(capPropertyName);

    PropertyInfo pinfo = this.properties.get(propertyName);
    if (pinfo == null) {
      final Class<?> pt = getter ? method.getReturnType() : method.getParameterTypes()[0];
      pinfo = new PropertyInfo(propertyName, pt);
      this.properties.put(propertyName, pinfo);
    }
    if (getter) {
      pinfo.setGetter(method);
    } else {
      pinfo.setSetter(method);
    }
  }

  @Override
  public String toString() {
    return this.javaClass.getName();
  }
}
