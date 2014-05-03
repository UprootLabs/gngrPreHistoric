package org.lobobrowser.html.domimpl;

import java.util.ArrayList;

import org.lobobrowser.html.FormInput;
import org.w3c.dom.DOMException;
import org.w3c.dom.html2.HTMLElement;
import org.w3c.dom.html2.HTMLOptionsCollection;
import org.w3c.dom.html2.HTMLSelectElement;

import org.mozilla.javascript.Function;

public class HTMLSelectElementImpl extends HTMLBaseInputElement implements HTMLSelectElement {
  public HTMLSelectElementImpl(final String name) {
    super(name);
  }

  public void add(final HTMLElement element, final HTMLElement before) throws DOMException {
    this.insertBefore(element, before);
  }

  public int getLength() {
    return this.getOptions().getLength();
  }

  private Boolean multipleState = null;

  public boolean getMultiple() {
    final Boolean m = this.multipleState;
    if (m != null) {
      return m.booleanValue();
    }
    return this.getAttributeAsBoolean("multiple");
  }

  private HTMLOptionsCollection options;

  public HTMLOptionsCollection getOptions() {
    synchronized (this) {
      if (this.options == null) {
        this.options = new HTMLOptionsCollectionImpl(this);
      }
      return this.options;
    }
  }

  public int getSelectedIndex() {
    final InputContext ic = this.inputContext;
    if (ic != null) {
      return ic.getSelectedIndex();
    } else {
      return this.deferredSelectedIndex;
    }
  }

  public int getSize() {
    final InputContext ic = this.inputContext;
    if (ic != null) {
      return ic.getVisibleSize();
    } else {
      return 0;
    }
  }

  public String getType() {
    return this.getMultiple() ? "select-multiple" : "select-one";
  }

  public void remove(final int index) {
    try {
      this.removeChild(this.getOptions().item(index));
    } catch (final DOMException de) {
      this.warn("remove(): Unable to remove option at index " + index + ".", de);
    }
  }

  public void setLength(final int length) throws DOMException {
    this.getOptions().setLength(length);
  }

  public void setMultiple(final boolean multiple) {
    final boolean prevMultiple = this.getMultiple();
    this.multipleState = Boolean.valueOf(multiple);
    if (prevMultiple != multiple) {
      this.informLayoutInvalid();
    }
  }

  private int deferredSelectedIndex = -1;

  public void setSelectedIndex(final int selectedIndex) {
    this.setSelectedIndexImpl(selectedIndex);
    final HTMLOptionsCollection options = this.getOptions();
    final int length = options.getLength();
    for (int i = 0; i < length; i++) {
      final HTMLOptionElementImpl option = (HTMLOptionElementImpl) options.item(i);
      option.setSelectedImpl(i == selectedIndex);
    }
  }

  void setSelectedIndexImpl(final int selectedIndex) {
    final InputContext ic = this.inputContext;
    if (ic != null) {
      ic.setSelectedIndex(selectedIndex);
    } else {
      this.deferredSelectedIndex = selectedIndex;
    }
  }

  public void setSize(final int size) {
    final InputContext ic = this.inputContext;
    if (ic != null) {
      ic.setVisibleSize(size);
    }
  }

  protected FormInput[] getFormInputs() {
    // Needs to be overriden for forms to submit.
    final InputContext ic = this.inputContext;
    String[] values = ic == null ? null : ic.getValues();
    if (values == null) {
      final String value = this.getValue();
      values = value == null ? null : new String[] { value };
      if (values == null) {
        return null;
      }
    }
    final String name = this.getName();
    if (name == null) {
      return null;
    }
    final ArrayList<FormInput> formInputs = new ArrayList<FormInput>();
    for (int i = 0; i < values.length; i++) {
      formInputs.add(new FormInput(name, values[i]));
    }
    return formInputs.toArray(FormInput.EMPTY_ARRAY);
  }

  public void resetInput() {
    final InputContext ic = this.inputContext;
    if (ic != null) {
      ic.resetInput();
    }
  }

  public void setInputContext(final InputContext ic) {
    super.setInputContext(ic);
    if (ic != null) {
      ic.setSelectedIndex(this.deferredSelectedIndex);
    }
  }

  private Function onchange;

  public Function getOnchange() {
    return this.getEventFunction(this.onchange, "onchange");
  }

  public void setOnchange(final Function value) {
    this.onchange = value;
  }
}
