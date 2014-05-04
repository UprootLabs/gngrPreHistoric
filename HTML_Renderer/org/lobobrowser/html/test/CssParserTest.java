package org.lobobrowser.html.test;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.*;
import java.io.*;
import java.util.logging.*;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import org.lobobrowser.html.*;
import org.lobobrowser.html.gui.*;
import org.lobobrowser.html.style.*;
import org.lobobrowser.util.io.*;
import org.w3c.css.sac.*;
import org.w3c.dom.css.*;

import com.steadystate.css.parser.CSSOMParser;

/**
 * Tests only the CSS parser.
 */
public class CssParserTest extends JFrame {
  private static final Logger logger = Logger.getLogger(CssParserTest.class.getName());
  private final HtmlPanel cssOutput;
  private final JTextArea textArea;

  public CssParserTest() throws HeadlessException {
    this("CSS Parser Test Tool");
  }

  public CssParserTest(final String title) throws HeadlessException {
    super(title);
    this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    final Container contentPane = this.getContentPane();
    contentPane.setLayout(new BorderLayout());
    final JPanel topPanel = new JPanel();
    topPanel.setLayout(new BorderLayout());
    final JPanel bottomPanel = new JPanel();
    bottomPanel.setLayout(new BorderLayout());
    final JTextField textField = new JTextField();
    final JButton button = new JButton("Parse & Render");
    final JTabbedPane tabbedPane = new JTabbedPane();

    final HtmlPanel htmlPanel = new HtmlPanel();
    this.cssOutput = htmlPanel;

    contentPane.add(topPanel, BorderLayout.NORTH);
    contentPane.add(bottomPanel, BorderLayout.CENTER);

    topPanel.add(new JLabel("URL: "), BorderLayout.WEST);
    topPanel.add(textField, BorderLayout.CENTER);
    topPanel.add(button, BorderLayout.EAST);

    bottomPanel.add(tabbedPane, BorderLayout.CENTER);

    final JTextArea textArea = new JTextArea();
    this.textArea = textArea;
    final JScrollPane textAreaSp = new JScrollPane(textArea);

    tabbedPane.addTab("Parsed CSS", htmlPanel);
    tabbedPane.addTab("Source Code", textAreaSp);

    button.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent event) {
        process(textField.getText());
      }
    });
  }

  private void process(final String uri) {
    try {
      URL url;
      try {
        url = new URL(uri);
      } catch (final java.net.MalformedURLException mfu) {
        final int idx = uri.indexOf(':');
        if (idx == -1 || idx == 1) {
          // try file
          url = new URL("file:" + uri);
        } else {
          throw mfu;
        }
      }
      logger.info("process(): Loading URI=[" + uri + "].");
      final long time0 = System.currentTimeMillis();
      final URLConnection connection = url.openConnection();
      connection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible;) Cobra/0.96.1+");
      connection.setRequestProperty("Cookie", "");
      if (connection instanceof HttpURLConnection) {
        final HttpURLConnection hc = (HttpURLConnection) connection;
        hc.setInstanceFollowRedirects(true);
        final int responseCode = hc.getResponseCode();
        logger.info("process(): HTTP response code: " + responseCode);
      }
      final InputStream in = connection.getInputStream();
      byte[] content;
      try {
        content = IORoutines.load(in, 8192);
      } finally {
        in.close();
      }
      final String source = new String(content, "ISO-8859-1");
      this.textArea.setText(source);
      final long time1 = System.currentTimeMillis();
      final CSSOMParser parser = CSSUtilities.mkParser();
      final InputSource is = CSSUtilities.getCssInputSourceForStyleSheet(source, uri);
      final CSSStyleSheet styleSheet = parser.parseStyleSheet(is, null, uri);
      final long time2 = System.currentTimeMillis();
      logger.info("Parsed URI=[" + uri + "]: Parse elapsed: " + (time2 - time1) + " ms. Load elapsed: " + (time1 - time0) + " ms.");
      this.showStyleSheet(styleSheet);
    } catch (final Exception err) {
      logger.log(Level.SEVERE, "Error trying to load URI=[" + uri + "].", err);
      this.clearCssOutput();
    }
  }

  private void clearCssOutput() {
    this.cssOutput.clearDocument();
  }

  private void showStyleSheet(final CSSStyleSheet styleSheet) {
    final StringWriter stringWriter = new StringWriter();
    final PrintWriter writer = new PrintWriter(stringWriter);
    writer.println("<DL>");
    final CSSRuleList ruleList = styleSheet.getCssRules();
    final int length = ruleList.getLength();
    for (int i = 0; i < length; i++) {
      final CSSRule rule = ruleList.item(i);
      writer.println("<DT><strong>Rule: type=" + rule.getType() + ",class=" + rule.getClass().getName() + "</strong></DT>");
      writer.println("<DD>");
      this.writeRuleInfo(writer, rule);
      writer.println("</DD>");
    }
    writer.println("</DL>");
    writer.flush();
    final String html = stringWriter.toString();
    final HtmlRendererContext rcontext = new SimpleHtmlRendererContext(this.cssOutput);
    this.cssOutput.setHtml(html, "about:css", rcontext);
  }

  private void writeRuleInfo(final PrintWriter writer, final CSSRule rule) {
    if (rule instanceof CSSStyleRule) {
      final CSSStyleRule styleRule = (CSSStyleRule) rule;
      writer.println("Selector: " + styleRule.getSelectorText());
      writer.println("<br>");
      writer.println("CSS Text: " + styleRule.getCssText());
    } else if (rule instanceof CSSImportRule) {
      final CSSImportRule styleRule = (CSSImportRule) rule;
      writer.println("HREF: " + styleRule.getHref());
      writer.println("<br>");
      writer.println("CSS Text: " + styleRule.getCssText());
    }
  }

  public static void main(final String[] args) {
    final CssParserTest frame = new CssParserTest();
    frame.setSize(800, 400);
    frame.setExtendedState(TestFrame.MAXIMIZED_BOTH);
    frame.setVisible(true);
  }
}
