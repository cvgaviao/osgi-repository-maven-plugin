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

import static io.takari.maven.testing.TestMavenRuntime.newParameter;
import static io.takari.maven.testing.TestResources.assertFilesNotPresent;
import static io.takari.maven.testing.TestResources.assertFilesPresent;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import br.com.c8tech.tools.maven.plugin.osgi.repository.utils.DirectoryUtil;
import br.com.c8tech.tools.maven.plugin.osgi.repository.utils.XmlUtils;
import io.takari.maven.testing.TestMavenRuntime;
import io.takari.maven.testing.TestResources;

public class GenerateIndexFromFilesetsUnitTest {

    @Rule
    public final TestResources resources = new TestResources();

    @Rule
    public final TestMavenRuntime maven = new TestMavenRuntime();

     @Test
    public void testIndexFileWithRelativePath() throws Exception {

        URI expectedFile = getClass().getResource("/jars/aBundle.jar").toURI();
        Assert.assertNotNull(expectedFile);
        Path sourceDir = Paths.get(expectedFile).getParent();
        MavenProject project = maven
                .readMavenProject(resources.getBasedir("ut-project--normal"));
        Path targetDir = project.getBasedir().toPath();
        Path rootDir = targetDir.resolve("repository");
        Path jarsDir = rootDir.resolve("jars");
        DirectoryUtil.copyDirectory(sourceDir, jarsDir);

        maven.executeMojo(project, "generateIndexFromFilesets",
                newParameter("fileSets",
                        jarsDir.toString()
                                + ":**/*.jar:**/01*.jar;**/aTra*;**/aNon*"),
                newParameter("compressed", "false"),
                newParameter("pretty", "true"),
                newParameter("rootDir", rootDir.toString()),
                newParameter("indexFileName", "repository.xml"),
                newParameter("incrementOverride", "1"));

        assertFilesPresent(rootDir.toFile(), "repository.xml");

        Path outputFile = rootDir.resolve("repository.xml");
        Path expected = Paths.get(getClass()
                .getResource("/xmls/index_from_folder_relative.xml").toURI());
        XmlUtils.assertXMLEqual(expected, outputFile);
    }
     @Test
     public void testEmptyFileset() throws Exception {

         MavenProject project = maven
                 .readMavenProject(resources.getBasedir("ut-project--normal"));
         Path targetDir = project.getBasedir().toPath();
         Path rootDir = targetDir.resolve("repository");

         maven.executeMojo(project, "generateIndexFromFilesets",
                 newParameter("compressed", "false"),
                 newParameter("pretty", "true"),
                 newParameter("rootDir", rootDir.toString()),
                 newParameter("indexFileName", "repository.xml"),
                 newParameter("incrementOverride", "1"));

         assertFilesNotPresent(rootDir.toFile(), "repository.xml");
     }

    @Test
    public void testIndexFileWithAbsolutePath() throws Exception {

        File basedir = resources.getBasedir("ut-project--normal");
        MavenProject project = maven.readMavenProject(basedir);
        URI expectedFile = getClass().getResource("/jars/aBundle.jar").toURI();
        Assert.assertNotNull(expectedFile);
        Path bundleSourceDir = Paths.get(expectedFile).getParent();
        URI expectedSS = getClass()
                .getResource("/subsystems/aCompositeSubsystem.esa").toURI();
        Assert.assertNotNull(expectedSS);
        Path ssSourceDir = Paths.get(expectedSS).getParent();
        Path rootDir = basedir.toPath()
                .resolve("target/repository");

        maven.executeMojo(project, "generateIndexFromFilesets",
                newParameter("fileSets",
                        bundleSourceDir.toString() + ":"
                                + "**/*.jar"
                                + ":**/01*.jar;**/aTra*;**/aNon*" + ", " + ssSourceDir
                                + ":**/*.esa"),
                newParameter("compressed", "false"),
                newParameter("pretty", "true"),
                newParameter("rootDir", rootDir.toString()),
                newParameter("indexFileName", "repository.xml"),
                newParameter("forceAbsolutePath", "true"),
                newParameter("incrementOverride", "1"));

        assertFilesPresent(rootDir.toFile(), "repository.xml");

        Path outputFile = rootDir.resolve("repository.xml");
        Path expected = Paths.get(getClass()
                .getResource("/xmls/index_from_folder_absolute.xml").toURI());
        Map<String, String> filters = new HashMap<>(1);
        filters.put("jars-dir", bundleSourceDir.toString());
        filters.put("subsystems-dir", ssSourceDir.toString());
        XmlUtils.assertXMLEqual(expected, outputFile, filters);
    }

    @Test (expected=IllegalArgumentException.class)
    public void testNullRootDir() throws Exception {
        File basedir = resources.getBasedir("ut-project--normal");
        MavenProject project = maven.readMavenProject(basedir);
        maven.executeMojo(project, "generateIndexFromFilesets");

    }

}
