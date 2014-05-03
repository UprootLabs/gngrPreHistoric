package org.lobobrowser.html.domimpl;

import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.html2.*;

public class HTMLOptionsCollectionImpl extends DescendentHTMLCollection implements HTMLOptionsCollection {
  public static final NodeFilter OPTION_FILTER = new OptionFilter();

  public HTMLOptionsCollectionImpl(final HTMLElementImpl selectElement) {
    super(selectElement, OPTION_FILTER, selectElement.treeLock, false);
  }

  public void setLength(final int length) throws DOMException {
    throw new UnsupportedOperationException();
  }

  private static class OptionFilter implements NodeFilter {
    public boolean accept(final Node node) {
      return node instanceof HTMLOptionElement;
    }
  }
}
