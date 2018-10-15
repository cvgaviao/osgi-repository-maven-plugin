/**
 * ==========================================================================
 * Copyright © 2015-2018 Cristiano Gavião, C8 Technology ME.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Cristiano Gavião (cvgaviao@c8tech.com.br)- initial API and implementation
 * ==========================================================================
 */
package br.com.c8tech.tools.maven.plugin.osgi.repository.ut;

import static io.takari.maven.testing.TestMavenRuntime.newParameter;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.c8tech.tools.maven.plugin.osgi.repository.utils.RepoIndexBridge;

import br.com.c8tech.tools.maven.plugin.osgi.repository.utils.XmlUtils;

public class GenerateIndexFromDependenciesUnitTest
        extends AbstractOsgiRepositoryTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test(expected = MojoExecutionException.class)
    public void testFailureWhenNoMavenArtifactsWereCached() throws Exception {
        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));

        addDependency(project, "subsystems/aCompositeSubsystem.esa", "1.0",
                true, Artifact.SCOPE_COMPILE, "osgi.subsystem.composite",
                false);
        incrementalBuildRule.executeMojo(project, "generateP2FromDependencies",
                newParameter("verbose", "true"),
                newParameter("generateP2", "true"),
                newParameter("scopes", "compile,provided"));
    }

    @Test(expected = MojoExecutionException.class)
    public void testFailureWhenNoP2ArtifactsWereCached() throws Exception {
        URL repository = testProperties.getClass()
                .getResource("/composite/repository/1.0.0");
        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));

        incrementalBuildRule.executeMojo(project, "generateP2FromDependencies",
                newParameter("verbose", "true"),
                newParameter("generateP2", "true"),
                newParameter("previousCachingRequired", "true"),
                newParameterP2ArtifactSets(
                        newParameterP2ArtifactSet(repository.toExternalForm(),
                                "group", newArtifactList("aBundle:1.8.4"))));
    }

    @Test
    public void testCompileAndProvidedDependenciesAreBeingSelected()
            throws Exception {

        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));

        addDependency(project, "subsystems/aCompositeSubsystem.esa", "1.0",
                true, Artifact.SCOPE_COMPILE, "osgi.subsystem.composite",
                false);
        // intentionally added with wrong type
        addDependency(project, "jars/01-bsn+version.jar", "1.0", true,
                Artifact.SCOPE_COMPILE, "osgi.subsystem.composite", true);
        addDependency(project, "jars/anotherBundle.jar", "1.0", true,
                Artifact.SCOPE_PROVIDED, "jar", false);
        addDependency(project, "jars/aNonValidBundle.jar", "1.0", true,
                Artifact.SCOPE_COMPILE, "jar", false);
        addDependency(project, "jars/aTransitiveDependencyBundle.jar", "1.0",
                false, Artifact.SCOPE_COMPILE, "jar", false);

        URL repository = testProperties.getClass()
                .getResource("/composite/repository/1.0.0");

        incrementalBuildRule.executeMojo(project, "downloadP2Artifacts",
                newParameter("verbose", "true"),
                newParameterP2ArtifactSets(
                        newParameterP2ArtifactSet(repository.toExternalForm(),
                                "group", newArtifactList("aBundle:1.8.4"))));
        incrementalBuildRule.assertBuildOutputs(
                new File(project.getBasedir(), "target"),
                "cache/plugins/aBundle_1.8.4.jar");

        incrementalBuildRule.executeMojo(project, "cacheMavenArtifacts",
                newParameter("verbose", "true"),
                newParameter("optionalConsidered", "false"),
                newParameter("cachedFilePatternReplacement", "%n-%c_%v.%e"),
                newParameter("scopes", "compile,provided"),
                newParameter("transitiveConsidered", "false"));

        incrementalBuildRule.assertBuildOutputs(
                new File(project.getBasedir(), "target"),
                "cache/plugins/anotherBundle_1.0.0.jar",
                "cache/subsystems/aCompositeSubsystem_0.1.1.qualifier.esa");

        incrementalBuildRule.executeMojo(project,
                "generateIndexFromDependencies",
                newParameter("verbose", "true"),
                newParameterP2ArtifactSets(
                        newParameterP2ArtifactSet(repository.toExternalForm(),
                                "group", newArtifactList("aBundle:1.8.4"))),
                newParameter("optionalConsidered", "false"),
                newParameter("compressed", "false"),
                newParameter("indexFileName", "index.xml"),
                newParameter("cachedFilePatternReplacement", "%n-%c_%v.%e"),
                newParameter("scopes", "compile,provided"),
                newParameter("incrementOverride", "1"),
                newParameter("transitiveConsidered", "false"));

        incrementalBuildRule.assertBuildOutputs(project.getBasedir(),
                "target/work/repository/index.xml",
                "target/work/repository/subsystems/aCompositeSubsystem_0.1.1.qualifier.esa",
                "target/work/repository/plugins/anotherBundle_1.0.0.jar",
                "target/work/repository/plugins/aBundle_1.8.4.jar");

        Path outputFile = assertAndGetBuildOutput(project,
                "target/work/repository/index.xml");
        Path expected = Paths.get(getClass()
                .getResource("/xmls/index_without_optional_and_transitives.xml")
                .toURI());

        XmlUtils.assertXMLEqual(expected, outputFile);

        // pack the generated index
        incrementalBuildRule.executeMojo(project,
                "packIndexedRepositoryArchive", newParameter("verbose", "true"),
                newParameterP2ArtifactSets(
                        newParameterP2ArtifactSet(repository.toExternalForm(),
                                "group", newArtifactList("aBundle:1.8.4"))),
                newParameter("optionalConsidered", "false"),
                newParameter("verbose", "true"),
                newParameter("compressed", "false"),
                newParameter("cachedFilePatternReplacement", "%n-%c_%v.%e"),
                newParameter("scopes", "compile,provided"),
                newParameter("incrementOverride", "1"),
                newParameter("transitiveConsidered", "false"));
        Path zipPath = project.getBasedir().toPath()
                .resolve("target/osgi-repository.unit.test-0.1.0.zip");
        assertThat(zipPath.toFile()).as("Repository file was not created.")
                .exists();

        ZipFile zip = new ZipFile(zipPath.toFile());
        assertThat(zip.size()).isEqualTo(6);

        ZipEntry entry1 = zip.getEntry("plugins/anotherBundle_1.0.0.jar");
        assertThat(entry1).isNotNull();

        ZipEntry entry2 = zip
                .getEntry("subsystems/aCompositeSubsystem_0.1.1.qualifier.esa");
        assertThat(entry2).isNotNull();

        ZipEntry entry3 = zip.getEntry("plugins/aBundle_1.8.4.jar");
        assertThat(entry3).isNotNull();

        ZipEntry entry4 = zip.getEntry("index.xml");
        assertThat(entry4).isNotNull();

        zip.close();
    }

    /**
     * When artifacts are not being embedded into the repository zip we will use
     * an absolute path in the index file.
     *
     * @throws Exception
     */
    @Test
    public void testIndexFileWithAbsolutePath() throws Exception {

        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));

        addDependency(project, "jars/aBundle.jar", "1.1.0", true,
                Artifact.SCOPE_COMPILE, "jar", false);
        incrementalBuildRule.executeMojo(project, "cacheMavenArtifacts",
                newParameter("indexFileName", "repository.xml"),
                newParameter("verbose", "true"),
                newParameter("compressed", "false"),
                newParameter("forceAbsolutePath", "true"),
                newParameter("incrementOverride", "1"),
                newParameter("embedArtifacts", "false"));

        incrementalBuildRule.assertBuildOutputs(
                new File(project.getBasedir(), "target"),
                "cache/plugins/aBundle-1.1.0.jar");

        incrementalBuildRule.executeMojo(project,
                "generateIndexFromDependencies",
                newParameter("indexFileName", "repository.xml"),
                newParameter("verbose", "true"),
                newParameter("compressed", "false"),
                newParameter("incrementOverride", "1"),
                newParameter("forceAbsolutePath", "true"),
                newParameter("embedArtifacts", "false"));
        incrementalBuildRule.assertBuildOutputs(project.getBasedir(),
                "target/work/repository/repository.xml",
                "target/work/repository/plugins/aBundle-1.1.0.jar");

        String expectedFile = new File(project.getBasedir(),
                "target/work/repository/plugins/aBundle-1.1.0.jar").getPath();
        Assert.assertNotNull(expectedFile);
        Path outputFile = assertAndGetBuildOutput(project,
                "target/work/repository/repository.xml");
        assertContentResourceUrlIs(outputFile, "com.c8tech.bundle", "1.1.0",
                expectedFile);

        // pack the generated index
        incrementalBuildRule.executeMojo(project,
                "packIndexedRepositoryArchive",
                newParameter("indexFileName", "repository.xml"),
                newParameter("verbose", "true"),
                newParameter("compressed", "false"),
                newParameter("forceAbsolutePath", "true"),
                newParameter("incrementOverride", "1"),
                newParameter("embedArtifacts", "false"));
        Path zip = project.getBasedir().toPath()
                .resolve("target/osgi-repository.unit.test-0.1.0.zip");
        assertThat(zip.toFile()).as("Repository file was not created.")
                .exists();

    }

    /**
     * When embedding artifacts into the repository zip we will use a relative
     * path in the index file.
     *
     * @throws Exception
     */
    @Test
    public void testIndexFileWithRelativePath() throws Exception {

        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));

        addDependency(project, "jars/aBundle.jar", "1.1.0", true,
                Artifact.SCOPE_COMPILE, "jar", false);

        incrementalBuildRule.executeMojo(project, "cacheMavenArtifacts",
                newParameter("compressed", "false"),
                newParameter("embedArtifacts", "true"));

        incrementalBuildRule.executeMojo(project,
                "generateIndexFromDependencies",
                newParameter("indexFileName", "index.xml"),
                newParameter("compressed", "false"),
                newParameter("embedArtifacts", "true"));
        Path outputFile = assertAndGetBuildOutput(project,
                "target/work/repository/index.xml");
        assertContentResourceUrlIs(outputFile, "com.c8tech.bundle", "1.1.0",
                "plugins/aBundle-1.1.0.jar");

        // pack the generated index
        incrementalBuildRule.executeMojo(project,
                "packIndexedRepositoryArchive",
                newParameter("compressed", "false"),
                newParameter("embedArtifacts", "true"));
        Path zip = project.getBasedir().toPath()
                .resolve("target/osgi-repository.unit.test-0.1.0.zip");
        assertThat(zip.toFile()).as("Repository file was not created.")
                .exists();

    }

    @Test
    public void testUsingExtraPackagingConfig() throws Exception {
        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--fail-wrong-packaging"));
        addDependency(project, "jars/anotherBundle.jar", "1.0", true,
                Artifact.SCOPE_PROVIDED, "jar", false);

        incrementalBuildRule.executeMojo(project, "cacheMavenArtifacts",
                newParameter("compressed", "false"),
                newParameter("verbose", "true"),
                newParameter("generateP2", "true"),
                newParameter("optionalConsidered", "true"),
                newParameter("incrementOverride", "1"),
                newParameter("extraSupportedPackagings", "jar"),
                newParameter("scopes", "compile,provided"),
                newParameter("transitiveConsidered", "true"));

    }

    @Test
    public void testOptionalAndTransitiveDependenciesAreBeingSelected()
            throws Exception {
        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));

        addDependency(project, "subsystems/aCompositeSubsystem.esa", "1.0",
                true, Artifact.SCOPE_COMPILE, "osgi.subsystem.composite",
                false);
        addDependency(project, "jars/01-bsn+version.jar", "1.0", true,
                Artifact.SCOPE_COMPILE, "jar", true);
        addDependency(project, "jars/anotherBundle.jar", "1.0", true,
                Artifact.SCOPE_PROVIDED, "jar", false);
        addDependency(project, "jars/aNonValidBundle.jar", "1.0", true,
                Artifact.SCOPE_COMPILE, "jar", false);
        addDependency(project, "jars/aBundle.jar", "1.0", true,
                Artifact.SCOPE_COMPILE, "jar", false);
        addDependency(project, "jars/aTransitiveDependencyBundle.jar", "1.0",
                false, Artifact.SCOPE_COMPILE, "jar", false);

        incrementalBuildRule.executeMojo(project, "cacheMavenArtifacts",
                newParameter("compressed", "false"),
                newParameter("verbose", "true"),
                newParameter("generateP2", "true"),
                newParameter("optionalConsidered", "true"),
                newParameter("incrementOverride", "1"),
                newParameter("scopes", "compile,provided"),
                newParameter("transitiveConsidered", "true"));

        // since is not possible to generated a p2 using unit tests
        // we need to simulate its execution copying the plugins from cache to a
        // proper directory
        Path basedir = project.getBasedir().toPath();
        Path plugins = Files.createDirectories(
                basedir.resolve("target/work/repository/plugins"));
        Path subsystems = Files.createDirectories(
                basedir.resolve("target/work/repository/subsystems"));
        Files.copy(
                basedir.resolve(
                        "target/cache/plugins/br.com.c8tech.bundle_1.1.0.jar"),
                plugins.resolve("br.com.c8tech.bundle_1.1.0.jar"));
        Files.copy(basedir.resolve(
                "target/cache/plugins/br.com.c8tech.anotherBundle_1.0.0.jar"),
                plugins.resolve("br.com.c8tech.anotherBundle_1.0.0.jar"));
        Files.copy(basedir.resolve(
                "target/cache/plugins/br.com.c8tech.bundle.transitive_1.0.0.jar"),
                plugins.resolve("br.com.c8tech.bundle.transitive_1.0.0.jar"));
        Files.copy(
                basedir.resolve("target/cache/subsystems/br.com.c8tech.subsystem.composite_0.1.1.qualifier.esa"),
                subsystems.resolve("br.com.c8tech.subsystem.composite_0.1.1.qualifier.esa"));

        incrementalBuildRule.executeMojo(project,
                "generateIndexFromDependencies",
                newParameter("compressed", "false"),
                newParameter("verbose", "true"),
                newParameter("generateP2", "true"),
                newParameter("optionalConsidered", "true"),
                newParameter("indexFileName", "index.xml"),
                newParameter("incrementOverride", "1"),
                newParameter("scopes", "compile,provided"),
                newParameter("transitiveConsidered", "true"));

        incrementalBuildRule.assertBuildOutputs(project.getBasedir(),
                "target/work/repository/plugins/br.com.c8tech.anotherBundle_1.0.0.jar",
                "target/work/repository/plugins/br.com.c8tech.bundle.transitive_1.0.0.jar",
                "target/work/repository/plugins/br.com.c8tech.bundle_1.1.0.jar",
                "target/work/repository/index.xml",
                "target/work/repository/subsystems/br.com.c8tech.subsystem.composite_0.1.1.qualifier.esa");

        Path outputFile = assertAndGetBuildOutput(project,
                "target/work/repository/index.xml");
        Path expected = Paths.get(getClass()
                .getResource("/xmls/index_with_optional_and_transitives.xml")
                .toURI());

        XmlUtils.assertXMLEqual(expected, outputFile);

        // pack the generated index
        incrementalBuildRule.executeMojo(project,
                "packIndexedRepositoryArchive",
                newParameter("compressed", "false"),
                newParameter("verbose", "true"),
                newParameter("optionalConsidered", "true"),
                newParameter("generateP2", "true"),
                newParameter("incrementOverride", "1"),
                newParameter("scopes", "compile,provided"),
                newParameter("classifier", "extra"),
                newParameter("transitiveConsidered", "true"));
        Path zip = project.getBasedir().toPath()
                .resolve("target/osgi-repository.unit.test-0.1.0-extra.zip");
        assertThat(zip.toFile()).as("Repository file was not created.")
                .exists();

        // generated target definition file
        incrementalBuildRule.executeMojo(project,
                "generateTargetDefinitionFile",
                newParameter("compressed", "false"),
                newParameter("verbose", "true"),
                newParameter("optionalConsidered", "true"),
                newParameter("incrementOverride", "1"),
                newParameter("scopes", "compile,provided"),
                newParameter("classifier", "extra"),
                newParameter("generateP2", "true"),
                newParameter("generateTargetPlatformDefinition", "true"),
                newParameter("transitiveConsidered", "true"));
        Path tpd = project.getBasedir().toPath()
                .resolve("target/osgi-repository.unit.test-0.1.0-extra.target");
        assertThat(tpd.toFile())
                .as("Target Platform Definition file was not created.")
                .exists();

    }

    @Test(expected = MojoExecutionException.class)
    public void testWrongPackagingFailure() throws Exception {
        File basedir = testResources
                .getBasedir("ut-project--fail-wrong-packaging");
        incrementalBuildRule.executeMojo(basedir,
                "generateIndexFromDependencies");

    }

    /**
     * This test can't be run currently using the takari testing api. this
     * feature is being tested using an integration test though.
     *
     * @throws Exception
     */
    // @Test
    public void testP2Generation() throws Exception {

        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));

        addDependency(project, "jars/aBundle.jar", "1.0", true,
                Artifact.SCOPE_COMPILE, "jar", false);

        incrementalBuildRule.executeMojo(project, "cacheMavenArtifacts",
                newParameter("verbose", "true"),
                newParameter("generateP2", "true"));

        incrementalBuildRule.assertBuildOutputs(
                new File(project.getBasedir(), "target"),
                "cache/plugins/aBundle_1.0.jar");

        incrementalBuildRule.executeMojo(project, "generateP2FromDependencies",
                newParameter("verbose", "true"),
                newParameter("generateP2", "true"));
        incrementalBuildRule.assertBuildOutputs(project.getBasedir(),
                "target/classes/category.xml");

        String expectedFile = new File(project.getBasedir(),
                "target/cache/plugins/aBundle-1.1.0.jar").getPath();
        Assert.assertNotNull(expectedFile);

        // pack the generated index
        incrementalBuildRule.executeMojo(project,
                "packIndexedRepositoryArchive",
                newParameter("indexFileName", "repository.xml"),
                newParameter("verbose", "true"),
                newParameter("generateP2", "true"));
        Path zip = project.getBasedir().toPath()
                .resolve("target/osgi-repository.unit.test-0.1.0.zip");
        assertThat(zip.toFile()).as("Repository file was not created.")
                .exists();

    }

    @Test
    public void testXpath()
            throws IOException, URISyntaxException, JDOMException {
        URL file = getClass().getResource("/xmls/index_xpath_text.xml");
        String SEARCH_PATTERN = "//repo:resource[repo:capability[repo:attribute[@name='osgi.identity' "
                + "and contains(@value,'com.c8tech.bundle')] "
                + "and repo:attribute[@name='version' and @value='1.8.4']]]/repo:capability[@namespace='osgi.content']/repo:attribute[@name='url']";

        try (InputStream bis = Files.newInputStream(Paths.get(file.toURI()),
                StandardOpenOption.READ)) {
            SAXBuilder saxBuilder = new SAXBuilder();
            org.jdom2.Document document = saxBuilder.build(bis);
            XPathFactory xpathFactory = XPathFactory.instance();
            XPathExpression<Element> expr = xpathFactory.compile(SEARCH_PATTERN,
                    Filters.element(RepoIndexBridge.NS), null,
                    RepoIndexBridge.NS);
            List<Element> elements = expr.evaluate(document);
            assertThat(elements.size()).isEqualTo(1);
            assertThat(elements.get(0).getAttributeValue("value"))
                    .isEqualTo("bundles/aBundle.jar");
        }
    }
}
