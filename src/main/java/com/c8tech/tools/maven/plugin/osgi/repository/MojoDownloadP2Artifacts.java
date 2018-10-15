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
import java.io.Writer;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.apache.maven.shared.transfer.artifact.deploy.ArtifactDeployer;
import org.apache.maven.shared.transfer.artifact.deploy.ArtifactDeployerException;
import org.apache.maven.shared.transfer.artifact.install.ArtifactInstallerException;
import org.apache.maven.shared.transfer.project.NoFileAssignedException;
import org.apache.maven.shared.transfer.project.install.ProjectInstaller;
import org.apache.maven.shared.transfer.project.install.ProjectInstallerRequest;
import org.apache.maven.shared.transfer.repository.RepositoryManager;
import org.apache.maven.shared.utils.WriterFactory;
import org.apache.maven.shared.utils.io.IOUtil;

import br.com.c8tech.tools.maven.osgi.lib.mojo.CommonMojoConstants;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTracker;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTrackerManager;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTrackerManagerBuilder;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.BuildContextWithUrl;
import io.takari.incrementalbuild.ResourceMetadata;
import io.takari.incrementalbuild.ResourceStatus;

/**
 * This mojo will download all artifacts stored in a p2 repository and declared
 * in the configuration tag <i>artifactConfigSets</i>.
 * <p>
 * By default the declared p2 artifacts will be saved in the cache directory,
 * but optionally can be installed in maven's local and/or remote repositories.
 * <p>
 * It was designed to be used stand alone, before the execution of another
 * project's build that would need the downloaded artifacts, such as the ones
 * that uses these packaging types: <b>osgi.repository</b>,
 * <b>osgi.subsystem</b>, <b>osgi.dp</b> , <b>osgi.ipzip</b>.
 * <p>
 *
 *
 * @author Cristiano Gavião
 *
 */
@Mojo(name = "downloadP2Artifacts", threadSafe = true,
        defaultPhase = LifecyclePhase.PROCESS_RESOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresDependencyCollection = ResolutionScope.COMPILE,
        requiresProject = true, inheritByDefault = true, aggregator = false)
public class MojoDownloadP2Artifacts extends AbstractOsgiRepositoryMojo {

    @Inject
    private BuildContextWithUrl copyBuildContext;

    @Inject
    private ArtifactDeployer deployer;

    /**
     * The path for a specific local repository directory.
     * <p>
     * By default the plugin will use the local repository configured in maven
     * global/local settings.
     */
    @Parameter
    private File localRepositoryPath;

    @Component
    protected RepositoryManager repositoryManager;

    /**
     * Used for creating the project to which the artifacts to install will be
     * attached.
     */
    @Component
    private ProjectBuilder projectBuilder;

    /**
     * Used to install the project created.
     */
    @Component
    private ProjectInstaller installer;

    /**
     * Map that contains the repository layouts.
     */
    @Component(role = ArtifactRepositoryLayout.class)
    private Map<String, ArtifactRepositoryLayout> repositoryLayouts;

    @Inject
    public MojoDownloadP2Artifacts(MavenProject project) {
        super(project);
    }

    private int downloadAndCopyNormalizedP2ArtifactFilesToCacheDirectory( // NOSONAR
            BuildContextWithUrl pBuildContextWithUrl,
            Set<ArtifactTracker> pRegisteredArtifactsToCopy)
            throws IOException, MojoExecutionException {
        int count = 0;
        for (ArtifactTracker artifactTracker : pRegisteredArtifactsToCopy) {

            ResourceMetadata<?> resourceMetadata = pBuildContextWithUrl
                    .registerInput(new URL(artifactTracker.getDownloadUrl()),
                            getCacheDirectory());
            if (resourceMetadata.getStatus() != ResourceStatus.UNMODIFIED) {
                if (isVerbose()) {
                    getLog().info("   Downloading artifact: "
                            + artifactTracker.getArtifactId());
                }
                URL sourceURL = null;
                if (resourceMetadata.getResource() instanceof URL) {
                    sourceURL = (URL) resourceMetadata.getResource();
                } else
                    if (resourceMetadata.getResource() instanceof File) {
                        sourceURL = ((File) resourceMetadata.getResource())
                                .toURI().toURL();
                    }
                resourceMetadata.process().associateOutput(getDirectoryHelper()
                        .copyResourceToDirectory(sourceURL, artifactTracker
                                .getCachedFilePath().getParent()));
                artifactTracker.setCached();
                if (isVerbose()) {
                    getLog().info("   Copied p2 artifact file from '"
                            + sourceURL + " to "
                            + artifactTracker.getCachedFilePath());
                }

                Map<String, String> jarManifestHeaders = artifactTracker
                        .getTypeHandler().extractManifestHeadersFromArchive(
                                artifactTracker.getCachedFilePath().toFile());
                if (artifactTracker.getTypeHandler()
                        .isArtifactManifestValid(jarManifestHeaders)) {
                    artifactTracker.getManifestHeaders()
                            .putAll(jarManifestHeaders);
                } else {
                    continue;
                }

                if (isInstallOnLocalRepository()) {
                    getLog().info(
                            "Starting installing artifacts into maven local repository");

                    installP2ArtifactIntoMavenLocalRepository(artifactTracker);
                }

                if (isDeployOnRemoteRepository()) {
                    getLog().info(
                            "Starting deploying artifacts to remote repository");
                    deployP2ArtifactIntoMavenRemoteReleaseRepository(
                            artifactTracker);
                }

                count++;
            } else {
                if (isVerbose()) {
                    getLog().info("   Bypassing downloading of artifact: "
                            + artifactTracker.getArtifactId());
                }
            }

        }
        return count;
    }

    /**
     * Copy files from p2 repository to cache directory.
     *
     * @param pArtifactTrackerManager
     *                                      The artifact tracker manager object.
     * @param pCopyP2BuildContextForURL
     *                                      The building context.
     * @throws MojoExecutionException
     *                                    When any IO related error have
     *                                    occurred.
     */
    protected void downloadAndCopyP2ArtifactsToCache(
            ArtifactTrackerManager pArtifactTrackerManager,
            BuildContextWithUrl pCopyP2BuildContextForURL)
            throws MojoExecutionException {

        Set<ArtifactTracker> artifacts = pArtifactTrackerManager
                .getP2ArtifactTrackers();
        if (artifacts.isEmpty()) {
            getLog().warn(
                    "Skipping downloading artifacts from p2 repositories for project "
                            + getProject().getArtifactId()
                            + " since there are any artifact declared in pom.");
            return;
        }

        getLog().info(
                "Starting downloading and caching artifacts from p2 repositories");
        try {
            int count = downloadAndCopyNormalizedP2ArtifactFilesToCacheDirectory(
                    pCopyP2BuildContextForURL, artifacts);
            getLog().info(
                    "Finished copying of "
                            + CommonMojoConstants.MSG_CHOICE_ARTIFACT
                                    .format(new Object[] { count })
                            + " from p2 repositories.");
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failure while copying the artifacts to cache directory",
                    e);
        }
    }

    private void deployP2ArtifactIntoMavenRemoteReleaseRepository(
            ArtifactTracker pArtifactTracker) throws MojoExecutionException {
        try {

            File artFile = pArtifactTracker.getCachedFilePath().toFile();
            File generatedPomFile = generatePomFile(pArtifactTracker);

            Artifact artifactMaven = pArtifactTracker.toArtifact();
            artifactMaven.setFile(artFile);

            // attach POM
            ProjectArtifactMetadata pomMetadata = new ProjectArtifactMetadata(
                    artifactMaven, generatedPomFile);
            artifactMaven.addMetadata(pomMetadata);

            ProjectBuildingRequest request = new DefaultProjectBuildingRequest();
            request.setRemoteRepositories(getRemoteRepositories());
            request.setProject(getProject());

            deployer.deploy(request, Arrays.asList(artifactMaven));
        } catch (ArtifactDeployerException e) {
            throw new MojoExecutionException(
                    "Failure while deploying an artifact into remote release repository",
                    e);
        }
    }

    @Override
    protected void executeMojo()
            throws MojoExecutionException, MojoFailureException {

        getLog().info("Setting up downloading of p2 artifacts for project "
                + getProject().getArtifactId());

        ArtifactTrackerManager artifactTrackerManager = ArtifactTrackerManagerBuilder
                .newBuilder(getMavenSession(), getCacheDirectory())
                .withGroupingByTypeDirectory(true).withOfflineMode(true)
                .withVerbose(isVerbose()).withPreviousCachingRequired(false)
                .p2Setup().withDefaultGroupId(getDefaultGroupId())
                .withP2ArtifactSets(getP2ArtifactSets()).endP2Setup().build();

        int count = 0;
        if (!getP2ArtifactSets().getP2ArtifactSets().isEmpty()) {
            count = artifactTrackerManager
                    .resolveP2Artifacts(getP2LocalPoolDirectory());
        }
        if (count > 0) {
            downloadAndCopyP2ArtifactsToCache(artifactTrackerManager,
                    copyBuildContext);
        } else {
            getLog().info(
                    "No artifact needs to be downloaded from a p2 repository for project "
                            + getProject().getArtifactId());

        }
    }

    private void installP2ArtifactIntoMavenLocalRepository(
            ArtifactTracker artifactTracker) throws MojoExecutionException {

        ProjectBuildingRequest buildingRequest = null;

        if (localRepositoryPath != null) {
            if (!localRepositoryPath.exists()) {
                localRepositoryPath.mkdirs();
            }
            buildingRequest = repositoryManager.setLocalRepositoryBasedir(
                    getMavenSession().getProjectBuildingRequest(),
                    localRepositoryPath);
            getLog().debug("localRepoPath: " + repositoryManager
                    .getLocalRepositoryBasedir(buildingRequest));

        } else {
            File defaultRepo = new File(
                    getMavenSession().getLocalRepository().getBasedir());
            buildingRequest = repositoryManager.setLocalRepositoryBasedir(
                    getMavenSession().getProjectBuildingRequest(), defaultRepo);
        }

        try {

            File artFile = artifactTracker.getCachedFilePath().toFile();
            File generatedPomFile = generatePomFile(artifactTracker);

            Artifact artifactMaven = artifactTracker.toArtifact();
            artifactMaven.setFile(artFile);

            // attach POM
            ProjectArtifactMetadata pomMetadata = new ProjectArtifactMetadata(
                    artifactMaven, generatedPomFile);
            artifactMaven.addMetadata(pomMetadata);

            buildingRequest.setProcessPlugins(false);

            MavenProject project = projectBuilder
                    .build(generatedPomFile, buildingRequest).getProject();
            project.setArtifact(artifactMaven);

            ProjectInstallerRequest projectInstallerRequest = new ProjectInstallerRequest()
                    .setProject(project);

            installer.install(buildingRequest, projectInstallerRequest);

        } catch (ProjectBuildingException | IOException
                | ArtifactInstallerException | NoFileAssignedException e) {
            throw new MojoExecutionException(
                    "Failure while installing an artifact into local repository",
                    e);
        }
    }

    /**
     * Generates a (temporary) POM file from the plugin configuration. It's the
     * responsibility of the caller to delete the generated file when no longer
     * needed.
     *
     * @return The path to the generated POM file, never <code>null</code>.
     * @throws MojoExecutionException
     *                                    If the POM file could not be
     *                                    generated.
     */
    private File generatePomFile(ArtifactTracker pArtifactTracker)
            throws MojoExecutionException {
        Model model = new Model();

        model.setModelVersion("4.0.0");

        model.setGroupId(pArtifactTracker.getGroupId());
        model.setArtifactId(pArtifactTracker.getArtifactId());
        model.setVersion(pArtifactTracker.getVersion());
        model.setPackaging(pArtifactTracker.getType());

        Writer writer = null;
        try {
            model.setDescription(pArtifactTracker.getManifestHeaders()
                    .get(CommonMojoConstants.OSGI_BUNDLE_HEADER_DESCRIPTION));
            model.setName(pArtifactTracker.getManifestHeaders()
                    .get(CommonMojoConstants.OSGI_BUNDLE_HEADER_NAME));
            File pomFile = File.createTempFile("c8tech-artifact-p2", ".pom");

            writer = WriterFactory.newXmlWriter(pomFile);
            new MavenXpp3Writer().write(writer, model);

            return pomFile;
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Error writing temporary POM file: " + e.getMessage(), e);
        } finally {
            IOUtil.close(writer);
        }
    }

}
