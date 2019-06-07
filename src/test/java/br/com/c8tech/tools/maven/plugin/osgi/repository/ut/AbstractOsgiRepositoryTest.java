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
package br.com.c8tech.tools.maven.plugin.osgi.repository.ut;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.junit.Assert;
import org.junit.Rule;

import com.c8tech.tools.maven.plugin.osgi.repository.utils.RepoIndexBridge;

import br.com.c8tech.tools.maven.osgi.lib.mojo.CommonMojoConstants;
import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestProperties;
import io.takari.maven.testing.TestResources;

public abstract class AbstractOsgiRepositoryTest {

    @Rule
    public final IncrementalBuildRule incrementalBuildRule = new IncrementalBuildRule();

    protected final TestProperties testProperties = new TestProperties();

    @Rule
    public final TestResources testResources = new TestResources();

    public AbstractOsgiRepositoryTest() {
    }

    public static void assertContentResourceUrlIs(Path xmlFile,
            String symbolicName, String version, String elementValue)
            throws IOException, JDOMException {

        try (InputStream bis = Files.newInputStream(xmlFile,
                StandardOpenOption.READ)) {
            SAXBuilder saxBuilder = new SAXBuilder();
            org.jdom2.Document document = saxBuilder.build(bis);
            XPathFactory xpathFactory = XPathFactory.instance();
            XPathExpression<Element> expr = xpathFactory.compile(
                    getArtifactSearchPattern(symbolicName, version),
                    Filters.element(RepoIndexBridge.NS), null,
                    RepoIndexBridge.NS);
            List<Element> elements = expr.evaluate(document);
            assertThat(elements.size()).isEqualTo(1);
            assertThat(elements.get(0).getAttributeValue("value"))
                    .isEqualTo(elementValue);
        }
    }

    protected static String getArtifactSearchPattern(String artifactId,
            String version) {
        String SEARCH_PATTERN = "//repo:resource[repo:capability[repo:attribute[@name='osgi.identity' "
                + "and contains(@value,'%s')] "
                + "and repo:attribute[@name='version' and @value='%s']]]/repo:capability[@namespace='osgi.content']/repo:attribute[@name='url']";

        String formatted = String.format(SEARCH_PATTERN, artifactId, version);
        return formatted;
    }

    protected final void addDependency(MavenProject project,
            String artifactName, String version, boolean direct, String scope,
            String type, boolean optional) throws Exception {
        URL fileUrl = testProperties.getClass()
                .getResource(!artifactName.startsWith("/") ? "/" + artifactName
                        : artifactName);
        if (fileUrl == null) {
            throw new IllegalArgumentException(
                    "A dependency file was not found at:" + artifactName);
        }
        File file = new File(fileUrl.getFile());
        String id = file.getName().substring(0, file.getName().indexOf('.'));
        incrementalBuildRule.newDependency(file).setVersion(version)
                .setType(type).setScope(scope).setOptional(optional)
                .setArtifactId(id).addTo(project, direct);
    }

    public final void addDependencyWorkspaceCustomPackageDir(
            MavenProject project, String artifactName, boolean direct,
            String scope, String type, boolean optional) throws Exception {
        URL file = testProperties.getClass()
                .getResource(!artifactName.startsWith("/") ? "/" + artifactName
                        : artifactName);
        if (file == null) {
            throw new IllegalArgumentException(
                    "A dependency directory was not found for:" + artifactName);
        }
        File pom = new File(file.getFile(), CommonMojoConstants.MAVEN_POM);
        incrementalBuildRule.newDependency(pom).setType(type).setScope(scope)
                .setOptional(optional).setArtifactId(artifactName)
                .addTo(project, direct);
    }

    protected Path assertAndGetBuildOutput(MavenProject project,
            String outputPath) {
        Path fileOutput = Paths.get(project.getBasedir().getPath(), outputPath);
        Assert.assertTrue("File was not found at " + outputPath,
                fileOutput.toFile().exists() && fileOutput.toFile().isFile()
                        && fileOutput.toFile().canRead());
        return fileOutput;
    }

    protected Xpp3Dom newChild(String name, String value) {
        Xpp3Dom child = new Xpp3Dom(name);
        child.setValue(value);
        return child;
    }

    protected Xpp3Dom newParameterP2ArtifactSets(Xpp3Dom... pP2ArtifactSet) {
        final Xpp3Dom configuration = new Xpp3Dom("p2ArtifactSets");
        for (Xpp3Dom bundleSet : pP2ArtifactSet) {
            configuration.addChild(bundleSet);
        }
        return configuration;
    }

    protected Xpp3Dom newParameterMavenArtifactsSet(String... mavenArtifacts) {
        final Xpp3Dom bundleSet = new Xpp3Dom("mavenArtifactSet");
        for (String artifact : mavenArtifacts) {
            bundleSet.addChild(newChild("artifact", artifact));
        }
        return bundleSet;
    }

    protected List<Xpp3Dom> newArtifactList(String... p2Artifacts) {
        final List<Xpp3Dom> bundleSet = new ArrayList<>();
        for (String artifact : p2Artifacts) {
            bundleSet.add(newChild("artifact", artifact));
        }
        return bundleSet;
    }

    protected Xpp3Dom newParameterP2ArtifactSet(String repository,
            String pGroupId, List<Xpp3Dom> pArtifacts) {
        final Xpp3Dom bundleSet1 = new Xpp3Dom("p2ArtifactSet");
        bundleSet1.addChild(newChild("repositoryURL", repository));
        bundleSet1.addChild(newChild("defaultGroupId", pGroupId));
        for (Xpp3Dom artifact : pArtifacts) {
            bundleSet1.addChild(artifact);
        }
        return bundleSet1;
    }

}
