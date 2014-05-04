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

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import org.lobobrowser.primary.ext.*;
import org.lobobrowser.util.*;

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
  public void connect() throws IOException {
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.net.URLConnection#getContentLength()
   */
  public int getContentLength() {
    return -1;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.net.URLConnection#getContentType()
   */
  public String getContentType() {
    return "text/html";
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.net.URLConnection#getInputStream()
   */
  public InputStream getInputStream() throws IOException {
    return new ByteArrayInputStream(this.getURLText(this.getURL()).getBytes("UTF-8"));
  }

  private String getURLText(final java.net.URL url) {
    final String path = url.getPath();
    if ("blank".equalsIgnoreCase(path)) {
      return "";
    } else if ("bookmarks".equalsIgnoreCase(path)) {
      return this.getBookmarks();
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
      return this.getSystemProperties();
    } else {
      return "[Unknown about path: " + path + "]";
    }
  }

  private String getSystemProperties() {
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

  private String getBookmarks() {
    final BookmarksHistory history = BookmarksHistory.getInstance();
    return this.getBookmarks(history.getAllEntries());
  }

  private String getBookmarks(final String searchQuery) {
    // This is more of a scan. Not efficient but it does the
    // job for now considering the number of entries is limited.
    final String[] keywords = Strings.split(searchQuery);
    final BookmarksHistory history = BookmarksHistory.getInstance();
    final Collection<HistoryEntry<BookmarkInfo>> entries = history.getAllEntries();
    final Collection<ScoredEntry> sortedEntries = new TreeSet<ScoredEntry>();
    for (final HistoryEntry<BookmarkInfo> entry : entries) {
      final int matchScore = this.getMatchScore(entry.getItemInfo(), keywords);
      if (matchScore > 0) {
        sortedEntries.add(new ScoredEntry(entry, matchScore));
      }
    }
    final Collection<HistoryEntry<BookmarkInfo>> finalEntries = new ArrayList<HistoryEntry<BookmarkInfo>>();
    for (final ScoredEntry scoredEntry : sortedEntries) {
      finalEntries.add(scoredEntry.getHistoryEntry());
    }
    return this.getBookmarks(finalEntries);
  }

  private int getMatchScore(final BookmarkInfo binfo, final String[] keywords) {
    int total = 0;
    for (int i = 0; i < keywords.length; i++) {
      final String keyword = keywords[i];
      final int score = this.getMatchScore(binfo, keyword);
      if (score == 0) {
        return 0;
      }
      total += score;
    }
    return total;
  }

  private int getMatchScore(final BookmarkInfo binfo, final String keyword) {
    final String keywordTL = keyword.toLowerCase();
    int score = 0;
    final String urlText = binfo.getUrl().toExternalForm();
    if (urlText.contains(keyword)) {
      score += 3;
    } else if (urlText.toLowerCase().contains(keywordTL)) {
      score += 2;
    }
    final String title = binfo.getTitle();
    if (title != null && title.contains(keyword)) {
      score += 8;
    } else if (title != null && title.toLowerCase().contains(keywordTL)) {
      score += 6;
    }
    final String description = binfo.getDescription();
    if (description != null && description.contains(keyword)) {
      score += 3;
    } else if (description != null && description.toLowerCase().contains(keywordTL)) {
      score += 2;
    }
    final String[] tags = binfo.getTags();
    if (tags != null) {
      for (int i = 0; i < tags.length; i++) {
        if (tags[i].equals(keyword)) {
          score += 8;
        } else if (tags[i].toLowerCase().equals(keywordTL)) {
          score += 6;
        }
      }
    }
    return score;
  }

  private String getBookmarks(final Collection<HistoryEntry<BookmarkInfo>> entries) {
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
        this.writeBookmark(writer, entry);
      }
      writer.println("</ol>");
    }
    writer.println("</body>");
    writer.println("</html>");
    writer.flush();
    return swriter.toString();
  }

  private void writeBookmark(final PrintWriter writer, final HistoryEntry<BookmarkInfo> entry) {
    final java.net.URL url = entry.getUrl();
    final String urlText = url.toExternalForm();
    final BookmarkInfo binfo = entry.getItemInfo();
    String text = binfo.getTitle();
    if (text == null || text.length() == 0) {
      text = urlText;
    }
    final long elapsed = System.currentTimeMillis() - entry.getTimetstamp();
    String description = binfo.getDescription();
    if (description == null) {
      description = "";
    }
    writer.println("<LI>");
    writer.println("<DIV>");
    writer.println("<A href=\"" + urlText + "\">" + text + "</A> (" + Timing.getElapsedText(elapsed) + " ago)");
    writer.println("</DIV>");
    writer.println("<DIV>");
    writer.println(description);
    writer.println("</DIV>");
    writer.println("</LI>");
  }

  private class ScoredEntry implements Comparable {
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

    public int compareTo(final Object o) {
      if (this == o) {
        return 0;
      }
      final ScoredEntry other = (ScoredEntry) o;
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

    public int hashCode() {
      return this.score;
    }
  }
}
