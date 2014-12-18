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
 * Created on Apr 16, 2005
 */
package org.lobobrowser.html.gui;

import java.awt.Adjustable;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JComponent;
import javax.swing.KeyStroke;

import org.lobobrowser.html.HtmlRendererContext;
import org.lobobrowser.html.domimpl.HTMLElementImpl;
import org.lobobrowser.html.domimpl.ModelNode;
import org.lobobrowser.html.domimpl.NodeImpl;
import org.lobobrowser.html.domimpl.UINode;
import org.lobobrowser.html.renderer.BoundableRenderable;
import org.lobobrowser.html.renderer.DelayedPair;
import org.lobobrowser.html.renderer.FrameContext;
import org.lobobrowser.html.renderer.NodeRenderer;
import org.lobobrowser.html.renderer.RBlock;
import org.lobobrowser.html.renderer.RCollection;
import org.lobobrowser.html.renderer.RElement;
import org.lobobrowser.html.renderer.Renderable;
import org.lobobrowser.html.renderer.RenderableContainer;
import org.lobobrowser.html.renderer.RenderableSpot;
import org.lobobrowser.html.style.RenderState;
import org.lobobrowser.ua.UserAgentContext;
import org.lobobrowser.util.Nodes;
import org.lobobrowser.util.Objects;
import org.lobobrowser.util.gui.ColorFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * A Swing component that renders a HTML block, given by a DOM root or an
 * internal element, typically a DIV. This component <i>cannot</i> render
 * FRAMESETs. <code>HtmlBlockPanel</code> is used by {@link HtmlPanel} whenever
 * the DOM is determined <i>not</i> to be a FRAMESET.
 *
 * @see HtmlPanel
 * @see FrameSetPanel
 * @author J. H. S.
 */
public class HtmlBlockPanel extends JComponent implements NodeRenderer, RenderableContainer, ClipboardOwner {
  private static final Logger logger = Logger.getLogger(HtmlBlockPanel.class.getName());
  private static final boolean loggableInfo = logger.isLoggable(Level.INFO);
  protected final FrameContext frameContext;
  protected final UserAgentContext ucontext;
  protected final HtmlRendererContext rcontext;

  protected RenderableSpot startSelection;
  protected RenderableSpot endSelection;
  protected RBlock rblock;
  protected int preferredWidth = -1;
  protected Insets defaultMarginInsets = null;
  // protected Insets defaultPaddingInsets = null;
  protected int defaultOverflowX = RenderState.OVERFLOW_AUTO;
  protected int defaultOverflowY = RenderState.OVERFLOW_SCROLL;

  public HtmlBlockPanel(final UserAgentContext pcontext, final HtmlRendererContext rcontext, final FrameContext frameContext) {
    this(ColorFactory.TRANSPARENT, false, pcontext, rcontext, frameContext);
  }

  public HtmlBlockPanel(final Color background, final boolean opaque, final UserAgentContext pcontext, final HtmlRendererContext rcontext,
      final FrameContext frameContext) {
    this.setLayout(null);
    this.setAutoscrolls(true);
    this.frameContext = frameContext;
    this.ucontext = pcontext;
    this.rcontext = rcontext;
    this.setOpaque(opaque);
    this.setBackground(background);
    final ActionListener actionListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final String command = e.getActionCommand();
        if ("copy".equals(command)) {
          copy();
        }
      }
    };
    if (!GraphicsEnvironment.isHeadless()) {
      this.registerKeyboardAction(actionListener, "copy", KeyStroke.getKeyStroke(KeyEvent.VK_COPY, 0), JComponent.WHEN_FOCUSED);
      this.registerKeyboardAction(actionListener, "copy",
          KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), JComponent.WHEN_FOCUSED);
    }
    this.addMouseListener(new MouseListener() {
      public void mouseClicked(final MouseEvent e) {
        onMouseClick(e);
      }

      public void mouseEntered(final MouseEvent e) {
      }

      public void mouseExited(final MouseEvent e) {
        onMouseExited(e);
      }

      public void mousePressed(final MouseEvent e) {
        onMousePressed(e);
      }

      public void mouseReleased(final MouseEvent e) {
        onMouseReleased(e);
      }
    });
    this.addMouseMotionListener(new MouseMotionListener() {
      /*
       * (non-Javadoc)
       *
       * @see
       * java.awt.event.MouseMotionListener#mouseDragged(java.awt.event.MouseEvent
       * )
       */
      public void mouseDragged(final MouseEvent e) {
        onMouseDragged(e);
      }

      /*
       * (non-Javadoc)
       *
       * @see
       * java.awt.event.MouseMotionListener#mouseMoved(java.awt.event.MouseEvent
       * )
       */
      public void mouseMoved(final MouseEvent arg0) {
        onMouseMoved(arg0);
      }
    });
    this.addMouseWheelListener(new MouseWheelListener() {
      public void mouseWheelMoved(final MouseWheelEvent e) {
        onMouseWheelMoved(e);
      }
    });
  }

  /**
   * Scrolls the body area to the given location.
   * <p>
   * This method should be called from the GUI thread.
   *
   * @param bounds
   *          The bounds in the scrollable block area that should become
   *          visible.
   * @param xIfNeeded
   *          If this parameter is true, scrolling will only occur if the
   *          requested bounds are not currently visible horizontally.
   * @param yIfNeeded
   *          If this parameter is true, scrolling will only occur if the
   *          requested bounds are not currently visible vertically.
   */
  public void scrollTo(final Rectangle bounds, final boolean xIfNeeded, final boolean yIfNeeded) {
    final RBlock block = this.rblock;
    if (block != null) {
      block.scrollTo(bounds, xIfNeeded, yIfNeeded);
    }
  }

  public void scrollBy(final int xOffset, final int yOffset) {
    final RBlock block = this.rblock;
    if (block != null) {
      if (xOffset != 0) {
        block.scrollBy(Adjustable.HORIZONTAL, xOffset);
      }
      if (yOffset != 0) {
        block.scrollBy(Adjustable.VERTICAL, yOffset);
      }
    }
  }

  /**
   * Scrolls the body area to the node given, if it is part of the current
   * document.
   * <p>
   * This method should be called from the GUI thread.
   *
   * @param node
   *          A DOM node.
   */
  public void scrollTo(final Node node) {
    final Rectangle bounds = this.getNodeBounds(node, true);
    if (bounds == null) {
      return;
    }
    this.scrollTo(bounds, true, false);
  }

  /**
   * Gets the rectangular bounds of the given node.
   * <p>
   * This method should be called from the GUI thread.
   *
   * @param node
   *          A node in the current document.
   * @param relativeToScrollable
   *          Whether the bounds should be relative to the scrollable body area.
   *          Otherwise, they are relative to the root block (which is the
   *          essentially the same as being relative to this
   *          <code>HtmlBlockPanel</code> minus Swing borders).
   */
  public Rectangle getNodeBounds(final Node node, final boolean relativeToScrollable) {
    final RBlock block = this.rblock;
    if (block == null) {
      return null;
    }
    // Find UINode first
    Node currentNode = node;
    UINode uiNode = null;
    while (currentNode != null) {
      if (currentNode instanceof HTMLElementImpl) {
        final HTMLElementImpl element = (HTMLElementImpl) currentNode;
        uiNode = element.getUINode();
        if (uiNode != null) {
          break;
        }
      }
      currentNode = currentNode.getParentNode();
    }
    if (uiNode == null) {
      return null;
    }
    final RCollection relativeTo = relativeToScrollable ? (RCollection) block.getRBlockViewport() : (RCollection) block;
    if (node == currentNode) {
      final BoundableRenderable br = (BoundableRenderable) uiNode;
      final Point guiPoint = br.getOriginRelativeTo(relativeTo);
      final Dimension size = br.getSize();
      return new Rectangle(guiPoint, size);
    } else {
      return this.scanNodeBounds((RCollection) uiNode, node, relativeTo);
    }
  }

  /**
   * Gets an aggregate of the bounds of renderer leaf nodes.
   */
  private Rectangle scanNodeBounds(final RCollection root, final Node node, final RCollection relativeTo) {
    final Iterator<? extends Renderable> i = root.getRenderables();
    Rectangle resultBounds = null;
    BoundableRenderable prevBoundable = null;
    if (i != null) {
      while (i.hasNext()) {
        final Renderable r = i.next();
        Rectangle subBounds = null;
        if (r instanceof RCollection) {
          final RCollection rc = (RCollection) r;
          prevBoundable = rc;
          subBounds = this.scanNodeBounds(rc, node, relativeTo);
        } else if (r instanceof BoundableRenderable) {
          final BoundableRenderable br = (BoundableRenderable) r;
          prevBoundable = br;
          if (Nodes.isSameOrAncestorOf(node, (Node) r.getModelNode())) {
            final Point origin = br.getOriginRelativeTo(relativeTo);
            final Dimension size = br.getSize();
            subBounds = new Rectangle(origin, size);
          }
        } else {
          // This would have to be a RStyleChanger. We rely on these
          // when the target node has blank content.
          if (Nodes.isSameOrAncestorOf(node, (Node) r.getModelNode())) {
            final int xInRoot = prevBoundable == null ? 0 : prevBoundable.getX() + prevBoundable.getWidth();
            final Point rootOrigin = root.getOriginRelativeTo(relativeTo);
            subBounds = new Rectangle(rootOrigin.x + xInRoot, rootOrigin.y, 0, root.getHeight());
          }
        }
        if (subBounds != null) {
          if (resultBounds == null) {
            resultBounds = subBounds;
          } else {
            resultBounds = subBounds.union(resultBounds);
          }
        }
      }
    }
    return resultBounds;
  }

  public BoundableRenderable getRootRenderable() {
    return this.rblock;
  }

  /**
   * Allows {@link #getPreferredSize()} to render the HTML block in order to
   * determine the preferred size of this component. Note that
   * <code>getPreferredSize()<code> is a potentially time-consuming
   * operation if the preferred width is set.
   *
   * @param width
   *          The preferred blocked width. Use <code>-1</code> to unset.
   */
  public void setPreferredWidth(final int width) {
    this.preferredWidth = width;
  }

  /**
   * If the preferred size has been set with
   * {@link #setPreferredSize(Dimension)}, then that size is returned. Otherwise
   * a preferred size is calculated by rendering the HTML DOM, provided one is
   * available and a preferred width other than <code>-1</code> has been set
   * with {@link #setPreferredWidth(int)}. An arbitrary preferred size is
   * returned in other scenarios.
   */
  @Override
  public Dimension getPreferredSize() {
    // Expected to be invoked in the GUI thread.
    if (this.isPreferredSizeSet()) {
      return super.getPreferredSize();
    }
    final int pw = this.preferredWidth;
    if (pw != -1) {
      final RBlock block = this.rblock;
      if (block != null) {
        // Layout should always be done in the GUI thread.
        if (EventQueue.isDispatchThread()) {
          block.layout(pw, 0, false, false, RenderState.OVERFLOW_VISIBLE, RenderState.OVERFLOW_VISIBLE, true);
        } else {
          try {
            EventQueue.invokeAndWait(new Runnable() {
              public void run() {
                block.layout(pw, 0, false, false, RenderState.OVERFLOW_VISIBLE, RenderState.OVERFLOW_VISIBLE, true);
              }
            });
          } catch (final Exception err) {
            logger.log(Level.SEVERE, "Unable to do preferred size layout.", err);
          }
        }
        // Adjust for permanent vertical scrollbar.
        final int newPw = Math.max(block.width + block.getVScrollBarWidth(), pw);
        return new Dimension(newPw, block.height);
      }
    }
    return new Dimension(600, 400);
  }

  @Override
  public void finalize() throws Throwable {
    super.finalize();
  }

  public boolean copy() {
    final String selection = HtmlBlockPanel.this.getSelectionText();
    if (selection != null) {
      final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      clipboard.setContents(new StringSelection(selection), HtmlBlockPanel.this);
      return true;
    } else {
      return false;
    }
  }

  // public void setDefaultPaddingInsets(Insets insets) {
  // this.defaultPaddingInsets = insets;
  // RBlock block = this.rblock;
  // if(block != null) {
  // block.setDefaultPaddingInsets(insets);
  // }
  // }

  public int getFirstLineHeight() {
    final RBlock block = this.rblock;
    return block == null ? 0 : block.getFirstLineHeight();
  }

  public void setSelectionEnd(final RenderableSpot rpoint) {
    this.endSelection = rpoint;
  }

  public void setSelectionStart(final RenderableSpot rpoint) {
    this.startSelection = rpoint;
  }

  public boolean isSelectionAvailable() {
    final RenderableSpot start = this.startSelection;
    final RenderableSpot end = this.endSelection;
    return (start != null) && (end != null) && !start.equals(end);
  }

  public org.w3c.dom.Node getSelectionNode() {
    final RenderableSpot start = this.startSelection;
    final RenderableSpot end = this.endSelection;
    if ((start != null) && (end != null)) {
      return Nodes.getCommonAncestor((Node) start.renderable.getModelNode(), (Node) end.renderable.getModelNode());
    } else {
      return null;
    }
  }

  /**
   * Sets the root node to render. This method should be invoked in the GUI
   * dispatch thread.
   */
  public void setRootNode(final NodeImpl node) {
    if (node != null) {
      final RBlock block = new RBlock(node, 0, this.ucontext, this.rcontext, this.frameContext, this);
      block.setDefaultMarginInsets(this.defaultMarginInsets);
      // block.setDefaultPaddingInsets(this.defaultPaddingInsets);
      block.setDefaultOverflowX(this.defaultOverflowX);
      block.setDefaultOverflowY(this.defaultOverflowY);
      node.setUINode(block);
      this.rblock = block;
    } else {
      this.rblock = null;
    }
    this.invalidate();
    this.validateAll();
    this.repaint();
  }

  protected void validateAll() {
    Component toValidate = this;
    for (;;) {
      final Container parent = toValidate.getParent();
      if ((parent == null) || parent.isValid()) {
        break;
      }
      toValidate = parent;
    }
    toValidate.validate();
  }

  protected void revalidatePanel() {
    // Called in the GUI thread.
    this.invalidate();
    this.validate();
    // TODO: Could be paintImmediately.
    this.repaint();
  }

  public NodeImpl getRootNode() {
    final RBlock block = this.rblock;
    return block == null ? null : (NodeImpl) block.getModelNode();
  }

  private void onMouseClick(final MouseEvent event) {
    // Rely on AWT mouse-click only for double-clicks
    final RBlock block = this.rblock;
    if (block != null) {
      final int button = event.getButton();
      final int clickCount = event.getClickCount();
      if ((button == MouseEvent.BUTTON1) && (clickCount > 1)) {
        // TODO: Double-click must be revised. It generates
        // a single click via mouse release.
        final Point point = event.getPoint();
        block.onDoubleClick(event, point.x, point.y);
      } else if ((button == MouseEvent.BUTTON3) && (clickCount == 1)) {
        block.onRightClick(event, event.getX(), event.getY());
      }
    }
  }

  private BoundableRenderable mousePressTarget;

  private void onMousePressed(final MouseEvent event) {
    this.requestFocus();
    final RBlock block = this.rblock;
    if (block != null) {
      final Point point = event.getPoint();
      this.mousePressTarget = block;
      final int rx = point.x;
      final int ry = point.y;
      block.onMousePressed(event, point.x, point.y);
      final RenderableSpot rp = block.getLowestRenderableSpot(rx, ry);
      if (rp != null) {
        this.frameContext.resetSelection(rp);
      } else {
        this.frameContext.resetSelection(null);
      }
    }
  }

  private void onMouseReleased(final MouseEvent event) {
    final RBlock block = this.rblock;
    if (block != null) {
      final Point point = event.getPoint();
      final int rx = point.x;
      final int ry = point.y;
      if (event.getButton() == MouseEvent.BUTTON1) {
        // TODO: This will be raised twice on a double-click.
        block.onMouseClick(event, rx, ry);
      }
      block.onMouseReleased(event, rx, ry);
      final BoundableRenderable oldTarget = this.mousePressTarget;
      if (oldTarget != null) {
        this.mousePressTarget = null;
        if (oldTarget != block) {
          oldTarget.onMouseDisarmed(event);
        }
      }
    } else {
      this.mousePressTarget = null;
    }
  }

  private void onMouseExited(final MouseEvent event) {
    final BoundableRenderable oldTarget = this.mousePressTarget;
    if (oldTarget != null) {
      this.mousePressTarget = null;
      oldTarget.onMouseDisarmed(event);
    }
  }

  private void onMouseWheelMoved(final MouseWheelEvent mwe) {
    final RBlock block = this.rblock;
    if (block != null) {
      switch (mwe.getScrollType()) {
      case MouseWheelEvent.WHEEL_UNIT_SCROLL:
        final int units = mwe.getWheelRotation() * mwe.getScrollAmount();
        block.scrollByUnits(Adjustable.VERTICAL, units);
        break;
      }
    }
  }

  private void onMouseDragged(final MouseEvent event) {
    final RBlock block = this.rblock;
    if (block != null) {
      final Point point = event.getPoint();
      final RenderableSpot rp = block.getLowestRenderableSpot(point.x, point.y);
      if (rp != null) {
        this.frameContext.expandSelection(rp);
      }
      block.ensureVisible(point);
    }
  }

  private void onMouseMoved(final MouseEvent event) {
    final RBlock block = this.rblock;
    if (block != null) {
      final Point point = event.getPoint();
      block.onMouseMoved(event, point.x, point.y, false, null);
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see javax.swing.JComponent#paintComponent(java.awt.Graphics)
   */
  // protected void paintComponent(Graphics g) {
  @Override
  public void paint(final Graphics g) {
    // We go against Sun's advice and override
    // paint() instead of paintComponent(). Scrollbars
    // do not repaint correctly if we use
    // paintComponent.
    if (this.isOpaque()) {
      // Background not painted by default in JComponent.
      final Rectangle clipBounds = g.getClipBounds();
      g.setColor(this.getBackground());
      g.fillRect(clipBounds.x, clipBounds.y, clipBounds.width, clipBounds.height);
    }
    if (g instanceof Graphics2D) {
      final Graphics2D g2 = (Graphics2D) g;
      try {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
      } catch (final NoSuchFieldError e) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      }
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }
    final RBlock block = this.rblock;
    if (block != null) {
      final boolean liflag = loggableInfo;
      final long time1 = liflag ? System.currentTimeMillis() : 0;
      block.paint(g);
      if (liflag) {
        final long time2 = System.currentTimeMillis();
        final Node rootNode = this.getRootNode();
        final String uri = rootNode instanceof Document ? ((Document) rootNode).getDocumentURI() : "";
        logger.info("paintComponent(): URI=[" + uri + "]. Block paint elapsed: " + (time2 - time1) + " ms.");
      }

      // Paint FrameContext selection

      final RenderableSpot start = this.startSelection;
      final RenderableSpot end = this.endSelection;
      if ((start != null) && (end != null) && !start.equals(end)) {
        block.paintSelection(g, false, start, end);
      }
    }
  }

  @Override
  public void doLayout() {
    try {
      final Dimension size = this.getSize();
      final boolean liflag = loggableInfo;
      long time1 = 0;
      if (liflag) {
        time1 = System.currentTimeMillis();
      }
      this.clearComponents();
      final RBlock block = this.rblock;
      if (block != null) {
        final ModelNode rootNode = block.getModelNode();
        block.layout(size.width, size.height, true, true, null, false);
        // Only set origin
        block.setOrigin(0, 0);
        block.updateWidgetBounds(0, 0);
        this.updateGUIComponents();
        if (liflag) {
          final long time2 = System.currentTimeMillis();
          final String uri = rootNode instanceof Document ? ((Document) rootNode).getDocumentURI() : "";
          logger.info("doLayout(): URI=[" + uri + "]. Block layout elapsed: " + (time2 - time1) + " ms. Component count: "
              + this.getComponentCount() + ".");
        }
      } else {
        if (this.getComponentCount() > 0) {
          this.removeAll();
        }
      }
    } catch (final Throwable thrown) {
      logger.log(Level.SEVERE, "Unexpected error in layout engine. Document is " + this.getRootNode(), thrown);
    }
  }

  /**
   * Implementation of UINode.repaint().
   */
  public void repaint(final ModelNode modelNode) {
    // this.rblock.invalidateRenderStyle();
    this.repaint();
  }

  public String getSelectionText() {
    final RenderableSpot start = this.startSelection;
    final RenderableSpot end = this.endSelection;
    if ((start != null) && (end != null)) {
      final StringBuffer buffer = new StringBuffer();
      this.rblock.extractSelectionText(buffer, false, start, end);
      return buffer.toString();
    } else {
      return null;
    }
  }

  public boolean hasSelection() {
    final RenderableSpot start = this.startSelection;
    final RenderableSpot end = this.endSelection;
    if ((start != null) && (end != null) && !start.equals(end)) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  protected void paintChildren(final Graphics g) {
    // Overridding with NOP. For various reasons,
    // the regular mechanism for painting children
    // needs to be handled by Cobra.
  }

  public Color getPaintedBackgroundColor() {
    return this.isOpaque() ? this.getBackground() : null;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * java.awt.datatransfer.ClipboardOwner#lostOwnership(java.awt.datatransfer
   * .Clipboard, java.awt.datatransfer.Transferable)
   */
  public void lostOwnership(final Clipboard arg0, final Transferable arg1) {
  }

  public void relayout() {
    // Expected to be called in the GUI thread.
    // Renderable branch should be invalidated at this
    // point, but this GUI component not necessarily.
    this.revalidatePanel();
  }

  public void invalidateLayoutUpTree() {
    // Called when renderable branch is invalidated.
    // We shouldn't do anything here. Changes in renderer
    // tree do not have any bearing on validity of GUI
    // component.
  }

  public void updateAllWidgetBounds() {
    this.rblock.updateWidgetBounds(0, 0);
  }

  public Point getGUIPoint(final int clientX, final int clientY) {
    // This is the GUI!
    return new Point(clientX, clientY);
  }

  public void focus() {
    this.grabFocus();
  }

  private boolean processingDocumentNotification = false;

  void processDocumentNotifications(final DocumentNotification[] notifications) {
    // Called in the GUI thread.
    if (this.processingDocumentNotification) {
      // This should not be possible. Even if
      // Javascript modifies the DOM during
      // parsing, this should be executed in
      // the GUI thread, not the parser thread.
      throw new IllegalStateException("Recursive");
    }
    this.processingDocumentNotification = true;
    try {
      // Note: It may be assumed that usually only generic
      // notifications come in batches. Other types
      // of noitifications probably come one by one.
      boolean topLayout = false;
      java.util.ArrayList<RElement> repainters = null;
      final int length = notifications.length;
      for (int i = 0; i < length; i++) {
        final DocumentNotification dn = notifications[i];
        final int type = dn.type;
        switch (type) {
        case DocumentNotification.GENERIC:
        case DocumentNotification.SIZE: {
          final NodeImpl node = dn.node;
          if (node == null) {
            // This is all-invalidate (new style sheet)
            if (loggableInfo) {
              logger.info("processDocumentNotifications(): Calling invalidateLayoutDeep().");
            }
            this.rblock.invalidateLayoutDeep();
            // this.rblock.invalidateRenderStyle();
          } else {
            final UINode uiNode = node.findUINode();
            if (uiNode != null) {
              final RElement relement = (RElement) uiNode;
              relement.invalidateLayoutUpTree();
              // if(type == DocumentNotification.GENERIC) {
              // relement.invalidateRenderStyle();
              // }
            } else {
              if (loggableInfo) {
                logger.info("processDocumentNotifications(): Unable to find UINode for " + node);
              }
            }
          }
          topLayout = true;
          break;
        }
        case DocumentNotification.POSITION: {
          // TODO: Could be more efficient.
          final NodeImpl node = dn.node;
          final NodeImpl parent = (NodeImpl) node.getParentNode();
          if (parent != null) {
            final UINode uiNode = parent.findUINode();
            if (uiNode != null) {
              final RElement relement = (RElement) uiNode;
              relement.invalidateLayoutUpTree();
            }
          }
          topLayout = true;
          break;
        }
        case DocumentNotification.LOOK: {
          final NodeImpl node = dn.node;
          final UINode uiNode = node.findUINode();
          if (uiNode != null) {
            if (repainters == null) {
              repainters = new ArrayList<>(1);
            }
            final RElement relement = (RElement) uiNode;
            // relement.invalidateRenderStyle();
            repainters.add(relement);
          }
          break;
        }
        default:
          break;
        }
      }
      if (topLayout) {
        this.revalidatePanel();
      } else {
        if (repainters != null) {
          final Iterator<RElement> i = repainters.iterator();
          while (i.hasNext()) {
            final RElement element = i.next();
            element.repaint();
          }
        }
      }
    } finally {
      this.processingDocumentNotification = false;
    }
  }

  public void addDelayedPair(final DelayedPair pair) {
    // NOP
  }

  public RenderableContainer getParentContainer() {
    return null;
  }

  public Collection<DelayedPair> getDelayedPairs() {
    return null;
  }

  public void clearDelayedPairs() {
  }

  private Set<Component> components;

  private void clearComponents() {
    final Set<Component> c = this.components;
    if (c != null) {
      c.clear();
    }
  }

  public Component addComponent(final Component component) {
    Set<Component> c = this.components;
    if (c == null) {
      c = new HashSet<>();
      this.components = c;
    }
    if (c.add(component)) {
      return component;
    } else {
      return null;
    }
  }

  private void updateGUIComponents() {
    // We use this method, instead of removing all components and
    // adding them back, because removal of components can cause
    // them to lose focus.

    final Set<Component> c = this.components;
    if (c == null) {
      if (this.getComponentCount() != 0) {
        this.removeAll();
      }
    } else {
      // Remove children not in the set.
      final Set<Component> workingSet = new HashSet<>();
      workingSet.addAll(c);
      int count = this.getComponentCount();
      for (int i = 0; i < count;) {
        final Component component = this.getComponent(i);
        if (!c.contains(component)) {
          this.remove(i);
          count = this.getComponentCount();
        } else {
          i++;
          workingSet.remove(component);
        }
      }
      // Add components in set that were not previously children.
      final Iterator<Component> wsi = workingSet.iterator();
      while (wsi.hasNext()) {
        final Component component = wsi.next();
        this.add(component);
      }
    }
  }

  public Insets getDefaultMarginInsets() {
    return defaultMarginInsets;
  }

  /**
   * Sets the default margin insets. Note that in the root block, the margin
   * behaves like padding.
   *
   * @param defaultMarginInsets
   *          The default margin insets.
   */
  public void setDefaultMarginInsets(final Insets defaultMarginInsets) {
    if (!Objects.equals(this.defaultMarginInsets, defaultMarginInsets)) {
      this.defaultMarginInsets = defaultMarginInsets;
      final RBlock block = this.rblock;
      if (block != null) {
        block.setDefaultMarginInsets(defaultMarginInsets);
        block.relayoutIfValid();
      }
    }
  }

  // public Insets getDefaultPaddingInsets() {
  // return defaultPaddingInsets;
  // }
  //
  public int getDefaultOverflowX() {
    return defaultOverflowX;
  }

  public void setDefaultOverflowX(final int defaultOverflowX) {
    if (defaultOverflowX != this.defaultOverflowX) {
      this.defaultOverflowX = defaultOverflowX;
      final RBlock block = this.rblock;
      if (block != null) {
        block.setDefaultOverflowX(defaultOverflowX);
        block.relayoutIfValid();
      }
    }
  }

  public int getDefaultOverflowY() {
    return defaultOverflowY;
  }

  public void setDefaultOverflowY(final int defaultOverflowY) {
    if (this.defaultOverflowY != defaultOverflowY) {
      this.defaultOverflowY = defaultOverflowY;
      final RBlock block = this.rblock;
      if (block != null) {
        block.setDefaultOverflowY(defaultOverflowY);
        block.relayoutIfValid();
      }
    }
  }

  @Override
  public Insets getInsets(final boolean hscroll, final boolean vscroll) {
    throw new UnsupportedOperationException(
        "Method added while implementing absolute positioned elements inside relative elements. But not implemented yet.");
  }
}