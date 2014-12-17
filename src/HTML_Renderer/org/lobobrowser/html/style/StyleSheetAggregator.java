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

package org.lobobrowser.html.style;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.lobobrowser.html.domimpl.HTMLDocumentImpl;
import org.lobobrowser.html.domimpl.HTMLElementImpl;
import org.lobobrowser.ua.UserAgentContext;
import org.w3c.dom.css.CSSImportRule;
import org.w3c.dom.css.CSSMediaRule;
import org.w3c.dom.css.CSSRule;
import org.w3c.dom.css.CSSRuleList;
import org.w3c.dom.css.CSSStyleDeclaration;
import org.w3c.dom.css.CSSStyleRule;
import org.w3c.dom.css.CSSStyleSheet;
import org.w3c.dom.stylesheets.MediaList;

/**
 * Aggregates all style sheets in a document. Every time a new STYLE element is
 * found, it is added to the style sheet aggreagator by means of the
 * {@link #addStyleSheet(CSSStyleSheet)} method. HTML elements have a
 * <code>style</code> object that has a list of <code>CSSStyleDeclaration</code>
 * instances. The instances inserted in that list are obtained by means of the
 * {@link #getStyleDeclarations(HTMLElementImpl, String, String, String)}
 * method.
 */
public class StyleSheetAggregator {
  private final HTMLDocumentImpl document;
  private final Map<String, Map<String, Collection<StyleRuleInfo>>> classMapsByElement = new HashMap<>();
  private final Map<String, Map<String, Collection<StyleRuleInfo>>> idMapsByElement = new HashMap<>();
  private final Map<String, Collection<StyleRuleInfo>> rulesByElement = new HashMap<>();

  public StyleSheetAggregator(final HTMLDocumentImpl document) {
    this.document = document;
  }

  public final void addStyleSheets(final Collection<CSSStyleSheet> styleSheets) throws MalformedURLException {
    final Iterator<CSSStyleSheet> i = styleSheets.iterator();
    while (i.hasNext()) {
      final CSSStyleSheet sheet = i.next();
      this.addStyleSheet(sheet);
    }
  }

  private final void addStyleSheet(final CSSStyleSheet styleSheet) throws MalformedURLException {
    final CSSRuleList ruleList = styleSheet.getCssRules();
    final int length = ruleList.getLength();
    for (int i = 0; i < length; i++) {
      final CSSRule rule = ruleList.item(i);
      this.addRule(styleSheet, rule);
    }
  }

  private final void addRule(final CSSStyleSheet styleSheet, final CSSRule rule) throws MalformedURLException {
    final HTMLDocumentImpl document = this.document;
    if (rule instanceof CSSStyleRule) {
      final CSSStyleRule sr = (CSSStyleRule) rule;
      final String selectorList = sr.getSelectorText();
      final StringTokenizer commaTok = new StringTokenizer(selectorList, ",");
      while (commaTok.hasMoreTokens()) {
        final String selectorPart = commaTok.nextToken().toLowerCase();
        ArrayList<SimpleSelector> simpleSelectors = null;
        String lastSelectorText = null;
        final StringTokenizer tok = new StringTokenizer(selectorPart, " \t\r\n");
        if (tok.hasMoreTokens()) {
          simpleSelectors = new ArrayList<>();
          SimpleSelector prevSelector = null;
          SELECTOR_FOR: for (;;) {
            final String token = tok.nextToken();
            if (">".equals(token)) {
              if (prevSelector != null) {
                prevSelector.setSelectorType(SimpleSelector.PARENT);
              }
              continue SELECTOR_FOR;
            } else if ("+".equals(token)) {
              if (prevSelector != null) {
                prevSelector.setSelectorType(SimpleSelector.PRECEEDING_SIBLING);
              }
              continue SELECTOR_FOR;
            }
            final int colonIdx = token.indexOf(':');
            final String simpleSelectorText = colonIdx == -1 ? token : token.substring(0, colonIdx);
            final String pseudoElement = colonIdx == -1 ? null : token.substring(colonIdx + 1);
            prevSelector = new SimpleSelector(simpleSelectorText, pseudoElement);
            simpleSelectors.add(prevSelector);
            if (!tok.hasMoreTokens()) {
              lastSelectorText = simpleSelectorText;
              break;
            }
          }
        }
        if (lastSelectorText != null) {
          final int dotIdx = lastSelectorText.indexOf('.');
          if (dotIdx != -1) {
            final String elemtl = lastSelectorText.substring(0, dotIdx);
            final String classtl = lastSelectorText.substring(dotIdx + 1);
            this.addClassRule(elemtl, classtl, sr, simpleSelectors);
          } else {
            final int poundIdx = lastSelectorText.indexOf('#');
            if (poundIdx != -1) {
              final String elemtl = lastSelectorText.substring(0, poundIdx);
              final String idtl = lastSelectorText.substring(poundIdx + 1);
              this.addIdRule(elemtl, idtl, sr, simpleSelectors);
            } else {
              final String elemtl = lastSelectorText;
              this.addElementRule(elemtl, sr, simpleSelectors);
            }
          }
        }
      }
      // TODO: Attribute selectors
    } else if (rule instanceof CSSImportRule) {
      final UserAgentContext uacontext = document.getUserAgentContext();
      if (uacontext.isExternalCSSEnabled()) {
        final CSSImportRule importRule = (CSSImportRule) rule;
        if (CSSUtilities.matchesMedia(importRule.getMedia(), uacontext)) {
          final String href = importRule.getHref();
          final String styleHref = styleSheet.getHref();
          final String baseHref = styleHref == null ? document.getBaseURI() : styleHref;
          final CSSStyleSheet sheet = CSSUtilities.parse(styleSheet.getOwnerNode(), href, document, baseHref, false);
          if (sheet != null) {
            this.addStyleSheet(sheet);
          }
        }
      }
    } else if (rule instanceof CSSMediaRule) {
      final CSSMediaRule mrule = (CSSMediaRule) rule;
      final MediaList mediaList = mrule.getMedia();
      if (CSSUtilities.matchesMedia(mediaList, document.getUserAgentContext())) {
        final CSSRuleList ruleList = mrule.getCssRules();
        final int length = ruleList.getLength();
        for (int i = 0; i < length; i++) {
          final CSSRule subRule = ruleList.item(i);
          this.addRule(styleSheet, subRule);
        }
      }
    }
  }

  private final void addClassRule(final String elemtl, final String classtl, final CSSStyleRule styleRule, final ArrayList<SimpleSelector> ancestorSelectors) {
    Map<String, Collection<StyleRuleInfo>> classMap = this.classMapsByElement.get(elemtl);
    if (classMap == null) {
      classMap = new HashMap<>();
      this.classMapsByElement.put(elemtl, classMap);
    }
    Collection<StyleRuleInfo> rules = classMap.get(classtl);
    if (rules == null) {
      rules = new LinkedList<>();
      classMap.put(classtl, rules);
    }
    rules.add(new StyleRuleInfo(ancestorSelectors, styleRule));
  }

  private final void addIdRule(final String elemtl, final String idtl, final CSSStyleRule styleRule, final ArrayList<SimpleSelector> ancestorSelectors) {
    Map<String, Collection<StyleRuleInfo>> idsMap = this.idMapsByElement.get(elemtl);
    if (idsMap == null) {
      idsMap = new HashMap<>();
      this.idMapsByElement.put(elemtl, idsMap);
    }
    Collection<StyleRuleInfo> rules = idsMap.get(idtl);
    if (rules == null) {
      rules = new LinkedList<>();
      idsMap.put(idtl, rules);
    }
    rules.add(new StyleRuleInfo(ancestorSelectors, styleRule));
  }

  private final void addElementRule(final String elemtl, final CSSStyleRule styleRule, final ArrayList<SimpleSelector> ancestorSelectors) {
    Collection<StyleRuleInfo> rules = this.rulesByElement.get(elemtl);
    if (rules == null) {
      rules = new LinkedList<>();
      this.rulesByElement.put(elemtl, rules);
    }
    rules.add(new StyleRuleInfo(ancestorSelectors, styleRule));
  }

  public final Collection<CSSStyleDeclaration> getActiveStyleDeclarations(final HTMLElementImpl element, final String elementName, final String elementId,
      final String className, final Set<String> pseudoNames) {
    Collection<CSSStyleDeclaration> styleDeclarations = null;
    final String elementTL = elementName.toLowerCase();
    Collection<StyleRuleInfo> elementRules = this.rulesByElement.get(elementTL);
    if (elementRules != null) {
      final Iterator<StyleRuleInfo> i = elementRules.iterator();
      while (i.hasNext()) {
        final StyleRuleInfo styleRuleInfo = i.next();
        if (styleRuleInfo.isSelectorMatch(element, pseudoNames)) {
          final CSSStyleRule styleRule = styleRuleInfo.styleRule;
          final CSSStyleSheet styleSheet = styleRule.getParentStyleSheet();
          if (styleSheet != null && styleSheet.getDisabled()) {
            continue;
          }
          if (styleDeclarations == null) {
            styleDeclarations = new LinkedList<>();
          }
          styleDeclarations.add(styleRule.getStyle());
        } else {
        }
      }
    }
    elementRules = this.rulesByElement.get("*");
    if (elementRules != null) {
      final Iterator<StyleRuleInfo> i = elementRules.iterator();
      while (i.hasNext()) {
        final StyleRuleInfo styleRuleInfo = i.next();
        if (styleRuleInfo.isSelectorMatch(element, pseudoNames)) {
          final CSSStyleRule styleRule = styleRuleInfo.styleRule;
          final CSSStyleSheet styleSheet = styleRule.getParentStyleSheet();
          if (styleSheet != null && styleSheet.getDisabled()) {
            continue;
          }
          if (styleDeclarations == null) {
            styleDeclarations = new LinkedList<>();
          }
          styleDeclarations.add(styleRule.getStyle());
        }
      }
    }
    if (className != null) {
      final String classNameTL = className.toLowerCase();
      Map<String, Collection<StyleRuleInfo>> classMaps = this.classMapsByElement.get(elementTL);
      if (classMaps != null) {
        final Collection<StyleRuleInfo> classRules = classMaps.get(classNameTL);
        if (classRules != null) {
          final Iterator<StyleRuleInfo> i = classRules.iterator();
          while (i.hasNext()) {
            final StyleRuleInfo styleRuleInfo = i.next();
            if (styleRuleInfo.isSelectorMatch(element, pseudoNames)) {
              final CSSStyleRule styleRule = styleRuleInfo.styleRule;
              final CSSStyleSheet styleSheet = styleRule.getParentStyleSheet();
              if (styleSheet != null && styleSheet.getDisabled()) {
                continue;
              }
              if (styleDeclarations == null) {
                styleDeclarations = new LinkedList<>();
              }
              styleDeclarations.add(styleRule.getStyle());
            }
          }
        }
      }
      classMaps = this.classMapsByElement.get("*");
      if (classMaps != null) {
        final Collection<StyleRuleInfo> classRules = classMaps.get(classNameTL);
        if (classRules != null) {
          final Iterator<StyleRuleInfo> i = classRules.iterator();
          while (i.hasNext()) {
            final StyleRuleInfo styleRuleInfo = i.next();
            if (styleRuleInfo.isSelectorMatch(element, pseudoNames)) {
              final CSSStyleRule styleRule = styleRuleInfo.styleRule;
              final CSSStyleSheet styleSheet = styleRule.getParentStyleSheet();
              if (styleSheet != null && styleSheet.getDisabled()) {
                continue;
              }
              if (styleDeclarations == null) {
                styleDeclarations = new LinkedList<>();
              }
              styleDeclarations.add(styleRule.getStyle());
            }
          }
        }
      }
    }
    if (elementId != null) {
      Map<String, Collection<StyleRuleInfo>> idMaps = this.idMapsByElement.get(elementTL);
      if (idMaps != null) {
        final String elementIdTL = elementId.toLowerCase();
        final Collection<StyleRuleInfo> idRules = idMaps.get(elementIdTL);
        if (idRules != null) {
          final Iterator<StyleRuleInfo> i = idRules.iterator();
          while (i.hasNext()) {
            final StyleRuleInfo styleRuleInfo = i.next();
            if (styleRuleInfo.isSelectorMatch(element, pseudoNames)) {
              final CSSStyleRule styleRule = styleRuleInfo.styleRule;
              final CSSStyleSheet styleSheet = styleRule.getParentStyleSheet();
              if (styleSheet != null && styleSheet.getDisabled()) {
                continue;
              }
              if (styleDeclarations == null) {
                styleDeclarations = new LinkedList<>();
              }
              styleDeclarations.add(styleRule.getStyle());
            }
          }
        }
      }
      idMaps = this.idMapsByElement.get("*");
      if (idMaps != null) {
        final String elementIdTL = elementId.toLowerCase();
        final Collection<StyleRuleInfo> idRules = idMaps.get(elementIdTL);
        if (idRules != null) {
          final Iterator<StyleRuleInfo> i = idRules.iterator();
          while (i.hasNext()) {
            final StyleRuleInfo styleRuleInfo = i.next();
            if (styleRuleInfo.isSelectorMatch(element, pseudoNames)) {
              final CSSStyleRule styleRule = styleRuleInfo.styleRule;
              final CSSStyleSheet styleSheet = styleRule.getParentStyleSheet();
              if (styleSheet != null && styleSheet.getDisabled()) {
                continue;
              }
              if (styleDeclarations == null) {
                styleDeclarations = new LinkedList<>();
              }
              styleDeclarations.add(styleRule.getStyle());
            }
          }
        }
      }
    }
    return styleDeclarations;
  }

  public final boolean affectedByPseudoNameInAncestor(final HTMLElementImpl element, final HTMLElementImpl ancestor, final String elementName,
      final String elementId, final String[] classArray, final String pseudoName) {
    final String elementTL = elementName.toLowerCase();
    Collection<StyleRuleInfo> elementRules = this.rulesByElement.get(elementTL);
    if (elementRules != null) {
      final Iterator<StyleRuleInfo> i = elementRules.iterator();
      while (i.hasNext()) {
        final StyleRuleInfo styleRuleInfo = i.next();
        final CSSStyleSheet styleSheet = styleRuleInfo.styleRule.getParentStyleSheet();
        if (styleSheet != null && styleSheet.getDisabled()) {
          continue;
        }
        if (styleRuleInfo.affectedByPseudoNameInAncestor(element, ancestor, pseudoName)) {
          return true;
        }
      }
    }
    elementRules = this.rulesByElement.get("*");
    if (elementRules != null) {
      final Iterator<StyleRuleInfo> i = elementRules.iterator();
      while (i.hasNext()) {
        final StyleRuleInfo styleRuleInfo = i.next();
        final CSSStyleSheet styleSheet = styleRuleInfo.styleRule.getParentStyleSheet();
        if (styleSheet != null && styleSheet.getDisabled()) {
          continue;
        }
        if (styleRuleInfo.affectedByPseudoNameInAncestor(element, ancestor, pseudoName)) {
          return true;
        }
      }
    }
    if (classArray != null) {
      for (int cidx = 0; cidx < classArray.length; cidx++) {
        final String className = classArray[cidx];
        final String classNameTL = className.toLowerCase();
        Map<String, Collection<StyleRuleInfo>> classMaps = this.classMapsByElement.get(elementTL);
        if (classMaps != null) {
          final Collection<StyleRuleInfo> classRules = classMaps.get(classNameTL);
          if (classRules != null) {
            final Iterator<StyleRuleInfo> i = classRules.iterator();
            while (i.hasNext()) {
              final StyleRuleInfo styleRuleInfo = i.next();
              final CSSStyleSheet styleSheet = styleRuleInfo.styleRule.getParentStyleSheet();
              if (styleSheet != null && styleSheet.getDisabled()) {
                continue;
              }
              if (styleRuleInfo.affectedByPseudoNameInAncestor(element, ancestor, pseudoName)) {
                return true;
              }
            }
          }
        }
        classMaps = this.classMapsByElement.get("*");
        if (classMaps != null) {
          final Collection<StyleRuleInfo> classRules = classMaps.get(classNameTL);
          if (classRules != null) {
            final Iterator<StyleRuleInfo> i = classRules.iterator();
            while (i.hasNext()) {
              final StyleRuleInfo styleRuleInfo = i.next();
              final CSSStyleSheet styleSheet = styleRuleInfo.styleRule.getParentStyleSheet();
              if (styleSheet != null && styleSheet.getDisabled()) {
                continue;
              }
              if (styleRuleInfo.affectedByPseudoNameInAncestor(element, ancestor, pseudoName)) {
                return true;
              }
            }
          }
        }
      }
    }
    if (elementId != null) {
      Map<String, Collection<StyleRuleInfo>> idMaps = this.idMapsByElement.get(elementTL);
      if (idMaps != null) {
        final String elementIdTL = elementId.toLowerCase();
        final Collection<StyleRuleInfo> idRules = idMaps.get(elementIdTL);
        if (idRules != null) {
          final Iterator<StyleRuleInfo> i = idRules.iterator();
          while (i.hasNext()) {
            final StyleRuleInfo styleRuleInfo = i.next();
            final CSSStyleSheet styleSheet = styleRuleInfo.styleRule.getParentStyleSheet();
            if (styleSheet != null && styleSheet.getDisabled()) {
              continue;
            }
            if (styleRuleInfo.affectedByPseudoNameInAncestor(element, ancestor, pseudoName)) {
              return true;
            }
          }
        }
      }
      idMaps = this.idMapsByElement.get("*");
      if (idMaps != null) {
        final String elementIdTL = elementId.toLowerCase();
        final Collection<StyleRuleInfo> idRules = idMaps.get(elementIdTL);
        if (idRules != null) {
          final Iterator<StyleRuleInfo> i = idRules.iterator();
          while (i.hasNext()) {
            final StyleRuleInfo styleRuleInfo = i.next();
            final CSSStyleSheet styleSheet = styleRuleInfo.styleRule.getParentStyleSheet();
            if (styleSheet != null && styleSheet.getDisabled()) {
              continue;
            }
            if (styleRuleInfo.affectedByPseudoNameInAncestor(element, ancestor, pseudoName)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static class StyleRuleInfo {
    private final CSSStyleRule styleRule;
    private final ArrayList<SimpleSelector> ancestorSelectors;

    /**
     * @param selectors
     *          A collection of SimpleSelector's.
     * @param rule
     *          A CSS rule.
     */
    public StyleRuleInfo(final ArrayList<SimpleSelector> simpleSelectors, final CSSStyleRule rule) {
      super();
      ancestorSelectors = simpleSelectors;
      styleRule = rule;
    }

    public final boolean affectedByPseudoNameInAncestor(final HTMLElementImpl element, final HTMLElementImpl ancestor, final String pseudoName) {
      final ArrayList<SimpleSelector> as = this.ancestorSelectors;
      HTMLElementImpl currentElement = element;
      final int size = as.size();
      boolean first = true;
      for (int i = size; --i >= 0;) {
        final SimpleSelector simpleSelector = as.get(i);
        if (first) {
          if (ancestor == element) {
            return simpleSelector.hasPseudoName(pseudoName);
          }
          first = false;
          continue;
        }
        final String selectorText = simpleSelector.simpleSelectorText;
        final int dotIdx = selectorText.indexOf('.');
        HTMLElementImpl newElement;
        if (dotIdx != -1) {
          final String elemtl = selectorText.substring(0, dotIdx);
          final String classtl = selectorText.substring(dotIdx + 1);
          newElement = currentElement.getAncestorWithClass(elemtl, classtl);
        } else {
          final int poundIdx = selectorText.indexOf('#');
          if (poundIdx != -1) {
            final String elemtl = selectorText.substring(0, poundIdx);
            final String idtl = selectorText.substring(poundIdx + 1);
            newElement = currentElement.getAncestorWithId(elemtl, idtl);
          } else {
            final String elemtl = selectorText;
            newElement = currentElement.getAncestor(elemtl);
          }
        }
        if (newElement == null) {
          return false;
        }
        currentElement = newElement;
        if (currentElement == ancestor) {
          return simpleSelector.hasPseudoName(pseudoName);
        }
      }
      return false;
    }

    /**
     * 
     * @param element
     *          The element to test for a match.
     * @param pseudoNames
     *          A set of pseudo-names in lowercase.
     */
    private final boolean isSelectorMatch(final HTMLElementImpl element, final Set<String> pseudoNames) {
      final ArrayList<SimpleSelector> as = this.ancestorSelectors;
      HTMLElementImpl currentElement = element;
      final int size = as.size();
      boolean first = true;
      for (int i = size; --i >= 0;) {
        final SimpleSelector simpleSelector = as.get(i);
        if (first) {
          if (!simpleSelector.matches(pseudoNames)) {
            return false;
          }
          first = false;
          continue;
        }
        final String selectorText = simpleSelector.simpleSelectorText;
        final int dotIdx = selectorText.indexOf('.');
        final int selectorType = simpleSelector.selectorType;
        HTMLElementImpl priorElement;
        if (dotIdx != -1) {
          final String elemtl = selectorText.substring(0, dotIdx);
          final String classtl = selectorText.substring(dotIdx + 1);
          if (selectorType == SimpleSelector.ANCESTOR) {
            priorElement = currentElement.getAncestorWithClass(elemtl, classtl);
          } else if (selectorType == SimpleSelector.PARENT) {
            priorElement = currentElement.getParentWithClass(elemtl, classtl);
          } else if (selectorType == SimpleSelector.PRECEEDING_SIBLING) {
            priorElement = currentElement.getPreceedingSiblingWithClass(elemtl, classtl);
          } else {
            throw new IllegalStateException("selectorType=" + selectorType);
          }
        } else {
          final int poundIdx = selectorText.indexOf('#');
          if (poundIdx != -1) {
            final String elemtl = selectorText.substring(0, poundIdx);
            final String idtl = selectorText.substring(poundIdx + 1);
            if (selectorType == SimpleSelector.ANCESTOR) {
              priorElement = currentElement.getAncestorWithId(elemtl, idtl);
            } else if (selectorType == SimpleSelector.PARENT) {
              priorElement = currentElement.getParentWithId(elemtl, idtl);
            } else if (selectorType == SimpleSelector.PRECEEDING_SIBLING) {
              priorElement = currentElement.getPreceedingSiblingWithId(elemtl, idtl);
            } else {
              throw new IllegalStateException("selectorType=" + selectorType);
            }
          } else {
            final String elemtl = selectorText;
            if (selectorType == SimpleSelector.ANCESTOR) {
              priorElement = currentElement.getAncestor(elemtl);
            } else if (selectorType == SimpleSelector.PARENT) {
              priorElement = currentElement.getParent(elemtl);
            } else if (selectorType == SimpleSelector.PRECEEDING_SIBLING) {
              priorElement = currentElement.getPreceedingSibling(elemtl);
            } else {
              throw new IllegalStateException("selectorType=" + selectorType);
            }
          }
        }
        if (priorElement == null) {
          return false;
        }
        if (!simpleSelector.matches(priorElement)) {
          return false;
        }
        currentElement = priorElement;
      }
      return true;
    }
  }

  static class SimpleSelector {
    public static final int ANCESTOR = 0;
    public static final int PARENT = 1;
    public static final int PRECEEDING_SIBLING = 2;

    public final String simpleSelectorText;
    public final String pseudoElement;
    public int selectorType;

    /**
     * 
     * @param simpleSelectorText
     *          Simple selector text in lower case.
     * @param pseudoElement
     *          The pseudo-element if any.
     */
    public SimpleSelector(final String simpleSelectorText, final String pseudoElement) {
      super();
      this.simpleSelectorText = simpleSelectorText;
      this.pseudoElement = pseudoElement;
      this.selectorType = ANCESTOR;
    }

    public final boolean matches(final HTMLElementImpl element) {
      final Set<String> names = element.getPseudoNames();
      if (names == null) {
        return this.pseudoElement == null;
      } else {
        final String pe = this.pseudoElement;
        return pe == null || names.contains(pe);
      }
    }

    public final boolean matches(final Set<String> names) {
      if (names == null) {
        return this.pseudoElement == null;
      } else {
        final String pe = this.pseudoElement;
        return pe == null || names.contains(pe);
      }
    }

    public final boolean matches(final String pseudoName) {
      if (pseudoName == null) {
        return this.pseudoElement == null;
      } else {
        final String pe = this.pseudoElement;
        return pe == null || pseudoName.equals(pe);
      }
    }

    public final boolean hasPseudoName(final String pseudoName) {
      return pseudoName.equals(this.pseudoElement);
    }

    public int getSelectorType() {
      return selectorType;
    }

    public void setSelectorType(final int selectorType) {
      this.selectorType = selectorType;
    }
  }
}
