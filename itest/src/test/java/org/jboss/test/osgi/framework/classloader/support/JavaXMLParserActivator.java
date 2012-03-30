package org.jboss.test.osgi.framework.classloader.support;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class JavaXMLParserActivator implements BundleActivator {

    @Override
    public void start(BundleContext context) throws Exception {
        new ParserTest().parseSomething();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
    }

    public static class ParserTest {

        public static final String TEST = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<project >"
                + "<modelVersion>4.0.0</modelVersion>"
                + "<groupId>foo.bar</groupId>"
                + "<artifactId>classloadingtest</artifactId>"
                + "<version>1.0-SNAPSHOT</version>"
                + "<packaging>bundle</packaging>"
                + "<name>classloadingtest</name>"
                + "</project>";

        public void parseSomething() {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            try {
                SAXParser saxParser = factory.newSAXParser();
                saxParser.parse(TEST, new Myhandler());
            } catch (Throwable err) {
                err.printStackTrace();
            }
        }
    }

    public static class Myhandler extends DefaultHandler {
        
        public int starts = 0;
        public int ends = 0;

        @Override
        public void startElement(String string, String string1, String string2, Attributes atrbts) throws SAXException {
            starts++;
        }

        @Override
        public void endElement(String string, String string1, String string2) throws SAXException {
            ends++;
        }
    }    
}
