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
package com.c8tech.tools.maven.plugin.osgi.repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import com.c8tech.tools.maven.plugin.osgi.repository.utils.RepoIndexBridge;
import com.google.common.collect.Sets;

import br.com.c8tech.tools.maven.osgi.lib.mojo.CommonMojoConstants;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTracker;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTrackerManager;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTrackerManagerBuilder;
import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.Resource;
import io.takari.incrementalbuild.ResourceMetadata;
import io.takari.incrementalbuild.ResourceStatus;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.InputSet;

/**
 * This mojo will generate an R5 OSGi indexed repository using as source the
 * declared dependencies.
 * <p>
 * It was designed as an integrated part of the default lifecycle of the
 * <b>repoindex</b> packaging and should not be used alone.
 *
 * @author Cristiano Gavião
 *
 */
@Mojo(name = "generateIndexFromDependencies", threadSafe = true,
        requiresProject = true, inheritByDefault = true, aggregator = false,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresDependencyCollection = ResolutionScope.COMPILE)
public class MojoGenerateIndexFromDependencies
        extends AbstractOsgiRepositoryMojo {

    private final AggregatorBuildContext buildContext;

    private final BuildContext copyContext;

    @Inject
    public MojoGenerateIndexFromDependencies(MavenProject project,
            AggregatorBuildContext pBuildContext, BuildContext pCopyContext) {
        super(project);
        buildContext = pBuildContext;
        copyContext = pCopyContext;
    }

    public Path calculateIndexFilePath(boolean compressedArg,
            Path indexParentDir, String fileName) {

        return indexParentDir.resolve(compressedArg
                ? fileName
                        .concat(CommonMojoConstants.OSGI_REPO_COMPRESSED_XML_GZ)
                : fileName);
    }

    @Override
    public void executeMojo()
            throws MojoExecutionException, MojoFailureException {

        if (!isGenerateIndex()) {
            getLog().info(
                    "Skipping R5 OSGi index repository generation since it was not allowed.");
            return;
        }

        getLog().info(
                "Setting up generation of the OSGi repository index file for project "
                        + getProject().getArtifactId());

        ArtifactTrackerManager artifactTrackerManager = ArtifactTrackerManagerBuilder
                .newBuilder(getMavenSession(), getCacheDirectory())
                .withGroupingByTypeDirectory(true).withVerbose(isVerbose())
                .withPreviousCachingRequired(true).mavenSetup()
                .withDependenciesHelper(getDependenciesHelper())
                .withCachedFileNamePattern(
                        isGenerateP2() ? CACHED_FILE_PATTERN_DEFAULT_FINALNAME
                                : getCachedFileNamePattern())
                .withRepositorySystem(getRepositorySystem()).workspaceSetup()
                .withAssemblyUrlProtocolAllowed(isWorkspaceResolutionAllowed())
                .withPackOnTheFlyAllowed(isWorkspaceResolutionAllowed())
                .endWorkspaceSetup().mavenFiltering()
                .withArtifactFilter(getRepositoryValidArtifactFilter())
                .withOptional(isOptionalConsidered())
                .withTransitive(isTransitiveConsidered())
                .withScopes(getScopes())
                .withMavenArtifactSet(getMavenArtifactSet())
                .withExcludedDependencies(getExcludedArtifacts())
                .endMavenFiltering().endMavenSetup().p2Setup()
                .withDefaultGroupId(getDefaultGroupId())
                .withP2ArtifactSets(getP2ArtifactSets()).endP2Setup().build();

        if (!getP2ArtifactSets().getP2ArtifactSets().isEmpty()) {
            artifactTrackerManager
                    .resolveP2Artifacts(getP2LocalPoolDirectory());
        }

        artifactTrackerManager.resolveMavenArtifacts(getScopes());

        Set<ArtifactTracker> allArtifacts = artifactTrackerManager
                .getAllArtifactTrackers();

        if (allArtifacts.isEmpty()) {
            getLog().warn(
                    "There was any artifact set in order to generate the OSGi repository index for project "
                            + getProject().getArtifactId());
            return;
        }

        InputSet indexInputSet = buildContext.newInputSet();
        try {
            Set<File> toProcess = copyArtifacts(allArtifacts);

            if (isVerbose()) {
                getLog().info(
                        "Registering the artifacts into the OSGi Repository index file"
                                + " generation incremental build context.");
            }
            for (File fileToProcess : toProcess) {
                indexInputSet.addInput(fileToProcess);
            }
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failure occurred while creating incremental inputs", e);
        }

        final Path outputFile = calculateIndexFilePath(false,
                getWorkSubDirectory(DEFAULT_WORK_DIR_NAME), getIndexFileName());

        prepareForOsgiRepositoryIndexFileGeneration(indexInputSet, outputFile);

    }

    private Set<File> copyArtifacts(Set<ArtifactTracker> pAllArtifacts)
            throws MojoExecutionException {

        Path workspaceDir = getWorkSubDirectory(DEFAULT_WORK_DIR_NAME);
        try {
            Files.createDirectories(workspaceDir);
        } catch (IOException e1) {
            throw new MojoExecutionException(
                    "Failure occurred while creating bundles work directory.",
                    e1);
        }

        try {
            for (ArtifactTracker art : pAllArtifacts) {
                File fileToCopy = art.getCachedFilePath().toFile();
                copyArtifactToRepositoryDir(workspaceDir, getCacheDirectory(),
                        fileToCopy, art.isWorkspaceProject());
            }
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failure while copying artifacts to work directory.", e);
        }
        try (Stream<Path> ss2 = Files.walk(workspaceDir)) {
            return ss2.map(Path::toFile)
                    .filter(p -> !p.getName().endsWith(".xml") && p.isFile())
                    .collect(Collectors.toSet());

        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failure while selecting artifacts from work directory.",
                    e);
        }
    }

    private void copyArtifactToRepositoryDir(Path pWorkspaceDir, Path pCacheDir,
            final File pFileToCopy, boolean pWorkspaceProject)
            throws IOException {
        ResourceMetadata<File> resourceMetadata = copyContext
                .registerInput(pFileToCopy);
        if (resourceMetadata.getStatus() != ResourceStatus.UNMODIFIED
                || pWorkspaceProject) {
            Resource<File> meta = resourceMetadata.process();
            Path targetPath = pWorkspaceDir.resolve(
                    pCacheDir.relativize(pFileToCopy.toPath()).toString());
            Output<File> output = meta.associateOutput(targetPath.toFile());
            Files.createDirectories(targetPath.getParent());
            if (Files.copy(pFileToCopy.toPath(), output.newOutputStream()) > 0
                    && isVerbose()) {
                getLog().info("    Copied artifact file from '" + pFileToCopy
                        + "' to '" + targetPath + "'");
            }

        }
    }

    private void prepareForOsgiRepositoryIndexFileGeneration(
            final InputSet pIndexInputSet, final Path pOutputFile)
            throws MojoExecutionException {

        try {

            pIndexInputSet.aggregateIfNecessary(pOutputFile.toFile(),
                    (output, inputs) -> {
                        getLog().info(
                                "Started generation of the repository index file for project "
                                        + getProject().getArtifactId());
                        generateRepository(output, inputs);
                    });
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "An error occurred while generating an indexed repository.",
                    e);
        }
    }

    private void generateRepository(Output<File> output, Iterable<File> inputs)
            throws IOException {
        final Path rootDir;
        final Path pluginTargetDir;
        final Path subsystemTargetDir;

        try {
            rootDir = getWorkSubDirectory(DEFAULT_WORK_DIR_NAME);
            pluginTargetDir = rootDir
                    .resolve(CommonMojoConstants.OSGI_BUNDLES_DIRECTORY);
            subsystemTargetDir = rootDir
                    .resolve(CommonMojoConstants.OSGI_SUBSYSTEM_DIRECTORY);
        } catch (MojoExecutionException e1) {
            throw new IOException(e1);
        }

        Map<String, String> repoindexConfig = buildRepoindexConfigFromParameters(
                rootDir, pluginTargetDir, subsystemTargetDir, false,
                isPretty());
        RepoIndexBridge bindexWrapper = new RepoIndexBridge(getClassLoader(),
                knownBundlesExtraFile(), getExtraBundles(),
                calculateTemporaryDirectory().toString(), isVerbose());

        try {
            bindexWrapper.generateRepositoryIndex(Sets.newLinkedHashSet(inputs),
                    output.newOutputStream(), repoindexConfig);
            getLog().info(String.format(
                    "Repository index file was successfully generated at : %s",
                    output.getResource().getAbsolutePath()));

        } catch (Exception e) {
            throw new IOException(
                    "Repository Indexer was unable to generate the repository index file ("
                            + output.getResource().getAbsolutePath() + ").",
                    e);
        }

    }

}
