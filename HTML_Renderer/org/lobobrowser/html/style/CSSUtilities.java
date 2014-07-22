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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.lobobrowser.html.domimpl.HTMLDocumentImpl;
import org.lobobrowser.ua.NetworkRequest;
import org.lobobrowser.ua.UserAgentContext;
import org.lobobrowser.ua.UserAgentContext.Request;
import org.lobobrowser.ua.UserAgentContext.RequestKind;
import org.lobobrowser.util.SecurityUtil;
import org.lobobrowser.util.Strings;
import org.lobobrowser.util.Urls;
import org.w3c.css.sac.Condition;
import org.w3c.css.sac.InputSource;
import org.w3c.css.sac.Selector;
import org.w3c.css.sac.SiblingSelector;
import org.w3c.css.sac.SimpleSelector;
import org.w3c.dom.css.CSSRule;
import org.w3c.dom.css.CSSStyleDeclaration;
import org.w3c.dom.css.CSSStyleRule;
import org.w3c.dom.css.CSSStyleSheet;
import org.w3c.dom.stylesheets.MediaList;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.steadystate.css.dom.CSSFontFaceRuleImpl;
import com.steadystate.css.dom.CSSMediaRuleImpl;
import com.steadystate.css.dom.CSSRuleListImpl;
import com.steadystate.css.dom.CSSStyleDeclarationImpl;
import com.steadystate.css.dom.CSSStyleRuleImpl;
import com.steadystate.css.dom.CSSStyleSheetImpl;
import com.steadystate.css.dom.MediaListImpl;
import com.steadystate.css.parser.SelectorListImpl;
import com.steadystate.css.parser.selectors.AttributeConditionImpl;
import com.steadystate.css.parser.selectors.BeginHyphenAttributeConditionImpl;
import com.steadystate.css.parser.selectors.ChildSelectorImpl;
import com.steadystate.css.parser.selectors.ClassConditionImpl;
import com.steadystate.css.parser.selectors.ConditionalSelectorImpl;
import com.steadystate.css.parser.selectors.DescendantSelectorImpl;
import com.steadystate.css.parser.selectors.DirectAdjacentSelectorImpl;
import com.steadystate.css.parser.selectors.ElementSelectorImpl;
import com.steadystate.css.parser.selectors.GeneralAdjacentSelectorImpl;
import com.steadystate.css.parser.selectors.IdConditionImpl;
import com.steadystate.css.parser.selectors.OneOfAttributeConditionImpl;
import com.steadystate.css.parser.selectors.PrefixAttributeConditionImpl;
import com.steadystate.css.parser.selectors.PseudoElementSelectorImpl;
import com.steadystate.css.parser.selectors.SubstringAttributeConditionImpl;
import com.steadystate.css.parser.selectors.SuffixAttributeConditionImpl;

import cz.vutbr.web.css.CSSException;
import cz.vutbr.web.css.CSSFactory;
import cz.vutbr.web.css.CombinedSelector;
import cz.vutbr.web.css.Declaration;
import cz.vutbr.web.css.MediaQuery;
import cz.vutbr.web.css.MediaSpec;
import cz.vutbr.web.css.MediaSpecNone;
import cz.vutbr.web.css.RuleBlock;
import cz.vutbr.web.css.RuleFontFace;
import cz.vutbr.web.css.RuleMedia;
import cz.vutbr.web.css.RuleSet;
import cz.vutbr.web.css.Selector.ElementAttribute;
import cz.vutbr.web.css.Selector.ElementClass;
import cz.vutbr.web.css.Selector.ElementID;
import cz.vutbr.web.css.Selector.ElementName;
import cz.vutbr.web.css.Selector.PseudoPage;
import cz.vutbr.web.css.Selector.SelectorPart;
import cz.vutbr.web.css.StyleSheet;

public class CSSUtilities {
  private static final Logger logger = Logger.getLogger(CSSUtilities.class.getName());

  private CSSUtilities() {
  }

  public static String preProcessCss(final String text) {
    try {
      final BufferedReader reader = new BufferedReader(new StringReader(text));
      String line;
      final StringBuffer sb = new StringBuffer();
      String pendingLine = null;
      // Only last line should be trimmed.
      while ((line = reader.readLine()) != null) {
        final String tline = line.trim();
        if (tline.length() != 0) {
          if (pendingLine != null) {
            sb.append(pendingLine);
            sb.append("\r\n");
            pendingLine = null;
          }
          if (tline.startsWith("//")) {
            pendingLine = line;
            continue;
          }
          sb.append(line);
          sb.append("\r\n");
        }
      }
      return sb.toString();
    } catch (final IOException ioe) {
      // not possible
      throw new IllegalStateException(ioe.getMessage());
    }
  }

  public static InputSource getCssInputSourceForStyleSheet(final String text, final String scriptURI) {
    final java.io.Reader reader = new StringReader(text);
    final InputSource is = new InputSource(reader);
    is.setURI(scriptURI);
    return is;
  }

  /* public static CSSOMParser mkParser() {
    return new CSSOMParser(new SACParserCSS3());
  } */

  public static CSSStyleDeclaration parseStyleDeclaration(String styleStr) {
    CSSFactory.setAutoImportMedia(new MediaSpecNone());
    try {
      final StyleSheet sheet = CSSFactory.parse("*{" + styleStr + "}");
      System.out.println("Parse over. Beginning conversion");
      CSSStyleSheetImpl w3cSheet = convertSheetToW3C(null, sheet);
      CSSRule firstRule = w3cSheet.getCssRules().item(0);
      CSSStyleRule firstStyleRule = (CSSStyleRule) firstRule;
      return firstStyleRule.getStyle();
    } catch (IOException | CSSException e) {
      logger.log(Level.WARNING, "Unable to parse CSS.", e);
      return null;
    }
  }

  public static CSSStyleSheet parseStyleSheet(final org.w3c.dom.Node ownerNode, final String baseURI, final String stylesheetStr) {
    return parseCSS2(ownerNode, baseURI, stylesheetStr);
  }

  public static CSSStyleSheet parse(final org.w3c.dom.Node ownerNode, final String href, final HTMLDocumentImpl doc, final String baseUri,
      final boolean considerDoubleSlashComments) throws MalformedURLException {
    final UserAgentContext bcontext = doc.getUserAgentContext();
    final NetworkRequest request = bcontext.createHttpRequest();
    final URL baseURL = new URL(baseUri);
    final URL cssURL = Urls.createURL(baseURL, href);
    final String cssURI = cssURL == null ? href : cssURL.toExternalForm();
    // Perform a synchronous request
    SecurityUtil.doPrivileged(() -> {
      try {
        request.open("GET", cssURI, false);
        request.send(null, new Request(cssURL, RequestKind.CSS));
      } catch (final java.io.IOException thrown) {
        logger.log(Level.WARNING, "parse()", thrown);
      }
      return null;
    });
    final int status = request.getStatus();
    if (status != 200 && status != 0) {
      logger.warning("Unable to parse CSS. URI=[" + cssURI + "]. Response status was " + status + ".");
      return null;
    }

    final String text = request.getResponseText();
    if (text != null && !"".equals(text)) {
      final String processedText = considerDoubleSlashComments ? preProcessCss(text) : text;
      return parseCSS2(ownerNode, cssURI, processedText);
      // return parseCSS(ownerNode, cssURI, processedText);
    } else {
      return null;
    }
  }

  /*
  private static CSSStyleSheet parseCSS(final org.w3c.dom.Node ownerNode, final String cssURI, final String processedText) {
    final CSSOMParser parser = mkParser();
    final InputSource is = getCssInputSourceForStyleSheet(processedText, cssURI);
    is.setURI(cssURI);
    try {
      final CSSStyleSheetImpl sheet = (CSSStyleSheetImpl) parser.parseStyleSheet(is, ownerNode, cssURI);
      return sheet;
    } catch (final Throwable err) {
      logger.log(Level.WARNING, "Unable to parse CSS. URI=[" + cssURI + "].", err);
      return null;
    }
  }*/

  private static CSSStyleSheet parseCSS2(final org.w3c.dom.Node ownerNode, final String cssURI, final String processedText) {
    CSSFactory.setAutoImportMedia(new MediaSpecNone());
    try {
      final StyleSheet sheet = CSSFactory.parse(processedText);
      System.out.println("Parse over. Beginning conversion");
      return convertSheetToW3C(ownerNode, sheet);
    } catch (IOException | CSSException e) {
      logger.log(Level.SEVERE, "Unable to parse CSS. URI=[" + cssURI + "].", e);
      return null;
    }
  }

  private static CSSStyleSheetImpl convertSheetToW3C(final org.w3c.dom.Node ownerNode, final StyleSheet sheet) {
    final MediaSpec screenMediaSpec = new MediaSpec("screen");
    final CSSStyleSheetImpl w3cSheet = new CSSStyleSheetImpl();
    w3cSheet.setOwnerNode(ownerNode);
    final CSSRuleListImpl rules = new CSSRuleListImpl();
    CSSRule previousRule = null;
    for (final RuleBlock<?> ruleBlock : sheet) {
      final CSSRule newRule = convertRuleBlockToW3C(ruleBlock, w3cSheet, previousRule, screenMediaSpec);
      if (newRule != null) {
        rules.add(newRule);
        previousRule = newRule;
      }
    }
    w3cSheet.setCssRules(rules);
    return w3cSheet;
  }

  private static Selector convertSelectorToW3C(final cz.vutbr.web.css.Selector origSelector, final Selector parent) {

    SimpleSelector simpleSelector = null; // new ElementSelectorImpl(origSelector.getElementName());
    SimpleSelector pseudoSelector = null;

    final Stream<SelectorPart> subParts = origSelector.stream();

    for (final SelectorPart part : (Iterable<SelectorPart>) subParts::iterator) {
      if (part instanceof PseudoPage) {
        final PseudoPage pseudoPage = (PseudoPage) part;
        // TODO: Distinguish between pseudo-class and pseudo-element
        // pseudoSelector = new DescendantSelectorImpl(simpleSelector, new PseudoElementSelectorImpl(pseudoPage.getValue()));
        pseudoSelector = new PseudoElementSelectorImpl(pseudoPage.getValue());
      } else if (part instanceof ElementAttribute) {
        final ElementAttribute elementAttribute = (ElementAttribute) part;
        Condition condition;
        final String attrValue = elementAttribute.getValue();
        final boolean valueSpecified = attrValue != null;
        switch (elementAttribute.getOperator()) {
        case EQUALS:
          condition = new AttributeConditionImpl(elementAttribute.getAttribute(), attrValue, valueSpecified);
          break;
        case NO_OPERATOR:
          condition = new AttributeConditionImpl(elementAttribute.getAttribute(), null, false);
          break;
        case CONTAINS:
          condition = new SubstringAttributeConditionImpl(elementAttribute.getAttribute(), attrValue, valueSpecified);
          break;
        case DASHMATCH:
          condition = new BeginHyphenAttributeConditionImpl(elementAttribute.getAttribute(), attrValue, valueSpecified);
          break;
        case ENDSWITH:
          condition = new SuffixAttributeConditionImpl(elementAttribute.getAttribute(), attrValue, valueSpecified);
          break;
        case INCLUDES:
          condition = new OneOfAttributeConditionImpl(elementAttribute.getAttribute(), attrValue, valueSpecified);
          break;
        case STARTSWITH:
          condition = new PrefixAttributeConditionImpl(elementAttribute.getAttribute(), attrValue, valueSpecified);
          break;
        default:
          System.err.println("Not implemented operator:" + elementAttribute.getOperator());
          throw new NotImplementedException();
        }

        simpleSelector = new ConditionalSelectorImpl(makeParentSelector(simpleSelector), condition);

      } else if (part instanceof ElementClass) {
        final ElementClass elementClass = (ElementClass) part;
        final Condition condition = new ClassConditionImpl(elementClass.getClassName());
        simpleSelector = new ConditionalSelectorImpl(makeParentSelector(simpleSelector), condition);
      } else if (part instanceof ElementID) {
        final ElementID elementID = (ElementID) part;
        final Condition condition = new IdConditionImpl(elementID.getID());
        simpleSelector = new ConditionalSelectorImpl(makeParentSelector(simpleSelector), condition);
      } else if (part instanceof ElementName) {
        ElementName elementName = (ElementName) part;
        assert(simpleSelector == null);
        simpleSelector = new ElementSelectorImpl(elementName.getName());
      } else {
        System.err.println("part class: " + part.getClass());
        throw new NotImplementedException();
      }
    }

    Selector w3cSelector = null;
    if (origSelector.getCombinator() == null) {
      w3cSelector = simpleSelector;
    } else {
      final SimpleSelector parentSelector = makeParentSelector(simpleSelector);
      switch (origSelector.getCombinator()) {
      case ADJACENT:
        w3cSelector = new DirectAdjacentSelectorImpl(SiblingSelector.ANY_NODE, parent, parentSelector);
        break;
      case CHILD:
        w3cSelector = new ChildSelectorImpl(parent, parentSelector);
        break;
      case DESCENDANT:
        w3cSelector = new DescendantSelectorImpl(parent, parentSelector);
        break;
      case PRECEDING:
        w3cSelector = new GeneralAdjacentSelectorImpl(SiblingSelector.ANY_NODE, parent, parentSelector);
        break;
      }
    }
    if (pseudoSelector != null) {
      w3cSelector = new DescendantSelectorImpl(w3cSelector, pseudoSelector);
    }

    assert(w3cSelector != null);
    return w3cSelector;
  }

  private static SimpleSelector makeParentSelector(SimpleSelector simpleSelector) {
    return simpleSelector == null ? new ElementSelectorImpl("*"): simpleSelector;
  }

  private static CSSRule convertRuleBlockToW3C(final RuleBlock<?> ruleBlock, final CSSStyleSheetImpl parentStyleSheet,
      final CSSRule parentRule, final MediaSpec mediaSpec) {
    if (ruleBlock instanceof RuleSet) {
      final RuleSet ruleSet = (RuleSet) ruleBlock;
      return convertRuleSetToW3C(parentStyleSheet, parentRule, ruleSet);
    } else if (ruleBlock instanceof RuleMedia) {
      final RuleMedia ruleMedia = (RuleMedia) ruleBlock;
      final boolean ruleMatches = ruleMedia.getMediaQueries().stream().anyMatch(mediaSpec::matches);
      if (ruleMatches) {
      final MediaList mediaList = new MediaListImpl();
      for (final MediaQuery query : ruleMedia.getMediaQueries()) {
        final String queryType = query.getType();
        // TODO: Don't ignore null query types.
        if (queryType != null) {
          mediaList.appendMedium(queryType);
        } else {
          System.out.println("TODO: Null query type: " + query);
        }
      }
      final CSSMediaRuleImpl mediaRule = new CSSMediaRuleImpl(parentStyleSheet, parentRule, mediaList);
      final CSSRuleListImpl cssRules = new CSSRuleListImpl();
      for (final RuleSet ruleSet : ruleMedia) {
        cssRules.add(convertRuleSetToW3C(parentStyleSheet, mediaRule, ruleSet));
      }
      mediaRule.setCssRules(cssRules);

      return mediaRule;
      } else {
        return null;
      }
    } else if (ruleBlock instanceof RuleFontFace) {
      RuleFontFace ruleFontFace = (RuleFontFace) ruleBlock;
      CSSFontFaceRuleImpl fontFaceRule = new CSSFontFaceRuleImpl(parentStyleSheet, parentRule);
      CSSStyleDeclarationImpl styleDeclaration = convertDeclarationsToW3C(ruleFontFace, fontFaceRule);
      fontFaceRule.setStyle(styleDeclaration);
      return fontFaceRule;
    } else {
      System.out.println("rule block class: " + ruleBlock.getClass());
      throw new NotImplementedException();
    }
  }

  private static CSSRule convertRuleSetToW3C(final CSSStyleSheetImpl parentStyleSheet, final CSSRule parentRule, final RuleSet ruleSet) {
    final List<CombinedSelector> combinedSelectors = ruleSet.getSelectors();
    final SelectorListImpl selectorList = new SelectorListImpl();
    combinedSelectors.forEach(combinedSelector -> {
      Selector w3cSelector = null;

      for (final cz.vutbr.web.css.Selector selector : combinedSelector) {
        w3cSelector = convertSelectorToW3C(selector, w3cSelector);
      }
      selectorList.add(w3cSelector);
    });

    final CSSStyleRuleImpl w3cRule = new CSSStyleRuleImpl(parentStyleSheet, parentRule, selectorList);
    w3cRule.setStyle(convertDeclarationsToW3C(ruleSet, w3cRule));
    return w3cRule;
  }

  private static CSSStyleDeclarationImpl convertDeclarationsToW3C(final List<Declaration> ruleSet, CSSRule parentRule) {
    final CSSStyleDeclarationImpl styleDeclarations = new CSSStyleDeclarationImpl(parentRule);
    for (final Declaration declaration : ruleSet) {
      final String terms =
          declaration.stream().map(term -> {
            return term.toString();
          }).collect(Collectors.joining());

      styleDeclarations.setProperty(declaration.getProperty(), terms.trim(), declaration.isImportant() ? "important" : "");

    }
    return styleDeclarations;
  }

  public static boolean matchesMedia(final String mediaValues, final UserAgentContext rcontext) {
    if (mediaValues == null || mediaValues.length() == 0) {
      return true;
    }
    if (rcontext == null) {
      return false;
    }
    final StringTokenizer tok = new StringTokenizer(mediaValues, ",");
    while (tok.hasMoreTokens()) {
      final String token = tok.nextToken().trim();
      final String mediaName = Strings.trimForAlphaNumDash(token);
      if (rcontext.isMedia(mediaName)) {
        return true;
      }
    }
    return false;
  }

  public static boolean matchesMedia(final MediaList mediaList, final UserAgentContext rcontext) {
    if (mediaList == null) {
      return true;
    }
    final int length = mediaList.getLength();
    if (length == 0) {
      return true;
    }
    if (rcontext == null) {
      return false;
    }
    for (int i = 0; i < length; i++) {
      final String mediaName = mediaList.item(i);
      if (rcontext.isMedia(mediaName)) {
        return true;
      }
    }
    return false;
  }

}
