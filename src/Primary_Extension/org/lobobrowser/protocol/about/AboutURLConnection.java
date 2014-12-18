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
/*
 * Created on Mar 14, 2005
 */
package org.lobobrowser.protocol.about;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.TreeSet;

import org.lobobrowser.primary.ext.BookmarkInfo;
import org.lobobrowser.primary.ext.BookmarksHistory;
import org.lobobrowser.primary.ext.HistoryEntry;
import org.lobobrowser.util.Strings;
import org.lobobrowser.util.Timing;

/**
 * @author J. H. S.
 */
public class AboutURLConnection extends URLConnection {
  public AboutURLConnection(final URL url) {
    super(url);
  }

  /*
   * (non-Javadoc)
   *
   * @see java.net.URLConnection#connect()
   */
  @Override
  public void connect() throws IOException {
  }

  /*
   * (non-Javadoc)
   *
   * @see java.net.URLConnection#getContentLength()
   */
  @Override
  public int getContentLength() {
    return -1;
  }

  /*
   * (non-Javadoc)
   *
   * @see java.net.URLConnection#getContentType()
   */
  @Override
  public String getContentType() {
    return "text/html";
  }

  /*
   * (non-Javadoc)
   *
   * @see java.net.URLConnection#getInputStream()
   */
  @Override
  public InputStream getInputStream() throws IOException {
    return new ByteArrayInputStream(this.getURLText(this.getURL()).getBytes("UTF-8"));
  }

  private String getURLText(final java.net.URL url) {
    final String path = url.getPath();
    if ("blank".equalsIgnoreCase(path)) {
      return "";
    } else if ("bookmarks".equalsIgnoreCase(path)) {
      return getBookmarks();
    } else if ("bookmark-search".equalsIgnoreCase(path)) {
      String query = url.getQuery();
      if (query == null) {
        query = "";
      }
      try {
        final String searchQuery = java.net.URLDecoder.decode(query, "UTF-8");
        return this.getBookmarks(searchQuery);
      } catch (final java.io.UnsupportedEncodingException uee) {
        throw new IllegalStateException("not expected", uee);
      }
    } else if ("java-properties".equals(path)) {
      return getSystemProperties();
    } else if ("welcome".equals(path)) {
      return getWelcomeMessage();
    } else {
      return "<p>Unknown about path: " + path + "</p>" +
          "<h3>Known paths are:</h3>" +
          "<ul>" +
          "<li><a href='about:blank'>about:blank</a></li>" +
          "<li><a href='about:bookmarks'>about:bookmarks</a></li>" +
          "<li><a href='about:bookmark-search?term'>about:bookmark-search?term</a></li>" +
          "<li><a href='about:java-properties'>about:java-properties</a></li>" +
          "</ul>";
    }
  }

  private static String getWelcomeMessage() {
    return
    "<div style='max-width:900px;margin:0 auto;text-align:center;'>"
        +
        "<h1>Welcome to gngr</h1>"
        +
        "<p><b>gngr</b> is a browser that cares deeply about privacy.</p>"
        +
        "<p>It is currently a proof-of-concept, and not very stable or secure.</p>"
        +
        "<p>We recommend that you use this version for casual browsing only and follow the project's <a href='https://blog.gngr.info'>blog</a> to stay abreast of changes.</p>"
        +
        "<div style='padding:1em;border:1px solid #bbb;background:#efe'>" +
        "<p>Tip: Checkout the Request Manager button on the right of the URL bar. " +
        "The Request Manager allows you to control which URL requests are allowed on a given webpage.</p>" +
        "<p>By default, cookies, scripts and frames are disabled on all websites. " +
        "You can change these rules as per your preferences.</p>" +
        "</div>" +
        "</div>";
  }

  private static String getSystemProperties() {
    final StringWriter swriter = new StringWriter();
    final PrintWriter writer = new PrintWriter(swriter);
    writer.println("<html>");
    writer.println("<head><title>Java Properties</title></head>");
    writer.println("<body>");
    writer.println("<pre>");
    final Properties properties = System.getProperties();
    properties.list(writer);
    writer.println("</pre>");
    writer.println("</body>");
    writer.println("</html>");
    writer.flush();
    return swriter.toString();
  }

  private static String getBookmarks() {
    final BookmarksHistory history = BookmarksHistory.getInstance();
    return getBookmarks(history.getAllEntries());
  }

  private String getBookmarks(final String searchQuery) {
    // This is more of a scan. Not efficient but it does the
    // job for now considering the number of entries is limited.
    final String[] keywords = Strings.split(searchQuery);
    final BookmarksHistory history = BookmarksHistory.getInstance();
    final Collection<HistoryEntry<BookmarkInfo>> entries = history.getAllEntries();
    final Collection<ScoredEntry> sortedEntries = new TreeSet<>();
    for (final HistoryEntry<BookmarkInfo> entry : entries) {
      final int matchScore = getMatchScore(entry.getItemInfo(), keywords);
      if (matchScore > 0) {
        sortedEntries.add(new ScoredEntry(entry, matchScore));
      }
    }
    final Collection<HistoryEntry<BookmarkInfo>> finalEntries = new ArrayList<>();
    for (final ScoredEntry scoredEntry : sortedEntries) {
      finalEntries.add(scoredEntry.getHistoryEntry());
    }
    return getBookmarks(finalEntries);
  }

  private static int getMatchScore(final BookmarkInfo binfo, final String[] keywords) {
    int total = 0;
    for (final String keyword : keywords) {
      final int score = getMatchScore(binfo, keyword);
      if (score == 0) {
        return 0;
      }
      total += score;
    }
    return total;
  }

  private static int getMatchScore(final BookmarkInfo binfo, final String keyword) {
    final String keywordTL = keyword.toLowerCase();
    int score = 0;
    final String urlText = binfo.getUrl().toExternalForm();
    if (urlText.contains(keyword)) {
      score += 3;
    } else if (urlText.toLowerCase().contains(keywordTL)) {
      score += 2;
    }
    final String title = binfo.getTitle();
    if ((title != null) && title.contains(keyword)) {
      score += 8;
    } else if ((title != null) && title.toLowerCase().contains(keywordTL)) {
      score += 6;
    }
    final String description = binfo.getDescription();
    if ((description != null) && description.contains(keyword)) {
      score += 3;
    } else if ((description != null) && description.toLowerCase().contains(keywordTL)) {
      score += 2;
    }
    final String[] tags = binfo.getTags();
    if (tags != null) {
      for (final String tag : tags) {
        if (tag.equals(keyword)) {
          score += 8;
        } else if (tag.toLowerCase().equals(keywordTL)) {
          score += 6;
        }
      }
    }
    return score;
  }

  private static String getBookmarks(final Collection<HistoryEntry<BookmarkInfo>> entries) {
    final StringWriter swriter = new StringWriter();
    final PrintWriter writer = new PrintWriter(swriter);
    writer.println("<html>");
    writer.println("<head>Bookmarks</head>");
    writer.println("<body>");
    if (entries.size() == 0) {
      writer.println("No bookmarks were found.");
    } else {
      writer.println("<h3>Bookmarks</h3>");
      writer.println("<ol>");
      for (final HistoryEntry<BookmarkInfo> entry : entries) {
        writeBookmark(writer, entry);
      }
      writer.println("</ol>");
    }
    writer.println("</body>");
    writer.println("</html>");
    writer.flush();
    return swriter.toString();
  }

  private static void writeBookmark(final PrintWriter writer, final HistoryEntry<BookmarkInfo> entry) {
    final java.net.URL url = entry.getUrl();
    final String urlText = url.toExternalForm();
    final BookmarkInfo binfo = entry.getItemInfo();
    String text = binfo.getTitle();
    if ((text == null) || (text.length() == 0)) {
      text = urlText;
    }
    final long elapsed = System.currentTimeMillis() - entry.getTimetstamp();
    String description = binfo.getDescription();
    if (description == null) {
      description = "";
    }
    writer.println("<LI>");
    writer.println("<DIV>");
    writer.println("<A href='" + urlText + "'>" + text + "</A> (" + Timing.getElapsedText(elapsed) + " ago)");
    writer.println("</DIV>");
    writer.println("<DIV>");
    writer.println(description);
    writer.println("</DIV>");
    writer.println("</LI>");
  }

  private class ScoredEntry implements Comparable<ScoredEntry> {
    private final HistoryEntry<BookmarkInfo> historyEntry;
    private final int score;

    public ScoredEntry(final HistoryEntry<BookmarkInfo> historyEntry, final int score) {
      super();
      this.historyEntry = historyEntry;
      this.score = score;
    }

    public HistoryEntry<BookmarkInfo> getHistoryEntry() {
      return historyEntry;
    }

    public int getScore() {
      return score;
    }

    public int compareTo(final ScoredEntry o) {
      if (this == o) {
        return 0;
      }
      final ScoredEntry other = o;
      int diff = other.score - this.score;
      if (diff != 0) {
        return diff;
      }
      diff = (int) (other.historyEntry.getTimetstamp() - this.historyEntry.getTimetstamp());
      if (diff != 0) {
        return diff;
      }
      diff = System.identityHashCode(other) - System.identityHashCode(this);
      if (diff != 0) {
        return diff;
      } else {
        return System.identityHashCode(other.historyEntry) - System.identityHashCode(this.historyEntry);
      }
    }

    @Override
    public int hashCode() {
      return this.score;
    }
  }
}
