package org.lobobrowser.html.domimpl;

import org.lobobrowser.html.BrowserFrame;
import org.lobobrowser.html.js.Window;
import org.lobobrowser.html.style.IFrameRenderState;
import org.lobobrowser.html.style.RenderState;
import org.w3c.dom.Document;
import org.w3c.dom.html2.HTMLIFrameElement;

public class HTMLIFrameElementImpl extends HTMLAbstractUIElement implements HTMLIFrameElement, FrameNode {
  private volatile BrowserFrame browserFrame;

  public HTMLIFrameElementImpl(final String name) {
    super(name);
  }

  public void setBrowserFrame(final BrowserFrame frame) {
    this.browserFrame = frame;
    if (frame != null) {
      final String src = this.getAttribute("src");
      if (src != null) {
        try {
          frame.loadURL(this.getFullURL(src));
        } catch (final java.net.MalformedURLException mfu) {
          this.warn("setBrowserFrame(): Unable to navigate to src.", mfu);
        }
      }
    }
  }

  public BrowserFrame getBrowserFrame() {
    return this.browserFrame;
  }

  public String getAlign() {
    return this.getAttribute("align");
  }

  public Document getContentDocument() {
    // TODO: Domain-based security
    final BrowserFrame frame = this.browserFrame;
    if (frame == null) {
      // Not loaded yet
      return null;
    }
    return frame.getContentDocument();
  }

  public Window getContentWindow() {
    final BrowserFrame frame = this.browserFrame;
    if (frame == null) {
      // Not loaded yet
      return null;
    }
    return Window.getWindow(frame.getHtmlRendererContext());
  }

  public String getFrameBorder() {
    return this.getAttribute("frameborder");
  }

  public String getHeight() {
    return this.getAttribute("height");
  }

  public String getLongDesc() {
    return this.getAttribute("longdesc");
  }

  public String getMarginHeight() {
    return this.getAttribute("marginheight");
  }

  public String getMarginWidth() {
    return this.getAttribute("marginwidth");
  }

  public String getName() {
    return this.getAttribute("name");
  }

  public String getScrolling() {
    return this.getAttribute("scrolling");
  }

  public String getSrc() {
    return this.getAttribute("src");
  }

  public String getWidth() {
    return this.getAttribute("width");
  }

  public void setAlign(final String align) {
    this.setAttribute("align", align);
  }

  public void setFrameBorder(final String frameBorder) {
    this.setAttribute("frameborder", frameBorder);
  }

  public void setHeight(final String height) {
    this.setAttribute("height", height);
  }

  public void setLongDesc(final String longDesc) {
    this.setAttribute("longdesc", longDesc);
  }

  public void setMarginHeight(final String marginHeight) {
    this.setAttribute("marginHeight", marginHeight);
  }

  public void setMarginWidth(final String marginWidth) {
    this.setAttribute("marginWidth", marginWidth);
  }

  public void setName(final String name) {
    this.setAttribute("name", name);
  }

  public void setScrolling(final String scrolling) {
    this.setAttribute("scrolling", scrolling);
  }

  public void setSrc(final String src) {
    this.setAttribute("src", src);
  }

  public void setWidth(final String width) {
    this.setAttribute("width", width);
  }

  protected void assignAttributeField(final String normalName, final String value) {
    if ("src".equals(normalName)) {
      final BrowserFrame frame = this.browserFrame;
      if (frame != null) {
        try {
          frame.loadURL(this.getFullURL(value));
        } catch (final java.net.MalformedURLException mfu) {
          this.warn("assignAttributeField(): Unable to navigate to src.", mfu);
        }
      }
    } else {
      super.assignAttributeField(normalName, value);
    }
  }

  protected RenderState createRenderState(final RenderState prevRenderState) {
    return new IFrameRenderState(prevRenderState, this);
  }
}
