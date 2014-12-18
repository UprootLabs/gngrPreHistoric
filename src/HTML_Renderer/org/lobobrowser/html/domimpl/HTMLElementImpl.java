/*    GNU LESSER GENERAL PUBLIC LICENSE
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
package org.lobobrowser.html.domimpl;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;

import org.lobobrowser.html.FormInput;
import org.lobobrowser.html.parser.HtmlParser;
import org.lobobrowser.html.style.CSS2PropertiesContext;
import org.lobobrowser.html.style.CSSNorm;
import org.lobobrowser.html.style.CSSUtilities;
import org.lobobrowser.html.style.ComputedJStyleProperties;
import org.lobobrowser.html.style.JStyleProperties;
import org.lobobrowser.html.style.LocalJStyleProperties;
import org.lobobrowser.html.style.RenderState;
import org.lobobrowser.html.style.StyleElements;
import org.lobobrowser.html.style.StyleSheetRenderState;
import org.lobobrowser.util.Strings;
import org.w3c.css.sac.InputSource;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.html.HTMLElement;
import org.w3c.dom.html.HTMLFormElement;

import cz.vutbr.web.css.CSSException;
import cz.vutbr.web.css.CSSFactory;
import cz.vutbr.web.css.NodeData;
import cz.vutbr.web.css.Selector;
import cz.vutbr.web.css.Selector.PseudoDeclaration;
import cz.vutbr.web.css.StyleSheet;
import cz.vutbr.web.csskit.MatchConditionOnElements;
import cz.vutbr.web.domassign.DirectAnalyzer;

public class HTMLElementImpl extends ElementImpl implements HTMLElement, CSS2PropertiesContext {
  private final boolean noStyleSheet;
  private static final MatchConditionOnElements elementMatchCondition = new MatchConditionOnElements();
  private static final StyleSheet recommendedStyle = parseStyle(CSSNorm.stdStyleSheet(), StyleSheet.Origin.AGENT);
  private static final StyleSheet userAgentStyle = parseStyle(CSSNorm.userStyleSheet(), StyleSheet.Origin.AGENT);

  public HTMLElementImpl(final String name, final boolean noStyleSheet) {
    super(name);
    this.noStyleSheet = noStyleSheet;
  }

  public HTMLElementImpl(final String name) {
    super(name);
    this.noStyleSheet = false;
  }

  protected final void forgetLocalStyle() {
    synchronized (this) {
      //TODO to be reconsidered in issue #41
      /*
      this.currentStyleDeclarationState = null;
      this.localStyleDeclarationState = null;
      this.computedStyles = null;
       */
    }
  }

  protected final void forgetStyle(final boolean deep) {
    // TODO: OPTIMIZATION: If we had a ComputedStyle map in
    // window (Mozilla model) the map could be cleared in one shot.
    synchronized (this) {
      //TODO to be reconsidered in issue #41
      /*
      this.currentStyleDeclarationState = null;
      this.computedStyles = null;
      this.isHoverStyle = null;
      this.hasHoverStyleByElement = null;
       */
      if (deep) {
        final java.util.ArrayList<Node> nl = this.nodeList;
        if (nl != null) {
          final Iterator<Node> i = nl.iterator();
          while (i.hasNext()) {
            final Object node = i.next();
            if (node instanceof HTMLElementImpl) {
              ((HTMLElementImpl) node).forgetStyle(deep);
            }
          }
        }
      }
    }
  }

  /**
   * Gets the style object associated with the element. It may return null only
   * if the type of element does not handle stylesheets.
   */
  // TODO Cache this method
  // TODO hide from JS
  public JStyleProperties getCurrentStyle() {
    synchronized (this) {
      return new ComputedJStyleProperties(this, getNodeData(null), true);
    }
  }

  private static StyleSheet parseStyle(final String cssdata, final StyleSheet.Origin origin) {
    try {
      final StyleSheet newsheet = CSSFactory.parse(cssdata);
      newsheet.setOrigin(origin);
      return newsheet;
    } catch (IOException | CSSException e) {
      throw new RuntimeException(e);
    }
  }

  // TODO Cache this method
  private NodeData getNodeData(final Selector.PseudoDeclaration psuedoElement) {
    final HTMLDocumentImpl doc = (HTMLDocumentImpl) this.document;
    final List<StyleSheet> jSheets = new ArrayList<>();
    jSheets.add(recommendedStyle);
    jSheets.add(userAgentStyle);
    jSheets.addAll(doc.styleSheetManager.getEnabledJStyleSheets());

    final StyleSheet attributeStyle = StyleElements.convertAttributesToStyles(this);
    if (attributeStyle != null) {
      jSheets.add(attributeStyle);
    }

    final StyleSheet inlineStyle = this.getInlineJStyle();
    if (inlineStyle != null) {
      jSheets.add(inlineStyle);
    }

    final DirectAnalyzer domAnalyser = new cz.vutbr.web.domassign.DirectAnalyzer(jSheets);
    domAnalyser.registerMatchCondition(elementMatchCondition);

    final NodeData nodeData = domAnalyser.getElementStyle(this, psuedoElement, "screen");
    final Node parent = this.parentNode;
    if ((parent != null) && (parent instanceof HTMLElementImpl)) {
      final HTMLElementImpl parentElement = (HTMLElementImpl) parent;
      nodeData.inheritFrom(parentElement.getNodeData(psuedoElement));
      nodeData.concretize();
    }
    return nodeData;
  }

  /**
   * Gets the local style object associated with the element. The properties
   * object returned only includes properties from the local style attribute. It
   * may return null only if the type of element does not handle stylesheets.
   */
  public JStyleProperties getStyle() {
    return new LocalJStyleProperties(this);
  }

  private StyleSheet getInlineJStyle() {
    synchronized (this) {
      final String style = this.getAttribute("style");
      if ((style != null) && (style.length() != 0)) {
        return CSSUtilities.jParseInlineStyle(style, null, this, true);
      }
    }
    // Synchronization note: Make sure getStyle() does not return multiple values.
    return null;
  }

  static private Selector.PseudoDeclaration getPseudoDeclaration(final String pseudoElement) {
    if ((pseudoElement != null)) {
      String choppedPseudoElement = pseudoElement;
      if (pseudoElement.startsWith("::")) {
        choppedPseudoElement = pseudoElement.substring(2, pseudoElement.length());
      } else if (pseudoElement.startsWith(":")) {
        choppedPseudoElement = pseudoElement.substring(1, pseudoElement.length());
      }
      final Selector.PseudoDeclaration[] pseudoDeclarations = Selector.PseudoDeclaration.values();
      for (final Selector.PseudoDeclaration pd : pseudoDeclarations) {
        if (pd.isPseudoElement()) {
          if (pd.value().equals(choppedPseudoElement)) {
            return pd;
          }
        }
      }
    }
    return null;
  }

  // TODO hide from JS
  // Chromium(v37) and firefox(v32) do not expose this function
  // couldn't find anything in the standards.
  public JStyleProperties getComputedStyle(final String pseudoElement) {
    return new ComputedJStyleProperties(this, getNodeData(getPseudoDeclaration(pseudoElement)), false);
  }

  public void setStyle(final Object value) {
    throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "Cannot set style property");
  }

  public void setCurrentStyle(final Object value) {
    throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "Cannot set currentStyle property");
  }

  public String getClassName() {
    final String className = this.getAttribute("class");
    // Blank required instead of null.
    return className == null ? "" : className;
  }

  public void setClassName(final String className) {
    this.setAttribute("class", className);
  }

  public String getCharset() {
    return this.getAttribute("charset");
  }

  public void setCharset(final String charset) {
    this.setAttribute("charset", charset);
  }

  @Override
  public void warn(final String message, final Throwable err) {
    logger.log(Level.WARNING, message, err);
  }

  @Override
  public void warn(final String message) {
    logger.log(Level.WARNING, message);
  }

  protected int getAttributeAsInt(final String name, final int defaultValue) {
    final String value = this.getAttribute(name);
    try {
      return Integer.parseInt(value);
    } catch (final Exception err) {
      this.warn("Bad integer", err);
      return defaultValue;
    }
  }

  public boolean getAttributeAsBoolean(final String name) {
    return this.getAttribute(name) != null;
  }

  @Override
  protected void assignAttributeField(final String normalName, final String value) {
    if (!this.notificationsSuspended) {
      this.informInvalidAttibute(normalName);
    } else {
      if ("style".equals(normalName)) {
        this.forgetLocalStyle();
      }
    }
    super.assignAttributeField(normalName, value);
  }

  protected final static InputSource getCssInputSourceForDecl(final String text) {
    final java.io.Reader reader = new StringReader(text);
    final InputSource is = new InputSource(reader);
    return is;
  }

  private boolean isMouseOver = false;

  public void setMouseOver(final boolean mouseOver) {
    if (this.isMouseOver != mouseOver) {
      if (mouseOver) {
        elementMatchCondition.addMatch(this, PseudoDeclaration.HOVER);
      } else {
        elementMatchCondition.removeMatch(this, PseudoDeclaration.HOVER);
      }
      // Change isMouseOver field before checking to invalidate.
      this.isMouseOver = mouseOver;
      // Check if descendents are affected (e.g. div:hover a { ... } )
      this.invalidateDescendentsForHover();
      if (this.hasHoverStyle()) {
        // TODO: OPTIMIZATION: In some cases it should be much
        // better to simply invalidate the "look" of the node.
        this.informInvalid();
      }
    }
  }

  private void invalidateDescendentsForHover() {
    synchronized (this.treeLock) {
      this.invalidateDescendentsForHoverImpl(this);
    }
  }

  private void invalidateDescendentsForHoverImpl(final HTMLElementImpl ancestor) {
    final ArrayList<Node> nodeList = this.nodeList;
    if (nodeList != null) {
      final int size = nodeList.size();
      for (int i = 0; i < size; i++) {
        final Object node = nodeList.get(i);
        if (node instanceof HTMLElementImpl) {
          final HTMLElementImpl descendent = (HTMLElementImpl) node;
          if (descendent.hasHoverStyle(ancestor)) {
            descendent.informInvalid();
          }
          descendent.invalidateDescendentsForHoverImpl(ancestor);
        }
      }
    }
  }

  //TODO: need to optimize it by checking if there is hover style for the given element
  private boolean hasHoverStyle() {
    return true;
  }

  //TODO: need to optimize it by checking if there is hover style for the given element
  private boolean hasHoverStyle(final HTMLElementImpl ancestor) {
    return true;
  }

  /**
   * Gets the pseudo-element lowercase names currently applicable to this
   * element. Method must return <code>null</code> if there are no such
   * pseudo-elements.
   */
  public Set<String> getPseudoNames() {
    Set<String> pnset = null;
    if (this.isMouseOver) {
      if (pnset == null) {
        pnset = new HashSet<>(1);
      }
      pnset.add("hover");
    }
    return pnset;
  }

  @Override
  public void informInvalid() {
    // This is called when an attribute or child changes.
    // TODO: forgetStyle can call informInvalid() since informInvalid() seems to always follow forgetStyle()
    this.forgetStyle(false);
    super.informInvalid();
  }

  // TODO: Use the handleAttributeChanged() system and remove informInvalidAttribute
  private void informInvalidAttibute(final String normalName) {
    if (isAttachedToDocument()) {
      // This is called when an attribute changes while
      // the element is allowing notifications.
      if ("style".equals(normalName)) {
        this.forgetLocalStyle();
      }

      forgetStyle(true);
      informInvalidRecursive();
    }
  }

  private void informInvalidRecursive() {
    super.informInvalid();
    final NodeImpl[] nodeList = this.getChildrenArray();
    if (nodeList != null) {
      for (final NodeImpl n : nodeList) {
        if (n instanceof HTMLElementImpl) {
          final HTMLElementImpl htmlElementImpl = (HTMLElementImpl) n;
          htmlElementImpl.informInvalidRecursive();
        }
      }
    }
  }

  /**
   * Gets form input due to the current element. It should return
   * <code>null</code> except when the element is a form input element.
   */
  protected FormInput[] getFormInputs() {
    // Override in input elements
    return null;
  }

  private boolean classMatch(final String classTL) {
    final String classNames = this.getClassName();
    if ((classNames == null) || (classNames.length() == 0)) {
      return classTL == null;
    }
    final StringTokenizer tok = new StringTokenizer(classNames, " \t\r\n");
    while (tok.hasMoreTokens()) {
      final String token = tok.nextToken();
      if (token.toLowerCase().equals(classTL)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Get an ancestor that matches the element tag name given and the style class
   * given.
   *
   * @param elementTL
   *          An tag name in lowercase or an asterisk (*).
   * @param classTL
   *          A class name in lowercase.
   */
  public HTMLElementImpl getAncestorWithClass(final String elementTL, final String classTL) {
    final Object nodeObj = this.getParentNode();
    if (nodeObj instanceof HTMLElementImpl) {
      final HTMLElementImpl parentElement = (HTMLElementImpl) nodeObj;
      final String pelementTL = parentElement.getTagName().toLowerCase();
      if (("*".equals(elementTL) || elementTL.equals(pelementTL)) && parentElement.classMatch(classTL)) {
        return parentElement;
      }
      return parentElement.getAncestorWithClass(elementTL, classTL);
    } else {
      return null;
    }
  }

  public HTMLElementImpl getParentWithClass(final String elementTL, final String classTL) {
    final Object nodeObj = this.getParentNode();
    if (nodeObj instanceof HTMLElementImpl) {
      final HTMLElementImpl parentElement = (HTMLElementImpl) nodeObj;
      final String pelementTL = parentElement.getTagName().toLowerCase();
      if (("*".equals(elementTL) || elementTL.equals(pelementTL)) && parentElement.classMatch(classTL)) {
        return parentElement;
      }
    }
    return null;
  }

  public HTMLElementImpl getPreceedingSiblingElement() {
    final Node parentNode = this.getParentNode();
    if (parentNode == null) {
      return null;
    }
    final NodeList childNodes = parentNode.getChildNodes();
    if (childNodes == null) {
      return null;
    }
    final int length = childNodes.getLength();
    HTMLElementImpl priorElement = null;
    for (int i = 0; i < length; i++) {
      final Node child = childNodes.item(i);
      if (child == this) {
        return priorElement;
      }
      if (child instanceof HTMLElementImpl) {
        priorElement = (HTMLElementImpl) child;
      }
    }
    return null;
  }

  public HTMLElementImpl getPreceedingSiblingWithClass(final String elementTL, final String classTL) {
    final HTMLElementImpl psibling = this.getPreceedingSiblingElement();
    if (psibling != null) {
      final String pelementTL = psibling.getTagName().toLowerCase();
      if (("*".equals(elementTL) || elementTL.equals(pelementTL)) && psibling.classMatch(classTL)) {
        return psibling;
      }
    }
    return null;
  }

  public HTMLElementImpl getAncestorWithId(final String elementTL, final String idTL) {
    final Object nodeObj = this.getParentNode();
    if (nodeObj instanceof HTMLElementImpl) {
      final HTMLElementImpl parentElement = (HTMLElementImpl) nodeObj;
      final String pelementTL = parentElement.getTagName().toLowerCase();
      final String pid = parentElement.getId();
      final String pidTL = pid == null ? null : pid.toLowerCase();
      if (("*".equals(elementTL) || elementTL.equals(pelementTL)) && idTL.equals(pidTL)) {
        return parentElement;
      }
      return parentElement.getAncestorWithId(elementTL, idTL);
    } else {
      return null;
    }
  }

  public HTMLElementImpl getParentWithId(final String elementTL, final String idTL) {
    final Object nodeObj = this.getParentNode();
    if (nodeObj instanceof HTMLElementImpl) {
      final HTMLElementImpl parentElement = (HTMLElementImpl) nodeObj;
      final String pelementTL = parentElement.getTagName().toLowerCase();
      final String pid = parentElement.getId();
      final String pidTL = pid == null ? null : pid.toLowerCase();
      if (("*".equals(elementTL) || elementTL.equals(pelementTL)) && idTL.equals(pidTL)) {
        return parentElement;
      }
    }
    return null;
  }

  public HTMLElementImpl getPreceedingSiblingWithId(final String elementTL, final String idTL) {
    final HTMLElementImpl psibling = this.getPreceedingSiblingElement();
    if (psibling != null) {
      final String pelementTL = psibling.getTagName().toLowerCase();
      final String pid = psibling.getId();
      final String pidTL = pid == null ? null : pid.toLowerCase();
      if (("*".equals(elementTL) || elementTL.equals(pelementTL)) && idTL.equals(pidTL)) {
        return psibling;
      }
    }
    return null;
  }

  public HTMLElementImpl getAncestor(final String elementTL) {
    final Object nodeObj = this.getParentNode();
    if (nodeObj instanceof HTMLElementImpl) {
      final HTMLElementImpl parentElement = (HTMLElementImpl) nodeObj;
      if ("*".equals(elementTL)) {
        return parentElement;
      }
      final String pelementTL = parentElement.getTagName().toLowerCase();
      if (elementTL.equals(pelementTL)) {
        return parentElement;
      }
      return parentElement.getAncestor(elementTL);
    } else {
      return null;
    }
  }

  public HTMLElementImpl getParent(final String elementTL) {
    final Object nodeObj = this.getParentNode();
    if (nodeObj instanceof HTMLElementImpl) {
      final HTMLElementImpl parentElement = (HTMLElementImpl) nodeObj;
      if ("*".equals(elementTL)) {
        return parentElement;
      }
      final String pelementTL = parentElement.getTagName().toLowerCase();
      if (elementTL.equals(pelementTL)) {
        return parentElement;
      }
    }
    return null;
  }

  public HTMLElementImpl getPreceedingSibling(final String elementTL) {
    final HTMLElementImpl psibling = this.getPreceedingSiblingElement();
    if (psibling != null) {
      if ("*".equals(elementTL)) {
        return psibling;
      }
      final String pelementTL = psibling.getTagName().toLowerCase();
      if (elementTL.equals(pelementTL)) {
        return psibling;
      }
    }
    return null;
  }

  protected Object getAncestorForJavaClass(final Class<HTMLFormElement> javaClass) {
    final Object nodeObj = this.getParentNode();
    if ((nodeObj == null) || javaClass.isInstance(nodeObj)) {
      return nodeObj;
    } else if (nodeObj instanceof HTMLElementImpl) {
      return ((HTMLElementImpl) nodeObj).getAncestorForJavaClass(javaClass);
    } else {
      return null;
    }
  }

  public void setInnerHTML(final String newHtml) {
    final HTMLDocumentImpl document = (HTMLDocumentImpl) this.document;
    if (document == null) {
      this.warn("setInnerHTML(): Element " + this + " does not belong to a document.");
      return;
    }
    final HtmlParser parser = new HtmlParser(document.getUserAgentContext(), document, null, null, null);
    synchronized (this) {
      removeAllChildrenImpl();
    }
    // Should not synchronize around parser probably.
    try {
      final Reader reader = new StringReader(newHtml);
      try {
        parser.parse(reader, this);
      } finally {
        reader.close();
      }
    } catch (final Exception thrown) {
      this.warn("setInnerHTML(): Error setting inner HTML.", thrown);
    }
  }

  public String getOuterHTML() {
    final StringBuffer buffer = new StringBuffer();
    synchronized (this) {
      this.appendOuterHTMLImpl(buffer);
    }
    return buffer.toString();
  }

  protected void appendOuterHTMLImpl(final StringBuffer buffer) {
    final String tagName = this.getTagName();
    buffer.append('<');
    buffer.append(tagName);
    final Map<String, String> attributes = this.attributes;
    if (attributes != null) {
      attributes.forEach((k, v) -> {
        if (v != null) {
          buffer.append(' ');
          buffer.append(k);
          buffer.append("=\"");
          buffer.append(Strings.strictHtmlEncode(v, true));
          buffer.append("\"");
        }
      });
    }
    final ArrayList<Node> nl = this.nodeList;
    if ((nl == null) || (nl.size() == 0)) {
      buffer.append("/>");
      return;
    }
    buffer.append('>');
    this.appendInnerHTMLImpl(buffer);
    buffer.append("</");
    buffer.append(tagName);
    buffer.append('>');
  }

  @Override
  protected RenderState createRenderState(final RenderState prevRenderState) {
    // Overrides NodeImpl method
    // Called in synchronized block already
    return new StyleSheetRenderState(prevRenderState, this);
  }

  public int getOffsetTop() {
    // TODO: Sometimes this can be called while parsing, and
    // browsers generally give the right answer.
    final UINode uiNode = this.getUINode();
    return uiNode == null ? 0 : uiNode.getBoundsRelativeToBlock().y;
  }

  public int getOffsetLeft() {
    final UINode uiNode = this.getUINode();
    return uiNode == null ? 0 : uiNode.getBoundsRelativeToBlock().x;
  }

  public int getOffsetWidth() {
    final UINode uiNode = this.getUINode();
    return uiNode == null ? 0 : uiNode.getBoundsRelativeToBlock().width;
  }

  public int getOffsetHeight() {
    final UINode uiNode = this.getUINode();
    return uiNode == null ? 0 : uiNode.getBoundsRelativeToBlock().height;
  }

  public String getDocumentBaseURI() {
    final HTMLDocumentImpl doc = (HTMLDocumentImpl) this.document;
    if (doc != null) {
      return doc.getBaseURI();
    } else {
      return null;
    }
  }

  @Override
  protected void handleDocumentAttachmentChanged() {
    if (isAttachedToDocument()) {
      forgetLocalStyle();
      forgetStyle(false);
      informInvalid();
    }
    super.handleDocumentAttachmentChanged();
  }
}
