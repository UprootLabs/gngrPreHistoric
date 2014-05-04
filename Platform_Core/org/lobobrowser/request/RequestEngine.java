/*
 GNU GENERAL PUBLIC LICENSE
 Copyright (C) 2006 The Lobo Project

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public
 License as published by the Free Software Foundation; either
 verion 2 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public
 License along with this library; if not, write to the Free Software
 Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

 Contact info: lobochief@users.sourceforge.net
 */
package org.lobobrowser.request;

import java.io.*;
import java.net.*;
import java.util.*;
import java.security.*;

import javax.net.ssl.HttpsURLConnection;

import java.util.logging.*;

import org.lobobrowser.async.AsyncResult;
import org.lobobrowser.async.AsyncResultImpl;
import org.lobobrowser.clientlet.*;
import org.lobobrowser.main.ExtensionManager;
import org.lobobrowser.settings.*;
import org.lobobrowser.store.*;
import org.lobobrowser.ua.*;
import org.lobobrowser.util.*;
import org.lobobrowser.util.io.*;

public final class RequestEngine {
  private static final Logger logger = Logger.getLogger(RequestEngine.class.getName());
  private static final boolean loggerInfo = logger.isLoggable(Level.INFO);

  private final SimpleThreadPool threadPool;
  private final Collection<RequestInfo> processingRequests = new HashSet<RequestInfo>();
  private final CookieStore cookieStore = CookieStore.getInstance();
  private final CacheSettings cacheSettings;
  private final BooleanSettings booleanSettings;
  private final ConnectionSettings connectionSettings;

  private RequestEngine() {
    // Use few threads to avoid excessive parallelism. Note that
    // downloads are not handled by this thread pool.
    this.threadPool = new SimpleThreadPool("RequestEngineThreadPool", 3, 5, 60 * 1000);

    // Security: Private fields that require privileged access to get
    // initialized.
    this.cacheSettings = CacheSettings.getInstance();
    this.connectionSettings = ConnectionSettings.getInstance();
    this.booleanSettings = BooleanSettings.getInstance();
  }

  private static final RequestEngine instance = new RequestEngine();

  public static RequestEngine getInstance() {
    return instance;
  }

  public String getCookie(final java.net.URL url) {
    final Collection cookies = this.cookieStore.getCookies(url.getHost(), url.getPath());
    final StringBuffer cookieText = new StringBuffer();
    final Iterator i = cookies.iterator();
    while (i.hasNext()) {
      final Cookie cookie = (Cookie) i.next();
      cookieText.append(cookie.getName());
      cookieText.append('=');
      cookieText.append(cookie.getValue());
      cookieText.append(';');
    }
    // Note: Return blank if there are no cookies, not null.
    return cookieText.toString();
  }

  public void setCookie(final URL url, final String cookieSpec) {
    this.cookieStore.saveCookie(url, cookieSpec);
  }

  public void setCookie(final String urlHostName, final String cookieSpec) {
    this.cookieStore.saveCookie(urlHostName, cookieSpec);
  }

  public void cancelAllRequests() {
    this.threadPool.cancelAll();
  }

  public void cancelRequest(final RequestHandler rhToDelete) {
    this.threadPool.cancel(new RequestHandlerTask(rhToDelete));
    this.cancelRequestIfRunning(rhToDelete);
  }

  public void cancelRequestIfRunning(final RequestHandler rhToDelete) {
    rhToDelete.cancel();
    final List<RequestInfo> handlersToCancel = new ArrayList<RequestInfo>();
    synchronized (this.processingRequests) {
      final Iterator<RequestInfo> ri = this.processingRequests.iterator();
      while (ri.hasNext()) {
        final RequestInfo rinfo = ri.next();
        if (rinfo.getRequestHandler() == rhToDelete) {
          handlersToCancel.add(rinfo);
        }
      }
    }
    final Iterator<RequestInfo> ri2 = handlersToCancel.iterator();
    while (ri2.hasNext()) {
      final RequestInfo rinfo = ri2.next();
      rinfo.abort();
    }
  }

  public void scheduleRequest(final RequestHandler handler) {
    // Note: Important to create task with current access context if there's
    // a security manager.
    final SecurityManager sm = System.getSecurityManager();
    final AccessControlContext context = sm == null ? null : AccessController.getContext();
    this.threadPool.schedule(new RequestHandlerTask(handler, context));
  }

  private static final String NORMAL_FORM_ENCODING = "application/x-www-form-urlencoded";

  private void postData(final URLConnection connection, final ParameterInfo pinfo, final String altPostData) throws IOException {
    final BooleanSettings boolSettings = this.booleanSettings;
    final String encoding = pinfo.getEncoding();
    if (encoding == null || NORMAL_FORM_ENCODING.equalsIgnoreCase(encoding)) {
      final ByteArrayOutputStream bufOut = new ByteArrayOutputStream();
      if (pinfo != null) {
        final Parameter[] parameters = pinfo.getParameters();
        boolean firstParam = true;
        for (int i = 0; i < parameters.length; i++) {
          final Parameter parameter = parameters[i];
          final String name = parameter.getName();
          final String encName = URLEncoder.encode(name, "UTF-8");
          if (parameter.isText()) {
            if (firstParam) {
              firstParam = false;
            } else {
              bufOut.write((byte) '&');
            }
            final String valueStr = parameter.getTextValue();
            final String encValue = URLEncoder.encode(valueStr, "UTF-8");
            bufOut.write(encName.getBytes("UTF-8"));
            bufOut.write((byte) '=');
            bufOut.write(encValue.getBytes("UTF-8"));
          } else {
            logger.warning("postData(): Ignoring non-textual parameter " + name + " for POST with encoding " + encoding + ".");
          }
        }
      } else {
        // No pinfo provided - check alternative POST data
        if (altPostData != null) {
          bufOut.write(altPostData.getBytes("UTF-8"));
        }
      }
      // Do not add a line break to post content. Some servers
      // can be picky about that (namely, java.net).
      final byte[] postContent = bufOut.toByteArray();
      if (loggerInfo) {
        logger.info("postData(): Will post: " + new String(postContent));
      }
      if (connection instanceof HttpURLConnection) {
        if (boolSettings.isHttpUseChunkedEncodingPOST()) {
          ((HttpURLConnection) connection).setChunkedStreamingMode(8192);
        } else {
          ((HttpURLConnection) connection).setFixedLengthStreamingMode(postContent.length);
        }
      }
      connection.setRequestProperty("Content-Type", NORMAL_FORM_ENCODING);
      // connection.setRequestProperty("Content-Length",
      // String.valueOf(postContent.length));
      final OutputStream postOut = connection.getOutputStream();
      postOut.write(postContent);
      postOut.flush();
    } else if ("multipart/form-data".equalsIgnoreCase(encoding)) {
      final long id = ID.generateLong();
      final String boundary = "----------------" + id;
      final boolean chunked = boolSettings.isHttpUseChunkedEncodingPOST();
      OutputStream mfstream;
      if (chunked) {
        mfstream = connection.getOutputStream();
      } else {
        mfstream = new ByteArrayOutputStream();
      }
      final MultipartFormDataWriter writer = new MultipartFormDataWriter(mfstream, boundary);
      try {
        if (pinfo != null) {
          final Parameter[] parameters = pinfo.getParameters();
          for (int i = 0; i < parameters.length; i++) {
            final Parameter parameter = parameters[i];
            final String name = parameter.getName();
            if (parameter.isText()) {
              writer.writeText(name, parameter.getTextValue(), "UTF-8");
            } else if (parameter.isFile()) {
              final File file = parameter.getFileValue();
              final FileInputStream in = new FileInputStream(parameter.getFileValue());
              try {
                final BufferedInputStream bin = new BufferedInputStream(in, 8192);
                writer.writeFileData(name, file.getName(), Files.getContentType(file), bin);
              } finally {
                in.close();
              }
            } else {
              logger.warning("postData(): Skipping parameter " + name + " of unknown type for POST with encoding " + encoding + ".");
            }
          }
        }
      } finally {
        writer.send();
      }
      connection.addRequestProperty("Content-Type", encoding + "; boundary=" + boundary);
      if (chunked) {
        if (connection instanceof HttpURLConnection) {
          ((HttpURLConnection) connection).setChunkedStreamingMode(8192);
        }
      } else {
        final byte[] content = ((ByteArrayOutputStream) mfstream).toByteArray();
        if (connection instanceof HttpURLConnection) {
          ((HttpURLConnection) connection).setFixedLengthStreamingMode(content.length);
        }
        final OutputStream out = connection.getOutputStream();
        out.write(content);
      }
    } else {
      throw new IllegalArgumentException("Unknown encoding: " + encoding);
    }
  }

  private String completeGetUrl(final String baseURL, final ParameterInfo pinfo, final String ref) throws Exception {
    String newNoRefURL;
    final Parameter[] parameters = pinfo.getParameters();
    if (parameters != null && parameters.length > 0) {
      final StringBuffer sb = new StringBuffer(baseURL);
      final int qmIdx = baseURL.indexOf('?');
      char separator = qmIdx == -1 ? '?' : '&';
      for (int i = 0; i < parameters.length; i++) {
        if (parameters[i].isText()) {
          sb.append(separator);
          sb.append(parameters[i].getName());
          sb.append('=');
          final String paramText = parameters[i].getTextValue();
          sb.append(URLEncoder.encode(paramText, "UTF-8"));
          separator = '&';
        } else {
          logger.warning("completeGetUrl(): Skipping non-textual parameter " + parameters[i].getName() + " in GET request.");
        }
      }
      newNoRefURL = sb.toString();
    } else {
      newNoRefURL = baseURL;
    }
    if (ref != null && ref.length() != 0) {
      return newNoRefURL + "#" + ref;
    } else {
      return newNoRefURL;
    }
  }

  private void addRequestProperties(final URLConnection connection, final ClientletRequest request, final CacheInfo cacheInfo, final String requestMethod,
      final URL lastRequestURL) throws ProtocolException {
    final UserAgent userAgent = request.getUserAgent();
    connection.addRequestProperty("User-Agent", userAgent.toString());
    connection.addRequestProperty("X-Java-Version", userAgent.getJavaVersion());
    // TODO: Commenting out X-Session-ID. Needs to be privately generated
    // or available with the right permissions only. Extensions should not
    // have access to the private field. This is not doable if extensions
    // should have a permission to open up access to any member.
    //
    // connection.addRequestProperty("X-Session-ID",
    // userAgent.getSessionID(connection.getURL()));
    final String referrer = request.getReferrer();
    if (referrer != null) {
      connection.addRequestProperty("Referer", referrer);
    }
    if (cacheInfo != null) {
      final String date = cacheInfo.getDateAsText();
      if (date != null) {
        connection.addRequestProperty("If-Modified-Since", date);
      }
    }
    if (connection instanceof HttpURLConnection) {
      final HttpURLConnection hconnection = (HttpURLConnection) connection;
      hconnection.setRequestMethod(requestMethod);
    }
    final Header[] headers = request.getExtraHeaders();
    if (headers != null) {
      for (int i = 0; i < headers.length; i++) {
        final String headerName = headers[i].getName();
        if (headerName.startsWith("X-")) {
          connection.addRequestProperty(headerName, headers[i].getValue());
        } else {
          logger.warning("run(): Ignoring request header: " + headerName);
        }
      }
    }
  }

  private CacheInfo getCacheInfo(final RequestHandler rhandler, final URL url) throws Exception {
    return AccessController.doPrivileged(new PrivilegedAction<CacheInfo>() {
      // Reason: Caller in context may not have privilege to access
      // the local file system, yet it's necessary to be able to load
      // a cache file.
      public CacheInfo run() {
        MemoryCacheEntry entry;
        byte[] persistentContent = null;
        final CacheManager cm = CacheManager.getInstance();
        entry = (MemoryCacheEntry) cm.getTransient(url);
        if (entry == null) {
          if (!"file".equalsIgnoreCase(url.getProtocol()) || !Strings.isBlank(url.getHost())) {
            try {
              persistentContent = cm.getPersistent(url, false);
            } catch (final java.io.IOException ioe) {
              logger.log(Level.WARNING, "getCacheInfo(): Unable to load cache file.", ioe);
            }
          }
        }
        if (persistentContent == null && entry == null) {
          return null;
        }
        final CacheInfo cinfo = new CacheInfo(entry, persistentContent, url);
        return cinfo;
      }
    });
  }

  private void cache(final RequestHandler rhandler, final java.net.URL url, final URLConnection connection, final byte[] content,
      final java.io.Serializable altPersistentObject, final Object altObject, final int approxAltObjectSize) {
    AccessController.doPrivileged(new PrivilegedAction<Object>() {
      // Reason: Caller might not have permission to access the
      // file system. Yet, caching should be allowed.
      public Object run() {
        try {
          final long currentTime = System.currentTimeMillis();
          if (loggerInfo) {
            logger.info("cache(): url=" + url + ",content.length=" + content.length + ",currentTime=" + currentTime);
          }
          int actualApproxObjectSize = 0;
          if (altObject != null) {
            if (approxAltObjectSize < content.length) {
              actualApproxObjectSize = content.length;
            } else {
              actualApproxObjectSize = approxAltObjectSize;
            }
          }
          final Long expiration = Urls.getExpiration(connection, currentTime);
          final List<NameValuePair> headers = Urls.getHeaders(connection);
          final MemoryCacheEntry memEntry = new MemoryCacheEntry(content, headers, expiration, altObject, actualApproxObjectSize);
          final int approxMemEntrySize = content.length + (altObject == null ? 0 : approxAltObjectSize);
          final CacheManager cm = CacheManager.getInstance();
          cm.putTransient(url, memEntry, approxMemEntrySize);
          final ByteArrayOutputStream out = new ByteArrayOutputStream();
          try {
            boolean hadDate = false;
            boolean hadContentLength = false;
            for (int counter = 0; true; counter++) {
              final String headerKey = connection.getHeaderFieldKey(counter);
              if (headerKey != null) {
                if (!hadDate && "date".equalsIgnoreCase(headerKey)) {
                  hadDate = true;
                }
                if (!hadContentLength && "content-length".equalsIgnoreCase(headerKey)) {
                  hadContentLength = true;
                }
              }
              final String headerValue = connection.getHeaderField(counter);
              if (headerValue == null) {
                break;
              }
              if (CacheInfo.HEADER_REQUEST_TIME.equalsIgnoreCase(headerKey)) {
                continue;
              }
              final String headerPrefix = headerKey == null || headerKey.length() == 0 ? "" : headerKey + ": ";
              final byte[] headerBytes = (headerPrefix + headerValue + "\r\n").getBytes("ISO-8859-1");
              out.write(headerBytes);
            }
            if (!hadDate) {
              final String currentDate = Urls.PATTERN_RFC1123.format(new java.util.Date());
              final byte[] headerBytes = ("Date: " + currentDate + "\r\n").getBytes("ISO-8859-1");
              out.write(headerBytes);
            }
            if (!hadContentLength) {
              final byte[] headerBytes = ("Content-Length: " + content.length + "\r\n").getBytes("ISO-8859-1");
              out.write(headerBytes);
            }
            final byte[] rtHeaderBytes = (CacheInfo.HEADER_REQUEST_TIME + ": " + currentTime + "\r\n").getBytes("ISO-8859-1");
            out.write(rtHeaderBytes);
            out.write(IORoutines.LINE_BREAK_BYTES);
            out.write(content);
          } finally {
            out.close();
          }
          try {
            cm.putPersistent(url, out.toByteArray(), false);
          } catch (final Exception err) {
            logger.log(Level.WARNING, "cache(): Unable to cache response content.", err);
          }
          if (altPersistentObject != null) {
            try {
              final ByteArrayOutputStream fileOut = new ByteArrayOutputStream();
              // No need to buffer - Java API already does.
              final ObjectOutputStream objOut = new ObjectOutputStream(fileOut);
              objOut.writeObject(altPersistentObject);
              objOut.flush();
              final byte[] byteArray = fileOut.toByteArray();
              if (byteArray.length == 0) {
                logger.log(Level.WARNING, "cache(): Serialized content has zero bytes for persistent object " + altPersistentObject + ".");
              }
              cm.putPersistent(url, byteArray, true);
            } catch (final Exception err) {
              logger.log(Level.WARNING, "cache(): Unable to write persistent cached object.", err);
            }
          }
        } catch (final Exception err) {
          logger.log(Level.WARNING, "cache()", err);
        }
        return null;
      }
    });
  }

  private boolean mayBeCached(final HttpURLConnection connection) {
    final String cacheControl = connection.getHeaderField("Cache-Control");
    if (cacheControl != null) {
      final StringTokenizer tok = new StringTokenizer(cacheControl, ",");
      while (tok.hasMoreTokens()) {
        final String token = tok.nextToken().trim();
        if ("no-cache".equalsIgnoreCase(token)) {
          return false;
        }
      }
    }
    return true;
  }

  private void printRequestHeaders(final URLConnection connection) {
    final Map<String, List<String>> headers = connection.getRequestProperties();
    final StringBuffer buffer = new StringBuffer();
    for (final Map.Entry<String, List<String>> entry : headers.entrySet()) {
      buffer.append(entry.getKey() + ": " + entry.getValue());
      buffer.append(System.getProperty("line.separator"));
    }
    logger.info("printRequestHeaders(): Request headers for URI=[" + connection.getURL() + "]\r\n" + buffer.toString());
  }

  public void inlineRequest(final RequestHandler rhandler) {
    // Security checked by low-level APIs in this case.
    this.processHandler(rhandler, 0, false);
  }

  public byte[] loadBytes(final String urlOrPath) throws Exception {
    return this.loadBytes(Urls.guessURL(urlOrPath));
  }

  public byte[] loadBytes(final URL url) throws Exception {
    final BoxedObject boxed = new BoxedObject();
    this.inlineRequest(new SimpleRequestHandler(url, RequestType.ELEMENT) {
      @Override
      public boolean handleException(final ClientletResponse response, final Throwable exception) throws ClientletException {
        if (exception instanceof ClientletException) {
          throw (ClientletException) exception;
        } else {
          throw new ClientletException(exception);
        }
      }

      public void processResponse(final ClientletResponse response) throws ClientletException, IOException {
        final byte[] bytes = org.lobobrowser.util.io.IORoutines.load(response.getInputStream(), 4096);
        boxed.setObject(bytes);
      }
    });
    return (byte[]) boxed.getObject();
  }

  public AsyncResult<byte[]> loadBytesAsync(final String urlOrPath) throws java.net.MalformedURLException {
    return this.loadBytesAsync(Urls.guessURL(urlOrPath));
  }

  public AsyncResult<byte[]> loadBytesAsync(final URL url) {
    final AsyncResultImpl<byte[]> asyncResult = new AsyncResultImpl<byte[]>();
    this.scheduleRequest(new SimpleRequestHandler(url, RequestType.ELEMENT) {
      @Override
      public boolean handleException(final ClientletResponse response, final Throwable exception) throws ClientletException {
        asyncResult.setException(exception);
        return true;
      }

      public void processResponse(final ClientletResponse response) throws ClientletException, IOException {
        final byte[] bytes = org.lobobrowser.util.io.IORoutines.load(response.getInputStream(), 4096);
        asyncResult.setResult(bytes);
      }
    });
    return asyncResult;
  }

  /**
   * Whether possibly cached request should always be revalidated, i.e. any
   * expiration information is ignored.
   */
  private static boolean shouldRevalidateAlways(final URL connectionUrl, final RequestType requestType) {
    return requestType == RequestType.ADDRESS_BAR;
  }

  /**
   * Whether the request type should always be obtained from cache if it is
   * there.
   */
  private static boolean doesNotExpire(final RequestType requestType) {
    return requestType == RequestType.HISTORY;
  }

  private ExtensionManager getSafeExtensionManager() {
    return AccessController.doPrivileged(new PrivilegedAction<ExtensionManager>() {
      public ExtensionManager run() {
        return ExtensionManager.getInstance();
      }
    });
  }

  private URLConnection getURLConnection(final URL connectionUrl, final ClientletRequest request, final String protocol, final String method,
      final RequestHandler rhandler, final CacheInfo cacheInfo) throws IOException {
    URLConnection connection;
    if (cacheInfo != null) {
      final RequestType requestType = rhandler.getRequestType();
      if (doesNotExpire(requestType)) {
        if (loggerInfo) {
          if (cacheInfo.hasTransientEntry()) {
            logger.info("getURLConnection(): FROM-RAM: " + connectionUrl + ".");
          } else {
            logger.info("getURLConnection(): FROM-FILE: " + connectionUrl + ".");
          }
        }
        return cacheInfo.getURLConnection();
      } else if (!shouldRevalidateAlways(connectionUrl, requestType)) {
        Long expires = cacheInfo.getExpires();
        if (expires == null) {
          final Integer defaultOffset = this.cacheSettings.getDefaultCacheExpirationOffset();
          if (defaultOffset != null) {
            expires = cacheInfo.getExpiresGivenOffset(defaultOffset.longValue());
            if (loggerInfo) {
              final java.util.Date expiresDate = expires == null ? null : new Date(expires);
              logger.info("getURLConnection(): Used default offset for " + connectionUrl + ": expires=" + expiresDate);
            }
          }
        }
        if (expires != null) {
          if (expires.longValue() > System.currentTimeMillis()) {
            if (loggerInfo) {
              final long secondsToExpiration = (expires.longValue() - System.currentTimeMillis()) / 1000;
              if (cacheInfo.hasTransientEntry()) {
                logger.info("getURLConnection(): FROM-RAM: " + connectionUrl + ". Expires in " + secondsToExpiration + " seconds.");
              } else {
                logger.info("getURLConnection(): FROM-FILE: " + connectionUrl + ". Expires in " + secondsToExpiration + " seconds.");
              }
            }
            return cacheInfo.getURLConnection();
          } else {
            if (loggerInfo) {
              logger.info("getURLConnection(): EXPIRED: " + connectionUrl + ". Expired on " + new Date(expires) + ".");
            }
          }
        }
        // If the document has expired, the cache may still
        // be used, but only after validation.
      }
    }
    final boolean isPost = "POST".equalsIgnoreCase(method);
    final String host = connectionUrl.getHost();
    final boolean isResURL = "res".equalsIgnoreCase(protocol);
    if (isResURL || host == null || host.length() == 0) {
      connection = connectionUrl.openConnection();
    } else {
      final Proxy proxy = this.connectionSettings.getProxy(host);
      if (proxy == Proxy.NO_PROXY) {
        // Workaround for JRE 1.5.0.
        connection = connectionUrl.openConnection();
      } else {
        connection = connectionUrl.openConnection(proxy);
      }
    }
    if (connection instanceof HttpsURLConnection) {
      ((HttpsURLConnection) connection).setHostnameVerifier(rhandler.getHostnameVerifier());
    }
    if (isPost) {
      connection.setDoOutput(true);
    }
    connection.setUseCaches(false);
    if (connection instanceof HttpURLConnection) {
      final HttpURLConnection hconnection = (HttpURLConnection) connection;
      hconnection.setConnectTimeout(60000);
      hconnection.setReadTimeout(90000);
    }
    this.addRequestProperties(connection, request, cacheInfo, method, connectionUrl);
    // Allow extensions to modify the connection object.
    // Doing it after addRequestProperties() to allow such
    // functionality as altering the Accept header.
    connection = this.getSafeExtensionManager().dispatchPreConnection(connection);
    // Print request headers
    if (logger.isLoggable(Level.FINE)) {
      this.printRequestHeaders(connection);
    }
    // POST data if we need to.
    if (isPost) {
      final ParameterInfo pinfo = rhandler instanceof RedirectRequestHandler ? null : request.getParameterInfo();
      if (pinfo == null) {
        throw new IllegalStateException("POST has no parameter information");
      }
      this.postData(connection, pinfo, request.getAltPostData());
    }
    return connection;
  }

  private static boolean isOKToRetrieveFromCache(final RequestType requestType) {
    return false;
    /*
     * return requestType != RequestType.SOFT_RELOAD && requestType !=
     * RequestType.HARD_RELOAD && requestType != RequestType.DOWNLOAD;
     */
  }

  private void processHandler(final RequestHandler rhandler, final int recursionLevel, final boolean trackRequestInfo) {
    // Method must be private.
    final boolean linfo = loggerInfo;
    final URL baseURL = rhandler.getLatestRequestURL();
    RequestInfo rinfo = null;
    ClientletResponseImpl response = null;
    String method = null;
    try {
      final ClientletRequest request = rhandler.getRequest();
      method = rhandler.getLatestRequestMethod().toUpperCase();
      // TODO: Hack: instanceof below
      final ParameterInfo pinfo = rhandler instanceof RedirectRequestHandler ? null : request.getParameterInfo();
      final boolean isGet = "GET".equals(method);
      URL url;
      if (isGet && pinfo != null) {
        final String ref = baseURL.getRef();
        final String noRefForm = Urls.getNoRefForm(baseURL);
        final String newURLText = completeGetUrl(noRefForm, pinfo, ref);
        url = new URL(newURLText);
      } else {
        url = baseURL;
      }
      CacheInfo cacheInfo = null;
      final String protocol = url.getProtocol();
      final URL connectionUrl;
      if (url.getQuery() != null && "file".equalsIgnoreCase(protocol)) {
        // Remove query (replace file with path) if "file" protocol.
        final String ref = url.getRef();
        final String refText = ref == null || ref.length() == 0 ? "" : "#" + ref;
        connectionUrl = new URL(protocol, url.getHost(), url.getPort(), url.getPath() + refText);
      } else {
        connectionUrl = url;
      }
      final RequestType requestType = rhandler.getRequestType();
      if (isGet && isOKToRetrieveFromCache(requestType)) {
        cacheInfo = this.getCacheInfo(rhandler, connectionUrl);
      }
      try {
        URLConnection connection = this.getURLConnection(connectionUrl, request, protocol, method, rhandler, cacheInfo);
        rinfo = new RequestInfo(connection, rhandler);
        InputStream responseIn = null;
        if (trackRequestInfo) {
          synchronized (this.processingRequests) {
            this.processingRequests.add(rinfo);
          }
        }
        try {
          if (rhandler.isCancelled()) {
            throw new CancelClientletException("cancelled");
          }
          rhandler.handleProgress(ProgressType.CONNECTING, url, method, 0, -1);
          // Handle response
          boolean isContentCached = cacheInfo != null && cacheInfo.isCacheConnection(connection);
          boolean isCacheable = false;
          if (connection instanceof HttpURLConnection && !isContentCached) {
            final HttpURLConnection hconnection = (HttpURLConnection) connection;
            hconnection.setInstanceFollowRedirects(false);
            final int responseCode = hconnection.getResponseCode();
            if (linfo) {
              logger.info("run(): ResponseCode=" + responseCode + " for url=" + connectionUrl);
            }
            if (responseCode == HttpURLConnection.HTTP_OK) {
              if (linfo) {
                logger.info("run(): FROM-HTTP: " + connectionUrl);
              }
              if (this.mayBeCached(hconnection)) {
                isCacheable = true;
              } else {
                if (linfo) {
                  logger.info("run(): NOT CACHEABLE: " + connectionUrl);
                }
                if (cacheInfo != null) {
                  cacheInfo.delete();
                }
              }
              responseIn = connection.getInputStream();
              rinfo.setConnection(connection, responseIn);
            } else if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
              if (cacheInfo == null) {
                throw new IllegalStateException("Cache info missing but it is necessary to process response code " + responseCode + ".");
              }
              if (linfo) {
                logger.info("run(): FROM-VALIDATION: " + connectionUrl);
              }
              // Disconnect the HTTP connection.
              hconnection.disconnect();
              isContentCached = true;
              // Even though the response is actually from the cache,
              // we need to cache it again, if only to update the
              // request time (used to calculate default expiration).
              isCacheable = true;
              connection = cacheInfo.getURLConnection();
              responseIn = connection.getInputStream();
              rinfo.setConnection(connection, responseIn);
            } else if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                || responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
              if (linfo) {
                logger.info("run(): REDIRECTING: ResponseCode=" + responseCode + " for url=" + url);
              }
              final RequestHandler newHandler = new RedirectRequestHandler(rhandler, hconnection);
              Thread.yield();
              if (recursionLevel > 5) {
                throw new ClientletException("Exceeded redirect recursion limit.");
              }
              this.processHandler(newHandler, recursionLevel + 1, trackRequestInfo);
              return;
            }
          } else {
            // Force it to throw exception if file does not exist
            responseIn = connection.getInputStream();
            rinfo.setConnection(connection, responseIn);
          }
          if (rinfo.isAborted()) {
            throw new CancelClientletException("Stopped");
          }
          // Give a change to extensions to post-process the connection.
          final URLConnection newConnection = this.getSafeExtensionManager().dispatchPostConnection(connection);
          if (newConnection != connection) {
            responseIn = newConnection.getInputStream();
            connection = newConnection;
          }
          // Create clientlet response.
          response = new ClientletResponseImpl(rhandler, connection, url, isContentCached, cacheInfo, isCacheable,
              rhandler.getRequestType());
          rhandler.processResponse(response);
          if (isCacheable) {
            // Make sure stream reaches EOF so we don't
            // get null stored content.
            response.ensureReachedEOF();
            final byte[] content = response.getStoredContent();
            if (content != null) {
              final Serializable persObject = response.getNewPersistentCachedObject();
              final Object altObject = response.getNewTransientCachedObject();
              final int altObjectSize = response.getNewTransientObjectSize();
              this.cache(rhandler, connectionUrl, connection, content, persObject, altObject, altObjectSize);
            } else {
              logger.warning("processHandler(): Cacheable response not available: " + connectionUrl);
            }
          } else if (cacheInfo != null && !cacheInfo.hasTransientEntry()) {
            // Content that came from cache cannot be cached
            // again, but a RAM entry was missing.
            final byte[] persContent = cacheInfo.getPersistentContent();
            final Object altObject = response.getNewTransientCachedObject();
            final int altObjectSize = response.getNewTransientObjectSize();
            final MemoryCacheEntry newMemEntry = new MemoryCacheEntry(persContent, cacheInfo.getExpires(), cacheInfo.getRequestTime(),
                altObject, altObjectSize);
            final int actualApproxObjectSize;
            if (altObject != null) {
              if (altObjectSize < persContent.length) {
                actualApproxObjectSize = persContent.length;
              } else {
                actualApproxObjectSize = altObjectSize;
              }
            } else {
              actualApproxObjectSize = 0;
            }
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
              // Reason: Privileges needed to access CacheManager.
              public Object run() {
                CacheManager.getInstance().putTransient(connectionUrl, newMemEntry, actualApproxObjectSize + persContent.length);
                return null;
              }
            });
          }
        } finally {
          if (trackRequestInfo) {
            synchronized (this.processingRequests) {
              this.processingRequests.remove(rinfo);
            }
          }
          if (responseIn != null) {
            try {
              responseIn.close();
            } catch (final java.io.IOException ioe) {
              // ignore
            }
          }
          if (connection instanceof HttpURLConnection) {
            ((HttpURLConnection) connection).disconnect();
          }
        }
      } finally {
        if (cacheInfo != null) {
          // This is necessary so that the file stream doesn't
          // stay open potentially.
          cacheInfo.dispose();
        }
      }
    } catch (final CancelClientletException cce) {
      if (linfo) {
        logger.log(Level.INFO, "run(): Clientlet cancelled: " + baseURL, cce);
      }
    } catch (final Throwable exception) {
      if (rinfo != null && rinfo.isAborted()) {
        if (linfo) {
          logger.log(Level.INFO, "run(): Exception ignored because request aborted.", exception);
        }
      } else {
        try {
          if (!rhandler.handleException(response, exception)) {
            logger.log(Level.WARNING, "Was unable to handle exception.", exception);
          }
        } catch (final Exception err) {
          System.out.println("Exception while handling exception:" + exception);
          exception.printStackTrace();
          logger.log(Level.WARNING, "Exception handler threw an exception.", err);
        }
      }
    } finally {
      rhandler.handleProgress(ProgressType.DONE, baseURL, method, 0, 0);
    }
  }

  private static class RequestInfo {
    private final RequestHandler requestHandler;

    private volatile boolean isAborted = false;
    private volatile InputStream inputStream;
    private volatile URLConnection connection;

    RequestInfo(final URLConnection connection, final RequestHandler rhandler) {
      this.connection = connection;
      this.requestHandler = rhandler;
    }

    boolean isAborted() {
      return this.isAborted;
    }

    void abort() {
      try {
        this.isAborted = true;
        if (this.connection instanceof HttpURLConnection) {
          ((HttpURLConnection) this.connection).disconnect();
        }
        final InputStream in = this.inputStream;
        if (in != null) {
          in.close();
        }
      } catch (final Exception err) {
        logger.log(Level.SEVERE, "abort()", err);
      }
    }

    RequestHandler getRequestHandler() {
      return this.requestHandler;
    }

    void setConnection(final URLConnection connection, final InputStream in) {
      this.connection = connection;
      this.inputStream = in;
    }
  }

  private class RequestHandlerTask implements SimpleThreadPoolTask {
    private final RequestHandler handler;

    private final AccessControlContext accessContext;

    private RequestHandlerTask(final RequestHandler handler, final AccessControlContext accessContext) {
      this.handler = handler;
      this.accessContext = accessContext;
    }

    private RequestHandlerTask(final RequestHandler handler) {
      this.handler = handler;
      this.accessContext = null;
    }

    public void run() {
      final SecurityManager sm = System.getSecurityManager();
      if (sm != null && this.accessContext != null) {
        final PrivilegedAction<Object> action = new PrivilegedAction<Object>() {
          public Object run() {
            processHandler(handler, 0, true);
            return null;
          }
        };
        // This way we ensure scheduled requests have the same
        // protection as inline requests, particularly in relation
        // to file and host name checks.
        AccessController.doPrivileged(action, this.accessContext);
      } else {
        processHandler(this.handler, 0, true);
      }
    }

    public void cancel() {
      cancelRequestIfRunning(this.handler);
    }

    public int hashCode() {
      return this.handler.hashCode();
    }

    public boolean equals(final Object other) {
      return other instanceof RequestHandlerTask && ((RequestHandlerTask) other).handler.equals(this.handler);
    }

    public String toString() {
      return "RequestHandlerTask[host=" + this.handler.getLatestRequestURL().getHost() + "]";
    }
  }
}
