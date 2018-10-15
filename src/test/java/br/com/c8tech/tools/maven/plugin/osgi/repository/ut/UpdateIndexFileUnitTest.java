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

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.Test;

import com.c8tech.tools.maven.plugin.osgi.repository.utils.RepoIndexBridge;

public class UpdateIndexFileUnitTest {

    @Test
    public void testSearchPatternFormatting() throws IOException {
        Path dir = Files.createTempDirectory("temp");

        RepoIndexBridge bindexWrapper = new RepoIndexBridge(
                getClass().getClassLoader(), null, Arrays.asList(""),
                dir.toString(), true);
        String formatted = bindexWrapper
                .getArtifactSearchPattern("ant-launcher", "1.8.2-SNAPSHOT");
        assertThat(formatted).isEqualTo(
                "//repo:resource[repo:capability[repo:attribute[@name='osgi.identity' "
                        + "and contains(@value,'ant-launcher')] and repo:attribute[@name='version' and contains(@value,'1.8.2')]]]");
        String formatted2 = bindexWrapper.getArtifactSearchPattern("ant",
                "1.7.1");
        assertThat(formatted2).isEqualTo(
                "//repo:resource[repo:capability[repo:attribute[@name='osgi.identity' "
                        + "and contains(@value,'ant')] and repo:attribute[@name='version' and @value='1.7.1']]]");
    }

}
