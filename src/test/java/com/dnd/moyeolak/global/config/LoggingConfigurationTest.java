package com.dnd.moyeolak.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class LoggingConfigurationTest {

    @Test
    @DisplayName("Loki appender는 loki 프로필에서만 활성화된다")
    void shouldEnableLokiAppenderOnlyWithLokiProfile() throws Exception {
        Document document = loadLogbackConfiguration();
        Element lokiAppender = findAppenderByName(document, "LOKI");

        Element springProfile = (Element) lokiAppender.getParentNode();

        assertThat(springProfile.getTagName()).isEqualTo("springProfile");
        assertThat(springProfile.getAttribute("name")).isEqualTo("loki");
    }

    @Test
    @DisplayName("로그 패턴은 MDC requestId를 포함한다")
    void shouldIncludeRequestIdInLogPattern() throws Exception {
        Document document = loadLogbackConfiguration();
        Element patternProperty = findPropertyByName(document, "APP_LOG_PATTERN");

        assertThat(patternProperty.getAttribute("value")).contains("%X{requestId:-no-request}");
    }

    private Document loadLogbackConfiguration() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        try (InputStream inputStream = getClass().getResourceAsStream("/logback-spring.xml")) {
            assertThat(inputStream).isNotNull();
            return factory.newDocumentBuilder().parse(inputStream);
        }
    }

    private Element findAppenderByName(Document document, String name) {
        NodeList appenders = document.getElementsByTagName("appender");

        for (int i = 0; i < appenders.getLength(); i++) {
            Node node = appenders.item(i);
            if (node instanceof Element appender && name.equals(appender.getAttribute("name"))) {
                return appender;
            }
        }

        throw new AssertionError("Appender not found: " + name);
    }

    private Element findPropertyByName(Document document, String name) {
        NodeList properties = document.getElementsByTagName("property");

        for (int i = 0; i < properties.getLength(); i++) {
            Node node = properties.item(i);
            if (node instanceof Element property && name.equals(property.getAttribute("name"))) {
                return property;
            }
        }

        throw new AssertionError("Property not found: " + name);
    }
}
