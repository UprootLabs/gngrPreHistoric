package org.lobobrowser.html.renderer;

import java.awt.Cursor;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.lobobrowser.html.FormInput;
import org.lobobrowser.html.HtmlRendererContext;
import org.lobobrowser.html.domimpl.HTMLAbstractUIElement;
import org.lobobrowser.html.domimpl.HTMLButtonElementImpl;
import org.lobobrowser.html.domimpl.HTMLInputElementImpl;
import org.lobobrowser.html.domimpl.HTMLLinkElementImpl;
import org.lobobrowser.html.domimpl.HTMLSelectElementImpl;
import org.lobobrowser.html.domimpl.ModelNode;
import org.lobobrowser.html.domimpl.NodeImpl;
import org.lobobrowser.html.js.Event;
import org.lobobrowser.html.js.Executor;
import org.lobobrowser.html.style.RenderState;
import org.mozilla.javascript.Function;

class HtmlController {
  private static final Logger logger = Logger.getLogger(HtmlController.class.getName());
  private static final HtmlController instance = new HtmlController();

  static HtmlController getInstance() {
    return instance;
  }

  /**
   * @return True to propagate further and false if the event was consumed.
   */
  public boolean onEnterPressed(final ModelNode node, final InputEvent event) {
    if (node instanceof HTMLInputElementImpl) {
      final HTMLInputElementImpl hie = (HTMLInputElementImpl) node;
      if (hie.isSubmittableWithEnterKey()) {
        hie.submitForm(null);
        return false;
      }
    }
    // No propagation
    return false;
  }

  /**
   * @return True to propagate further and false if the event was consumed.
   */
  public boolean onMouseClick(final ModelNode node, final MouseEvent event, final int x, final int y) {
    if (logger.isLoggable(Level.INFO)) {
      logger.info("onMouseClick(): node=" + node + ",class=" + node.getClass().getName());
    }
    if (node instanceof HTMLAbstractUIElement) {
      final HTMLAbstractUIElement uiElement = (HTMLAbstractUIElement) node;

      final Event jsEvent = new Event("click", uiElement, event, x, y);
      uiElement.dispatchEvent(jsEvent);

      final Function f = uiElement.getOnclick();
      if (f != null) {
        if (!Executor.executeFunction(uiElement, f, jsEvent)) {
          return false;
        }
      }
      final HtmlRendererContext rcontext = uiElement.getHtmlRendererContext();
      if (rcontext != null) {
        if (!rcontext.onMouseClick(uiElement, event)) {
          return false;
        }
      }
    }
    if (node instanceof HTMLLinkElementImpl) {
      ((HTMLLinkElementImpl) node).navigate();
      return false;
    } else if (node instanceof HTMLButtonElementImpl) {
      final HTMLButtonElementImpl button = (HTMLButtonElementImpl) node;
      final String rawType = button.getAttribute("type");
      String type;
      if (rawType == null) {
        type = "submit";
      } else {
        type = rawType.trim().toLowerCase();
      }
      if ("submit".equals(type)) {
        FormInput[] formInputs;
        final String name = button.getName();
        if (name == null) {
          formInputs = null;
        } else {
          formInputs = new FormInput[] { new FormInput(name, button.getValue()) };
        }
        button.submitForm(formInputs);
      } else if ("reset".equals(type)) {
        button.resetForm();
      } else {
        // NOP for "button"!
      }
      return false;
    }
    final ModelNode parent = node.getParentModelNode();
    if (parent == null) {
      return true;
    }
    return this.onMouseClick(parent, event, x, y);
  }

  public boolean onContextMenu(final ModelNode node, final MouseEvent event, final int x, final int y) {
    if (logger.isLoggable(Level.INFO)) {
      logger.info("onContextMenu(): node=" + node + ",class=" + node.getClass().getName());
    }
    if (node instanceof HTMLAbstractUIElement) {
      final HTMLAbstractUIElement uiElement = (HTMLAbstractUIElement) node;
      final Function f = uiElement.getOncontextmenu();
      if (f != null) {
        final Event jsEvent = new Event("contextmenu", uiElement, event, x, y);
        if (!Executor.executeFunction(uiElement, f, jsEvent)) {
          return false;
        }
      }
      final HtmlRendererContext rcontext = uiElement.getHtmlRendererContext();
      if (rcontext != null) {
        // Needs to be done after Javascript, so the script
        // is able to prevent it.
        if (!rcontext.onContextMenu(uiElement, event)) {
          return false;
        }
      }
    }
    final ModelNode parent = node.getParentModelNode();
    if (parent == null) {
      return true;
    }
    return this.onContextMenu(parent, event, x, y);
  }

  public void onMouseOver(final BaseBoundableRenderable renderable, final ModelNode nodeStart, final MouseEvent event, final int x,
      final int y, final ModelNode limit) {
    {
      ModelNode node = nodeStart;
      while (node != null) {
        if (node == limit) {
          break;
        }
        if (node instanceof HTMLAbstractUIElement) {
          final HTMLAbstractUIElement uiElement = (HTMLAbstractUIElement) node;
          uiElement.setMouseOver(true);
          final Function f = uiElement.getOnmouseover();
          if (f != null) {
            final Event jsEvent = new Event("mouseover", uiElement, event, x, y);
            Executor.executeFunction(uiElement, f, jsEvent);
          }
          final HtmlRendererContext rcontext = uiElement.getHtmlRendererContext();
          if (rcontext != null) {
            rcontext.onMouseOver(uiElement, event);
          }
        }
        node = node.getParentModelNode();
      }
    }

    setMouseOnMouseOver(renderable, nodeStart, limit);
  }

  private static void setMouseOnMouseOver(final BaseBoundableRenderable renderable, final ModelNode nodeStart, final ModelNode limit) {
    {
      ModelNode node = nodeStart;
      while (node != null) {
        if (node == limit) {
          break;
        }
        if (node instanceof NodeImpl) {
          final NodeImpl uiElement = (NodeImpl) node;
          final HtmlRendererContext rcontext = uiElement.getHtmlRendererContext();
          final RenderState rs = uiElement.getRenderState();
          final Optional<Cursor> cursorOpt = rs.getCursor();
          if (cursorOpt.isPresent()) {
            rcontext.setCursor(cursorOpt);
            break;
          } else {
            if (node.getParentModelNode() == limit) {
              if ((renderable instanceof RWord) || (renderable instanceof RBlank)) {
                rcontext.setCursor(Optional.of(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)));
              }
            }
          }
        }
        node = node.getParentModelNode();
      }

    }
  }

  public void onMouseOut(final ModelNode nodeStart, final MouseEvent event, final int x, final int y, final ModelNode limit) {
    {
      ModelNode node = nodeStart;
      while (node != null) {
        if (node == limit) {
          break;
        }
        if (node instanceof HTMLAbstractUIElement) {
          final HTMLAbstractUIElement uiElement = (HTMLAbstractUIElement) node;
          uiElement.setMouseOver(false);
          final Function f = uiElement.getOnmouseout();
          if (f != null) {
            final Event jsEvent = new Event("mouseout", uiElement, event, x, y);
            Executor.executeFunction(uiElement, f, jsEvent);
          }
          final HtmlRendererContext rcontext = uiElement.getHtmlRendererContext();
          if (rcontext != null) {
            rcontext.onMouseOut(uiElement, event);
          }

        }
        node = node.getParentModelNode();
      }
    }

    resetCursorOnMouseOut(nodeStart, limit);
  }

  private static void resetCursorOnMouseOut(final ModelNode nodeStart, final ModelNode limit) {
    Optional<Cursor> foundCursorOpt = Optional.empty();
    ModelNode node = limit;
    while (node != null) {
      if (node instanceof NodeImpl) {
        final NodeImpl uiElement = (NodeImpl) node;

        final RenderState rs = uiElement.getRenderState();
        final Optional<Cursor> cursorOpt = rs.getCursor();
        foundCursorOpt = cursorOpt;
        if (cursorOpt.isPresent()) {
          break;
        }

      }
      node = node.getParentModelNode();
    }

    if (nodeStart instanceof NodeImpl) {
      final NodeImpl uiElement = (NodeImpl) nodeStart;
      final HtmlRendererContext rcontext = uiElement.getHtmlRendererContext();
      // rcontext.setCursor(Optional.empty());
      rcontext.setCursor(foundCursorOpt);
    }
  }

  /**
   * @return True to propagate further, false if consumed.
   */
  public boolean onDoubleClick(final ModelNode node, final MouseEvent event, final int x, final int y) {
    if (logger.isLoggable(Level.INFO)) {
      logger.info("onDoubleClick(): node=" + node + ",class=" + node.getClass().getName());
    }
    if (node instanceof HTMLAbstractUIElement) {
      final HTMLAbstractUIElement uiElement = (HTMLAbstractUIElement) node;
      final Function f = uiElement.getOndblclick();
      if (f != null) {
        final Event jsEvent = new Event("dblclick", uiElement, event, x, y);
        if (!Executor.executeFunction(uiElement, f, jsEvent)) {
          return false;
        }
      }
      final HtmlRendererContext rcontext = uiElement.getHtmlRendererContext();
      if (rcontext != null) {
        if (!rcontext.onDoubleClick(uiElement, event)) {
          return false;
        }
      }
    }
    final ModelNode parent = node.getParentModelNode();
    if (parent == null) {
      return true;
    }
    return this.onDoubleClick(parent, event, x, y);
  }

  /**
   * @return True to propagate further, false if consumed.
   */
  public boolean onMouseDisarmed(final ModelNode node, final MouseEvent event) {
    if (node instanceof HTMLLinkElementImpl) {
      ((HTMLLinkElementImpl) node).getCurrentStyle().setOverlayColor(null);
      return false;
    }
    final ModelNode parent = node.getParentModelNode();
    if (parent == null) {
      return true;
    }
    return this.onMouseDisarmed(parent, event);
  }

  /**
   * @return True to propagate further, false if consumed.
   */
  public boolean onMouseDown(final ModelNode node, final MouseEvent event, final int x, final int y) {
    boolean pass = true;
    if (node instanceof HTMLAbstractUIElement) {
      final HTMLAbstractUIElement uiElement = (HTMLAbstractUIElement) node;
      final Function f = uiElement.getOnmousedown();
      if (f != null) {
        final Event jsEvent = new Event("mousedown", uiElement, event, x, y);
        pass = Executor.executeFunction(uiElement, f, jsEvent);
      }
    }
    if (node instanceof HTMLLinkElementImpl) {
      ((HTMLLinkElementImpl) node).getCurrentStyle().setOverlayColor("#9090FF80");
      return false;
    }
    if (!pass) {
      return false;
    }
    final ModelNode parent = node.getParentModelNode();
    if (parent == null) {
      return true;
    }
    return this.onMouseDown(parent, event, x, y);
  }

  /**
   * @return True to propagate further, false if consumed.
   */
  public boolean onMouseUp(final ModelNode node, final MouseEvent event, final int x, final int y) {
    boolean pass = true;
    if (node instanceof HTMLAbstractUIElement) {
      final HTMLAbstractUIElement uiElement = (HTMLAbstractUIElement) node;
      final Function f = uiElement.getOnmouseup();
      if (f != null) {
        final Event jsEvent = new Event("mouseup", uiElement, event, x, y);
        pass = Executor.executeFunction(uiElement, f, jsEvent);
      }
    }
    if (node instanceof HTMLLinkElementImpl) {
      ((HTMLLinkElementImpl) node).getCurrentStyle().setOverlayColor(null);
      return false;
    }
    if (!pass) {
      return false;
    }
    final ModelNode parent = node.getParentModelNode();
    if (parent == null) {
      return true;
    }
    return this.onMouseUp(parent, event, x, y);
  }

  /**
   * @param node
   *          The node generating the event.
   * @param x
   *          For images only, x coordinate of mouse click.
   * @param y
   *          For images only, y coordinate of mouse click.
   * @return True to propagate further, false if consumed.
   */
  public boolean onPressed(final ModelNode node, final InputEvent event, final int x, final int y) {
    if (node instanceof HTMLAbstractUIElement) {
      final HTMLAbstractUIElement uiElement = (HTMLAbstractUIElement) node;
      final Function f = uiElement.getOnclick();
      if (f != null) {
        final Event jsEvent = new Event("click", uiElement, event, x, y);
        if (!Executor.executeFunction(uiElement, f, jsEvent)) {
          return false;
        }
      }
    }
    if (node instanceof HTMLInputElementImpl) {
      final HTMLInputElementImpl hie = (HTMLInputElementImpl) node;
      if (hie.isSubmitInput()) {
        FormInput[] formInputs;
        final String name = hie.getName();
        if (name == null) {
          formInputs = null;
        } else {
          formInputs = new FormInput[] { new FormInput(name, hie.getValue()) };
        }
        hie.submitForm(formInputs);
      } else if (hie.isImageInput()) {
        final String name = hie.getName();
        final String prefix = name == null ? "" : name + ".";
        final FormInput[] extraFormInputs = new FormInput[] { new FormInput(prefix + "x", String.valueOf(x)),
            new FormInput(prefix + "y", String.valueOf(y)) };
        hie.submitForm(extraFormInputs);
      } else if (hie.isResetInput()) {
        hie.resetForm();
      }
    }
    // No propagate
    return false;
  }

  public boolean onChange(final ModelNode node) {
    if (node instanceof HTMLSelectElementImpl) {
      final HTMLSelectElementImpl uiElement = (HTMLSelectElementImpl) node;
      final Function f = uiElement.getOnchange();
      if (f != null) {
        final Event jsEvent = new Event("change", uiElement);
        if (!Executor.executeFunction(uiElement, f, jsEvent)) {
          return false;
        }
      }
    }
    // No propagate
    return false;
  }
}
