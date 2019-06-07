/**
 * ============================================================================
 *  Copyright ©  2015-2019,    Cristiano V. Gavião
 *
 *  All rights reserved.
 *  This program and the accompanying materials are made available under
 *  the terms of the Eclipse Public License v1.0 which accompanies this
 *  distribution and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * ============================================================================
 */
package br.com.c8tech.tools.maven.plugin.osgi.repository.utils;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class XmlUtils {
    
    private XmlUtils(){
        
    }

    public static String prettyFormat(String input)
            throws IOException {
        try{
        InputSource src = new InputSource(new StringReader(input));
        Element document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(src).getDocumentElement();
        document.normalize();

        Boolean keepDeclaration = input.startsWith("<?xml");
        DOMImplementationRegistry registry = DOMImplementationRegistry
                .newInstance();
        DOMImplementationLS impl = (DOMImplementationLS) registry
                .getDOMImplementation("LS");
        LSSerializer writer = impl.createLSSerializer();
        writer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);
        writer.getDomConfig().setParameter("xml-declaration", keepDeclaration);
        return writer.writeToString(document);
        }
 catch (SAXException | ParserConfigurationException
                | ClassNotFoundException | InstantiationException | IllegalAccessException | ClassCastException e){
            throw new IOException(e);
        }
    }

    public static void assertXMLEqual(String expected, String actual)
            throws IOException {
        String canonicalExpected;
        canonicalExpected = prettyFormat(expected);
        String canonicalActual = prettyFormat(actual);
        assertEquals(canonicalExpected, canonicalActual);
    }

    public static void assertXMLEqual(Path expected, Path outputFile)
            throws IOException {
        String e = new String(Files.readAllBytes(expected));
        String r = new String(Files.readAllBytes(outputFile));
        assertXMLEqual(e, r);

    }

    public static void assertXMLEqual(Path expected, Path outputFile,
            Map<String, String> filters) throws IOException {

        String r = new String(Files.readAllBytes(outputFile));
        String e = new String(Files.readAllBytes(expected));
        for (String key : filters.keySet()) {
            e = replaceAll(e, key, filters.get(key));
        }
        assertXMLEqual(e, r);
    }

    private static String replaceAll(String source, String key,
            String newValue) {
        String regex = "\\$\\{" + key + "\\}";
        return source.replaceAll(regex, newValue);
    }
}
