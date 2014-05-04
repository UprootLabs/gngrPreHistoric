/*
    GNU GENERAL PUBLIC LICENSE
    Copyright (C) 2006 The Lobo Project

    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public
    License as published by the Free Software Foundation; either
    version 2 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    General Public License for more details.

    You should have received a copy of the GNU General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Contact info: lobochief@users.sourceforge.net
 */
package org.lobobrowser.primary.clientlets.html;

import java.awt.Image;
import java.io.IOException;
import java.net.URL;

import org.lobobrowser.html.HttpRequest;
import org.lobobrowser.html.ReadyStateChangeListener;
import org.lobobrowser.ua.NetworkRequest;
import org.lobobrowser.ua.NetworkRequestEvent;
import org.lobobrowser.ua.NetworkRequestListener;
import org.lobobrowser.clientlet.*;
import org.w3c.dom.Document;

public class HttpRequestImpl implements HttpRequest {
  private final NetworkRequest networkRequest;

  public HttpRequestImpl(final NetworkRequest networkRequest) {
    super();
    this.networkRequest = networkRequest;
  }

  public void abort() {
    networkRequest.abort();
  }

  public String getAllResponseHeaders() {
    return networkRequest.getAllResponseHeaders();
  }

  public int getReadyState() {
    return networkRequest.getReadyState();
  }

  public byte[] getResponseBytes() {
    return networkRequest.getResponseBytes();
  }

  public String getResponseHeader(final String headerName) {
    return networkRequest.getResponseHeader(headerName);
  }

  public Image getResponseImage() {
    return networkRequest.getResponseImage();
  }

  public String getResponseText() {
    return networkRequest.getResponseText();
  }

  public Document getResponseXML() {
    return networkRequest.getResponseXML();
  }

  public int getStatus() {
    return networkRequest.getStatus();
  }

  public String getStatusText() {
    return networkRequest.getStatusText();
  }

  public void open(final String method, final String url, final boolean asyncFlag) throws IOException {
    networkRequest.open(method, url, asyncFlag);
  }

  public void open(final String method, final String url) throws IOException {
    networkRequest.open(method, url);
  }

  public void open(final String method, final URL url, final boolean asyncFlag, final String userName, final String password) throws IOException {
    networkRequest.open(method, url, asyncFlag, userName, password);
  }

  public void open(final String method, final URL url, final boolean asyncFlag, final String userName) throws IOException {
    networkRequest.open(method, url, asyncFlag, userName);
  }

  public void open(final String method, final URL url, final boolean asyncFlag) throws IOException {
    networkRequest.open(method, url, asyncFlag);
  }

  public void open(final String method, final URL url) throws IOException {
    networkRequest.open(method, url);
  }

  public void send(final String content) throws IOException {
    networkRequest.send(content);
  }

  public void addReadyStateChangeListener(final ReadyStateChangeListener listener) {
    networkRequest.addNetworkRequestListener(new NetworkRequestListener() {
      public void readyStateChanged(final NetworkRequestEvent event) {
        listener.readyStateChanged();
      }
    });
  }
}
