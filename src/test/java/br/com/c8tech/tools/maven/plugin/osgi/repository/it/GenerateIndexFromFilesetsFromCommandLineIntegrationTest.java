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
package br.com.c8tech.tools.maven.plugin.osgi.repository.it;

import static org.assertj.core.api.Assertions.*;

import static io.takari.maven.testing.TestResources.assertFilesPresent;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.c8tech.tools.maven.plugin.osgi.repository.AbstractOsgiRepositoryMojo;

import br.com.c8tech.tools.maven.osgi.lib.mojo.CommonMojoConstants;
import br.com.c8tech.tools.maven.plugin.osgi.repository.utils.DirectoryUtil;
import br.com.c8tech.tools.maven.plugin.osgi.repository.utils.XmlUtils;
import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({ "3.5.4" })
public class GenerateIndexFromFilesetsFromCommandLineIntegrationTest {

    public MavenRuntime mavenRuntime;

    public final MavenRuntimeBuilder mavenRuntimeBuilder;

    public GenerateIndexFromFilesetsFromCommandLineIntegrationTest(
            MavenRuntimeBuilder verifierBuilder) throws Exception {
        this.mavenRuntimeBuilder = verifierBuilder;
    }

    @Rule
    public final TestResources resources = new TestResources();

    @Test
    public void testGenerateIndexWithAbsolutePath() throws Exception {
        File basedir = resources.getBasedir("it-project--jar");
        URI expectedFile = getClass().getResource("/jars/aBundle.jar").toURI();
        assertThat(expectedFile).isNotNull();
        Path bundleSourceDir = Paths.get(expectedFile).getParent();
        URI expectedSS = getClass()
                .getResource("/subsystems/aCompositeSubsystem.esa").toURI();
        assertThat(expectedSS).isNotNull();
        Path ssSourceDir = Paths.get(expectedSS).getParent();
        Path rootDir = basedir.toPath()
                .resolve(AbstractOsgiRepositoryMojo.DEFAULT_WORK_DIR_NAME);
        Path jarsDir = rootDir.resolve(CommonMojoConstants.OSGI_BUNDLES_DIRECTORY);
        Path ssDir = rootDir.resolve(CommonMojoConstants.OSGI_SUBSYSTEM_DIRECTORY);
        DirectoryUtil.copyDirectory(bundleSourceDir, jarsDir);
        DirectoryUtil.copyDirectory(ssSourceDir, ssDir);
        String rootDirParam = "-Dosgi.repository.rootDir=" + rootDir;
        String forceAbsolutePathParam = "-Dosgi.repository.forceAbsolutePath=true";
        String incrementOverrideParam = "-Dosgi.repository.incrementOverride=1";
        String compressedParam = "-Dosgi.repository.compressed=false";
        String filesetsParam = "-Dosgi.repository.filesets=" + jarsDir
                + ":**/*.jar:**/01*.jar;**/aTra*;**/aNon*.jar" + ", " + ssDir
                + ":**/*.esa";
        mavenRuntime = mavenRuntimeBuilder
                // .withCliOptions("-X", "-U", "-B","-o" rootDirParam,
                // filesetsParam, skipRelativiseParam)
                .withCliOptions("-B", rootDirParam, filesetsParam,
                        forceAbsolutePathParam, incrementOverrideParam,
                        compressedParam)
                .build();
        MavenExecutionResult result = mavenRuntime.forProject(basedir)
                .execute("package");

        result.assertErrorFreeLog();
        result.assertLogText(
                "Started generation of the repository index file for project ");
        result.assertLogText("Repository index file was generated at");

        Path outputFile = rootDir.resolve("index.xml");
        Path expected = Paths.get(getClass()
                .getResource("/xmls/index_from_folder_absolute.xml").toURI());
        Map<String, String> filters = new HashMap<>(1);
        filters.put("jars-dir", jarsDir.toString());
        filters.put("subsystems-dir", ssDir.toString());
        XmlUtils.assertXMLEqual(expected, outputFile, filters);
    }

    @Test
    public void testAbsolutePathCompressedFile() throws Exception {
        File basedir = resources.getBasedir("it-project--jar");
        URI expectedFile = getClass().getResource("/jars/aBundle.jar").toURI();
        assertThat(expectedFile).isNotNull();
        Path sourceDir = Paths.get(expectedFile).getParent();
        Path targetDir = basedir.toPath();
        Path rootDir = targetDir
                .resolve(AbstractOsgiRepositoryMojo.DEFAULT_WORK_DIR_NAME);
        Path jarsDir = rootDir.resolve(CommonMojoConstants.OSGI_BUNDLES_DIRECTORY);
        DirectoryUtil.copyDirectory(sourceDir, jarsDir);
        String rootDirParam = "-Dosgi.repository.rootDir=" + rootDir;
        String skipRelativiseParam = "-Dosgi.repository.forceAbsolutePath=true";
        String incrementOverrideParam = "-Dosgi.repository.incrementOverride=1";
        String filesetsParam = "-Dosgi.repository.filesets=" + jarsDir
                + ":**/*.jar:**/*.txt";
        mavenRuntime = mavenRuntimeBuilder
                // .withCliOptions("-X", "-U", "-B", rootDirParam,
                // filesetsParam, skipRelativiseParam)
                .withCliOptions("-B", rootDirParam, filesetsParam,
                        skipRelativiseParam, incrementOverrideParam)
                .build();
        MavenExecutionResult result = mavenRuntime.forProject(basedir)
                .execute("package");

        result.assertErrorFreeLog();
        result.assertLogText(
                "Started generation of the repository index file for project");
        result.assertLogText("Repository index file was generated at");

        assertFilesPresent(rootDir.toFile(), "index.xml.gz");

    }
}
