package org.lobobrowser.protocol.data;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Base64;
import java.util.HashMap;

/**
 * http://www.ietf.org/rfc/rfc2397.txt
 * 
 * 
 * dataurl := "data:" [ mediatype ] [ ";base64" ] "," data mediatype := [ type
 * "/" subtype ] *( ";" parameter ) data := *urlchar parameter := attribute "="
 * value
 * 
 * 
 * @author toenz
 * 
 */
public class DataURLConnection extends URLConnection {

  private final HashMap<String, String> headerMap = new HashMap<>();
  private byte[] content = new byte[0];

  protected DataURLConnection(final URL url) {
    super(url);
    loadHeaderMap();
  }

  @Override
  public void connect() throws IOException {
  }

  private void loadHeaderMap() {
    final String UTF8 = "UTF-8";
    this.headerMap.clear();
    final String path = getURL().getPath();
    int index2 = path.indexOf(",");
    if (index2 == -1) {
      index2 = path.lastIndexOf(";");
    }
    final String mediatype = path.substring(0, index2).trim();
    boolean base64 = false;
    final String[] split = mediatype.split("[;,]");
    String value = path.substring(index2 + 1).trim();
    if (split[0].equals("")) {
      split[0] = "text/plain";
    }

    this.headerMap.put("content-type", split[0]);

    try {
      for (int i = 1; i < split.length; i++) {
        if (split[i].contains("=")) {
          final int index = split[i].indexOf("=");
          final String attr = split[i].substring(0, index);
          final String v = split[i].substring(index + 1);
          this.headerMap.put(attr, java.net.URLDecoder.decode(v, UTF8));
        } else if (split[i].equalsIgnoreCase("base64")) {
          base64 = true;
        }
      }
      String charset = this.getHeaderField("charset");
      if (charset == null) {
        charset = UTF8;
      }
      if (base64) {
        this.content = Base64.getDecoder().decode(value);
      } else {
        value = java.net.URLDecoder.decode(value, charset);
        this.content = value.getBytes();
      }
    } catch (final IOException e) {
      e.printStackTrace();
    }

  }

  @Override
  public int getContentLength() {
    return content.length;
  }

  @Override
  public String getHeaderField(final int n) {
    // TODO: Looks highly inefficient to convert the keyset to array every time!
    return headerMap.get(headerMap.keySet().toArray()[n]);
  }

  @Override
  public String getHeaderField(final String name) {
    return headerMap.get(name);
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return new ByteArrayInputStream(content);
  }

}
