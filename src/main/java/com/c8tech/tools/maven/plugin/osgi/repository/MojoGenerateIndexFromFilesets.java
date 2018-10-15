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
package com.c8tech.tools.maven.plugin.osgi.repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import com.c8tech.tools.maven.plugin.osgi.repository.utils.RepoIndexBridge;
import com.google.common.collect.Sets;

import br.com.c8tech.tools.maven.osgi.lib.mojo.CommonMojoConstants;
import br.com.c8tech.tools.maven.osgi.lib.mojo.beans.FileSet;
import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.InputSet;

@Mojo(name = "generateIndexFromFilesets", threadSafe = false,
        aggregator = false, defaultPhase = LifecyclePhase.VERIFY,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresDependencyCollection = ResolutionScope.COMPILE,
        requiresProject = false, inheritByDefault = true)
public class MojoGenerateIndexFromFilesets extends AbstractOsgiRepositoryMojo {

    @Inject
    protected AggregatorBuildContext buildContext;

    /**
     * This property will indicate whether the plugin should to generate a
     * compressed file or not.
     * <p>
     * In case of R5 repositories, the result will depend on its combination
     * with <b>pretty</b> parameter, as stated in table below:
     *
     * <pre>
     * pretty   compressed         out-pretty     out-compressed
     *   null         null        Indent.NONE               true*
     *   null        false        Indent.NONE              false
     *   null         true        Indent.NONE               true
     *  false         null      Indent.PRETTY              false*
     *  false        false        Indent.NONE              false
     *  false         true        Indent.NONE               true
     *   true         null      Indent.PRETTY              false*
     *   true        false      Indent.PRETTY              false
     *   true         true      Indent.PRETTY               true
     *
     *   * = original behaviour, before compressed was introduced
     * </pre>
     */
    @Parameter(required = true, defaultValue = "true",
            property = "osgi.repository.compressed")
    private boolean compressed;

    /**
     * The required set of source files that should be processed.
     *
     * <pre>
     *  {@code
     * <fileSets>
     *   <fileSet>
     *      <directory>/home/path</directory>
     *       <includes>
     *          <include>** /*.jar</include>
     *       </includes>
     *       <excludes>
     *         <exclude>** /*.txt</exclude>
     *       </excludes>
     * </fileSets>}<br>
     * or the shortest version:
     * {@code
     * <fileSets>/home/path:** /*.jar:** /*.txt</fileSets>
     * }
     * </pre>
     */
    @Parameter(required = true, property = "osgi.repository.filesets")
    private List<FileSet> fileSets = new ArrayList<>();

    /**
     * The directory where the plugin will get the artifacts that will be
     * indexed.
     * <p>
     * When this directory is below the directory where the index file will be
     * generated then the indexer tool will use a relative path for the
     * resource, otherwise an absolute path will be used.
     *
     * @see #targetDir
     */
    @Parameter(required = true, property = "osgi.repository.rootDir")
    private File rootDir;

    @Inject
    public MojoGenerateIndexFromFilesets(final MavenProject project) {
        super(project, false, new String[0]);
    }

    public void addFileSet(FileSet pFileSet) {
        fileSets.add(pFileSet);
    }

    public Path calculateIndexFilePath(boolean compressedArg,
            Path indexParentDir, String fileName) {

        return indexParentDir.resolve(compressedArg
                ? fileName
                        .concat(CommonMojoConstants.OSGI_REPO_COMPRESSED_XML_GZ)
                : fileName);
    }

    protected Path calculateRootDirPath() throws IOException {
        if (rootDir == null) {
            throw new IllegalArgumentException(
                    "The Root Directory must be a valid existent directory !");
        }
        if (!rootDir.exists()) {
            Files.createDirectories(rootDir.toPath());
        }
        if (rootDir.isDirectory()) {
            return rootDir.toPath();
        }
        return rootDir.getParentFile().toPath();
    }

    @Override
    protected void executeExtraInitializationSteps()
            throws MojoExecutionException {
        // do nothing
    }

    @Override
    protected void executeMojo()
            throws MojoExecutionException, MojoFailureException {

        try {
            getLog().info(
                    "Started generation of the repository index file for project "
                            + getProject().getArtifactId());
            Set<File> filesToIndex;
            final Path rootDirPath = calculateRootDirPath();
            InputSet inputSet = buildContext.newInputSet();
            filesToIndex = getDirectoryHelper().findFiles(fileSets);
            if (filesToIndex.isEmpty()) {
                getLog().warn(
                        "No files was found using the provided fileSets parameter:"
                                + fileSets);
                return;
            }

            for (File file : filesToIndex) {
                if (isVerbose()) {
                    getLog().info("Adding file '" + file.getName() + "'");
                }
                inputSet.addInput(file);
            }
            Path outputFile = calculateIndexFilePath(isCompressed(),
                    rootDirPath, getIndexFileName());
            inputSet.aggregateIfNecessary(outputFile.toFile(), (output,
                    inputs) -> generateRepository(rootDirPath, output, inputs));
            getLog().info("Repository index file was generated at :"
                    + outputFile.toAbsolutePath());
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "An error occurred while generating an indexed repository.",
                    e);
        }

    }

    private void generateRepository(Path rootDirPath, Output<File> output,
            Iterable<File> inputs) throws IOException {

        Map<String, String> repoindexConfig = buildRepoindexConfigFromParameters(
                rootDirPath, null, null, isCompressed(), isPretty());
        RepoIndexBridge bindexWrapper = new RepoIndexBridge(getClassLoader(),
                knownBundlesExtraFile(), getExtraBundles(),
                calculateTemporaryDirectory().toString(), isVerbose());
        try {
            bindexWrapper.generateRepositoryIndex(Sets.newLinkedHashSet(inputs),
                    output.newOutputStream(), repoindexConfig);
        } catch (Exception e) {
            throw new IOException(e);
        }

    }

    public List<FileSet> getFileSets() {
        return fileSets;
    }

    public boolean isCompressed() {
        return compressed;
    }

    public void setFileSets(List<FileSet> pFileSets) {
        for (FileSet fileSet : pFileSets) {
            addFileSet(fileSet);
        }
    }

}
