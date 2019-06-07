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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.model.License;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.osgi.service.indexer.Constants;
import org.osgi.service.indexer.impl.KnownBundleAnalyzer;
import org.osgi.service.indexer.impl.RepoIndex;

import br.com.c8tech.tools.maven.osgi.lib.mojo.AbstractCustomPackagingMojo;
import br.com.c8tech.tools.maven.osgi.lib.mojo.CommonMojoConstants;
import br.com.c8tech.tools.maven.osgi.lib.mojo.beans.MavenArtifactSet;
import br.com.c8tech.tools.maven.osgi.lib.mojo.beans.P2ArtifactSets;
import br.com.c8tech.tools.maven.osgi.lib.mojo.filters.ValidTypeArtifactFilter;
import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Incremental.Configuration;

/**
 *
 * @author Cristiano Gavião
 *
 */
public abstract class AbstractOsgiRepositoryMojo
        extends AbstractCustomPackagingMojo {

    protected static final String CACHED_FILE_PATTERN_DEFAULT_FINALNAME = "%s-%c_%v.%e";

    public static final String DEFAULT_INCLUDE_FILEPATTERN = "**/*.jar";

    public static final String DEFAULT_REPOSITORY_FILE_NAME = "index.xml";

    private static final String[] DEFAULT_SUPPORTED_PACKAGING = {
            "osgi.repository" };

    public static final String DEFAULT_WORK_DIR_NAME = "repository";

    /**
     * The name used for the generated artifacts.
     * <p>
     * The final name will be calculated aggregating the classifier and the file
     * extension.
     */
    @Parameter(defaultValue = "${project.build.finalName}", required = true)
    private String artifactFileName;

    /**
     * The directory used to hold downloaded files.
     */
    @Parameter(property = "osgi.repository.cacheDirectory", required = true,
            defaultValue = "${project.build.directory}/"
                    + CommonMojoConstants.DEFAULT_CACHE_DIR_NAME)
    private File cacheDirectory;

    /**
     * The groupId to be used when installing or deploying artifacts coming from
     * a p2 repository.
     */
    @Parameter(required = true, defaultValue = "org.eclipse.platform",
            property = "osgi.repository.defaultGroupId")
    private String defaultGroupId;

    /**
     * Whether the plugin should deploy the downloaded p2 artifacts on the maven
     * remote repository in addition to the copy to the cache directory.
     * <p>
     * By default it will use the release remote repository.
     *
     * @see AbstractOsgiRepositoryMojo#installOnLocalRepository
     */
    @Parameter(required = true, defaultValue = "false")
    private boolean deployOnRemoteRepository;

    /**
     * When true, the plugin will copy all declared maven dependencies files
     * from maven local repository into the cache directory and use them in
     * order to create the index file and use a relative path pointing to them.
     * <p>
     * Otherwise, it won't copy files and will create the index file pointing to
     * artifacts into the local maven repository. <br>
     * Also, an absolute PATH will be used in order to locate the artifact
     * locally.
     * <p>
     * Note that this property has any effect in case of p2 dependencies that
     * will always copy downloaded artifacts to the cache directory.
     */
    @Parameter(required = true, defaultValue = "true")
    private boolean embedArtifacts;

    /**
     * A list of dependencies's artifactId that must be ignored by the plugin.
     * <p>
     * Example:
     *
     * <pre>
     * {@code
     *  <excludedArtifacts>
     *     <excludeArtifact>osgi.core</excludeArtifact>
     *     <excludeArtifact>org.osgi.annotation</excludeArtifact>
     *  </excludedArtifacts>}
     * </pre>
     */
    @Parameter(property = "osgi.repository.excludedArtifacts")
    private List<String> excludedArtifacts = new ArrayList<>();

    /**
     * A list of artifacts that will be added to the PojoSr classpath when
     * running the OSGi R5 index generator.
     * <p>
     * This is the way to filter which artifacts PojoSR will use and prevent it
     * to use any maven or eclipse classes. Example:
     *
     * <pre>
     * {@code
     * <extraBundles>
     *    <extraBundle>br.com.c8tech.tools.maven.osgi.lib.subsystem</extraBundles>
     *    <extraBundles>org.some.other.bundle</extraBundles>
     * </extraBundles>
     * }
     * </pre>
     *
     * Note: by default com.c8tech.tools.maven.lib.repoindex and
     * br.com.c8tech.tools.maven.osgi.lib.subsystem are being added to pojosr's
     * classpath.
     */
    @Parameter(property = "osgi.repository.extraBundles")
    private List<String> extraBundles = new ArrayList<>();

    /**
     * Defines a property file that contains extra know artifacts to be used by
     * the OSGi R5 index generator {@link KnownBundleAnalyzer} class.
     */
    @Parameter(property = "osgi.repository.extraKnownBundlesPropertiesFile")
    private File extraKnownBundlesPropertiesFile;

    /**
     * The plugin will force the use of absolute paths when creating R5 index
     * repository.
     * <p>
     * Even if the embed mode is on (where it uses relative paths by
     * default).<br>
     * 
     * It has any effect on p2 generation.
     */
    @Parameter(required = true, defaultValue = "false",
            property = "osgi.repository.forceAbsolutePath")
    private boolean forceAbsolutePath;

    /**
     * The plugin will use this property as the base URL when calculating the
     * location of the artifacts and other resources in the R5 index repository.
     * <p>
     * 
     * It has any effect on p2 generation.
     */
    @Parameter(required = false, property = "osgi.repository.forceBaseURL")
    private URL forceBaseURL = null;

    /**
     * This property will activate the generation of a R5 OSGi Index repository.
     */
    @Parameter(required = true, defaultValue = "true",
            property = "osgi.repository.generateP2")
    private boolean generateIndex;

    /**
     * This property will activate the generation of a p2 repository.
     * <p>
     * In order to the generated p2 includes the declared maven dependencies the
     * {@link #embedArtifacts} parameter must be set to true.
     */
    @Parameter(required = true, defaultValue = "false",
            property = "osgi.repository.generateP2")
    private boolean generateP2;

    /**
     * The plugin will use this property in order to set the increment value for
     * an OSGi R5 index repository.
     * <p>
     * The default increment number is derived from the current time
     * millisecond.<br>
     * It has any effect on p2 generation.
     */
    @Parameter(required = false, property = "osgi.repository.incrementOverride")
    private String incrementOverride = null;

    /**
     * The file name of the OSGi R5 index repository that will be generated.
     * <p>
     * When it is not informed a default one will be used to created the URL
     * using the {@link #rootDir} as its base directory.<br>
     * It has any effect on p2 generation.
     */
    @Parameter(property = "osgi.repository.indexFileName")
    private String indexFileName;

    /**
     * Whether the plugin should install the p2 artifacts that was downloaded to
     * the cache directory on the maven local repository.
     * <p>
     *
     * @see #deployOnRemoteRepository
     */
    @Parameter(required = true, defaultValue = "false")
    private boolean installOnLocalRepository;

    /**
     * Set this to <code>true</code> to skip the plugin execution.
     */
    @Parameter(defaultValue = "false", property = "osgi.repository.skip")
    @Incremental(configuration = Configuration.ignore)
    private boolean skip;

    /**
     * The set of maven based artifacts that will be downloaded and cached.
     * <p>
     * 
     * Those artifacts will override dependencies declared in the POM file, if
     * any.
     */
    @Parameter()
    private MavenArtifactSet mavenArtifactSet;

    @Parameter(required = true, defaultValue = "${settings.offline}")
    private boolean offline;

    /**
     * Whether the plugin should consider maven optional dependencies in order
     * to generate the repositories.
     */
    @Parameter(required = true, defaultValue = "false",
            property = "osgi.repository.optionalConsidered")
    private boolean optionalConsidered;

    /**
     * The set of p2 based artifacts that will be downloaded to the cache
     * directory.
     */
    @Parameter()
    private P2ArtifactSets p2ArtifactSets;

    /**
     * Currently Eclipse IDE can be installed using OOmph (Eclipse Installer).
     * <p>
     * OOmph will create a p2 pool directory when bundles and features will be
     * cache and share by all Eclipse instances.
     * <p>
     * Use this option to point to the local p2 pool created by OOmph in order
     * avoid re-download already existent local files and minimize the build
     * time.
     */
    @Parameter(property = "osgi.repository.p2LocalPool")
    private URL p2LocalPoolDirectory;

    @Parameter(required = true, property = "plugin", readonly = true)
    @Incremental(configuration = Configuration.ignore)
    // for Maven 3 only
    private PluginDescriptor pluginDescriptor;

    /**
     * Whether the plugin should format the generated repository index XML file.
     *
     */
    @Parameter(required = true, defaultValue = "true",
            property = "osgi.repository.pretty")
    private boolean pretty;

    /**
     * The name to be set inside the generated OSGi R5 index repository file.
     * <p>
     * ex: <code>My Repository</code>
     */
    @Parameter(defaultValue = "An OSGi Repository",
            property = "osgi.repository.repositoryName")
    private String repositoryName;

    private ValidTypeArtifactFilter validTypeArtifactFilter;

    /**
     * A custom template used by repository indexer plugin to interpret the URL
     * of resources that will be part of generated index repository.
     * <p>
     * See details in {@link Constants#URL_TEMPLATE}.
     */
    @Parameter()
    private String resourceUrlTemplate;

    /**
     * A list of scopes to be considered by the plugin when collecting maven
     * dependencies to be used in order to generate the OSGi repositories.
     * <p>
     * The default value is <b>compile</b>.
     * <p>
     * When needed, the user can use the empty value this way:
     *
     * <pre>
     * {@code <scopes><scope>empty</scope></scopes>}
     * </pre>
     */
    @Parameter(property = "osgi.repository.scopes")
    private Set<String> scopes = new HashSet<>();

    /**
     * Whether the plugin should consider maven transitive dependencies in order
     * to generate repositories.
     */
    @Parameter(required = true, defaultValue = "false",
            property = "osgi.repository.transitiveConsidered")
    private boolean transitiveConsidered;

    /**
     * A list of maven packaging types used to identify a bundle and narrow the
     * number of dependencies to be used in order to generate the OSGi
     * repositories.
     * <p>
     * Any directed or transitive dependencies (when allowed) must have its
     * packaging type contained in this list in order to be considered as valid
     * and be considered.
     * <p>
     * Example:
     *
     * <pre>
     * {@code
     *      <validBundleTypes>
     *        <validBundleType>jar</validBundleType>
     *        <validBundleType>bundle</validBundleType>
     *      <validBundleTypes>
     * }
     * </pre>
     *
     */
    @Parameter(property = "osgi.repository.validBundleTypes",
            defaultValue = "jar, bundle")
    private List<String> validBundleTypes = new ArrayList<>();

    /**
     * A list of maven packaging types used to identify a subsystem and narrow
     * the number of dependencies to be processed when generating the OSGi
     * repositories.
     * <p>
     * Any directed or transitive dependencies (when allowed) must have its
     * packaging types contained in this list in order to be considered a valid
     * dependency.
     * <p>
     * Example:
     *
     * <pre>
     * {@code <validSubsystemTypes>
     *          <validSubsystemType>esa<validSubsystemType>
     *          <validSubsystemTypes>subsystem<validSubsystemType>
     *        </validSubsystemTypes>}
     * </pre>
     *
     */
    @Parameter(property = "osgi.repository.validSubsystemTypes",
            defaultValue = CommonMojoConstants.OSGI_SUBSYSTEM_PACKAGING_COMPOSITE
                    + ","
                    + CommonMojoConstants.OSGI_SUBSYSTEM_PACKAGING_APPLICATION
                    + ","
                    + CommonMojoConstants.OSGI_SUBSYSTEM_PACKAGING_FEATURE)
    private List<String> validSubsystemTypes = new ArrayList<>();

    /**
     * Indicates whether the plugin should consider the projects opened in the
     * IDE for dependency resolution.
     */
    @Parameter(defaultValue = "false")
    private boolean workspaceResolutionAllowed;

    public AbstractOsgiRepositoryMojo(final MavenProject project) {
        this(project, false, getDefaultSupportedPackaging());
    }

    public AbstractOsgiRepositoryMojo(final MavenProject project,
            boolean aggregatorMojo) {
        this(project, aggregatorMojo, getDefaultSupportedPackaging());
    }

    public AbstractOsgiRepositoryMojo(final MavenProject project,
            boolean aggregatorMojo, final String... packagings) {
        super(project, aggregatorMojo, packagings);
    }

    protected static final String[] getDefaultSupportedPackaging() {
        return DEFAULT_SUPPORTED_PACKAGING;
    }

    public static MessageFormat getMsgChoiceArtifact() {
        return CommonMojoConstants.MSG_CHOICE_ARTIFACT;
    }

    public void addExcludeArtifact(String excludeArtifact) {
        excludedArtifacts.add(excludeArtifact);
    }

    public void addExtraBundle(String extraBundle) {
        this.extraBundles.add(extraBundle);
    }

    public void addScope(String scope) {
        this.scopes.add(scope);
    }

    public void addValidBundleType(String type) {
        this.validBundleTypes.add(type);
    }

    public void addValidSubsystemType(String type) {
        this.validSubsystemTypes.add(type);
    }

    protected Map<String, String> buildRepoindexConfigFromParameters(
            Path rootDir, Path bundlesDirPath, Path subsystemsDirPath,
            boolean compressedArg, boolean prettyArg) {
        Map<String, String> bindexConfig = new HashMap<>(11);

        bindexConfig.put(Constants.ROOT_DIR, rootDir.toString());

        bindexConfig.put(Constants.BUNDLES_COPY_DIR,
                bundlesDirPath != null ? bundlesDirPath.toString() : null);

        bindexConfig.put(Constants.SUBSYSTEMS_COPY_DIR,
                subsystemsDirPath != null ? subsystemsDirPath.toString()
                        : null);
        bindexConfig.put(Constants.INDEX_FILE_NAME, getIndexFileName());

        bindexConfig.put(Constants.COMPRESSED, Boolean.toString(compressedArg));

        bindexConfig.put(Constants.PRETTY, Boolean.toString(prettyArg));

        bindexConfig.put(Constants.FORCE_ABSOLUTE_PATH,
                Boolean.toString(isForceAbsolutePath()));

        if (forceBaseURL != null)
            bindexConfig.put(Constants.FORCE_BASE_URL, forceBaseURL.toString());

        bindexConfig.put(Constants.VERBOSE, Boolean.toString(isVerbose()));

        if (getRepositoryName() != null) {
            bindexConfig.put(Constants.REPOSITORY_NAME, getRepositoryName());
        }
        if (getResourceUrlTemplate() != null) {
            bindexConfig.put(Constants.URL_TEMPLATE, getResourceUrlTemplate());
        }
        if (incrementOverride != null) {
            bindexConfig.put(RepoIndex.REPOSITORY_INCREMENT_OVERRIDE,
                    incrementOverride);
        }
        if (!getProject().getLicenses().isEmpty()) {
            License license = getProject().getLicenses().get(0);
            bindexConfig.put(Constants.LICENSE_URL, license.getUrl());
        }
        return bindexConfig;
    }

    protected File calculateRepositoryArchiveName() {

        String name = getArtifactFileName()
                + (getClassifier() != null ? "-" + getClassifier() : "") + "."
                + CommonMojoConstants.OSGI_REPOSITORY_ARCHIVE_EXTENSION;

        return new File(getProject().getBuild().getDirectory(), name);
    }

    protected Path calculateTargetLocation() throws IOException {
        Path pluginTargetDir;
        if (isGenerateP2()) {
            pluginTargetDir = getCacheDirectory();
        } else {
            pluginTargetDir = getWorkDirectory();
        }
        Files.createDirectories(pluginTargetDir);
        return pluginTargetDir;
    }

    protected Path calculateTemporaryDirectory() {
        Path tempDir;
        if (getProject() != null) {
            tempDir = Paths.get(getProject().getBuild().getDirectory());
        } else {
            try {
                tempDir = Files.createTempDirectory("pojosr");
            } catch (IOException e) {
                getLog().warn(
                        "Could not create temp dir, tryng to use 'user.dir'",
                        e);
                tempDir = Paths.get(System.getProperty("user.dir"));
            }
        }
        return tempDir;
    }

    protected final String defaultExcludeFilePatterns() {
        return "**/*.properties;**/*.txt;**/*.xml;**/.meta;"
                + "**/.cache;**/.locks;**/*-javadoc*";
    }

    /**
     * Used to specify any action that must be executed when the mojo are being
     * skipped by maven reactor.
     *
     * @throws MojoExecutionException
     *                                    When the skipping process had any
     *                                    problem
     */
    protected void doBeforeSkipMojo() throws MojoExecutionException {
    }

    protected void executeExtraInitializationSteps()
            throws MojoExecutionException {
    }

    protected final String getArtifactFileName() {
        return artifactFileName;
    }

    protected final Path getCacheDirectory() {
        return cacheDirectory.toPath();
    }

    public ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }

    protected final String getDefaultGroupId() {
        return defaultGroupId;
    }

    protected final List<String> getExcludedArtifacts() {
        return excludedArtifacts;
    }

    protected final List<String> getExtraBundles() {
        return extraBundles;
    }

    protected final File getExtraKnownBundlesPropertiesFile() {
        return extraKnownBundlesPropertiesFile;
    }

    protected final URL getForceBaseURL() {
        return forceBaseURL;
    }

    protected final String getIncrementOverride() {
        return incrementOverride;
    }

    protected final String getIndexFileName() {
        if (indexFileName == null || indexFileName.isEmpty()) {

            indexFileName = DEFAULT_REPOSITORY_FILE_NAME;
        }
        return indexFileName;
    }

    protected final MavenArtifactSet getMavenArtifactSet() {
        return mavenArtifactSet;
    }

    protected final P2ArtifactSets getP2ArtifactSets() {
        if (p2ArtifactSets == null) {
            p2ArtifactSets = new P2ArtifactSets();
        }
        return p2ArtifactSets;
    }

    protected final URL getP2LocalPoolDirectory() {
        return p2LocalPoolDirectory;
    }

    protected final String getRepositoryName() {
        return repositoryName;
    }

    public ValidTypeArtifactFilter getRepositoryValidArtifactFilter() {

        if (validTypeArtifactFilter == null) {

            validTypeArtifactFilter = new ValidTypeArtifactFilter();
            validTypeArtifactFilter.addItems(validBundleTypes);
            validTypeArtifactFilter.addItems(validSubsystemTypes);
        }

        return this.validTypeArtifactFilter;
    }

    protected final String getResourceUrlTemplate() {
        return resourceUrlTemplate;
    }

    protected final Set<String> getScopes() {
        if (scopes.isEmpty()) {
            scopes.add("compile");
        }
        return scopes;
    }

    protected final List<String> getValidBundleTypes() {
        return validBundleTypes;
    }

    protected final List<String> getValidSubsystemTypes() {
        return validSubsystemTypes;
    }

    protected final String incrementOverride() {
        return incrementOverride;
    }

    protected final boolean isDeployOnRemoteRepository() {
        return deployOnRemoteRepository;
    }

    protected final boolean isEmbedArtifacts() {
        return embedArtifacts;
    }

    protected final boolean isForceAbsolutePath() {
        return forceAbsolutePath;
    }

    protected final boolean isGenerateIndex() {
        return generateIndex;
    }
    
    protected final boolean isGenerateP2() {
        return generateP2;
    }

    protected final boolean isInstallOnLocalRepository() {
        return installOnLocalRepository;
    }

    public boolean isOffline() {
        return offline;
    }

    protected final boolean isOptionalConsidered() {
        return optionalConsidered;
    }

    protected final boolean isPretty() {
        return pretty;
    }

    @Override
    protected boolean isSkip() {
        return skip;
    }

    protected final boolean isTransitiveConsidered() {
        return transitiveConsidered;
    }

    public boolean isWorkspaceResolutionAllowed() {
        return this.workspaceResolutionAllowed;
    }

    protected final File knownBundlesExtraFile() {
        return extraKnownBundlesPropertiesFile;
    }

    public void setCacheDirectory(File cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    public final void setDefaultGroupId(String pDefaultGroupId) {
        defaultGroupId = pDefaultGroupId;
    }

    public final void setExtraBundles(List<String> extraBundles) {
        for (String scope : extraBundles) {
            addExtraBundle(scope);
        }
    }

    public final void setForceAbsolutePath(boolean pForceAbsolutePath) {
        forceAbsolutePath = pForceAbsolutePath;
    }

    public final void setForceBaseURL(URL pForceBaseURL) {
        forceBaseURL = pForceBaseURL;
    }

    public final void setMavenArtifactSet(MavenArtifactSet pMavenArtifactSet) {
        mavenArtifactSet = pMavenArtifactSet;
    }

    public final void setP2ArtifactSets(P2ArtifactSets pP2ArtifactSets) {
        p2ArtifactSets = pP2ArtifactSets;
    }

    public final void setP2LocalPoolDirectory(URL pLocalPoolDirectory) {
        p2LocalPoolDirectory = pLocalPoolDirectory;
    }

    public final void setScopes(List<String> scopes) {
        for (String scope : scopes) {
            addScope(scope);
        }
    }

    public final void setValidBundleTypes(List<String> validTypes) {
        for (String validType : validTypes) {
            addValidBundleType(validType);
        }
    }

    public final void setValidSubsystemTypes(List<String> validTypes) {
        for (String validType : validTypes) {
            addValidSubsystemType(validType);
        }
    }

    public void setWorkspaceResolutionAllowed(
            boolean pAllowsWorkspaceResolution) {
        this.workspaceResolutionAllowed = pAllowsWorkspaceResolution;
    }

}
