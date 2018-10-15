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
package br.com.c8tech.tools.maven.plugin.osgi.repository.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.EnumSet;

public class DirectoryUtil {

    private DirectoryUtil(){
        
    }
    /**
     * Copies a directory.
     * <p>
     * NOTE: This method is not thread-safe.
     * <p>
     * 
     * @param source
     *            the directory to copy from
     * @param target
     *            the directory to copy into
     * @throws IOException
     *             if an I/O error occurs
     */
    public static void copyDirectory(final Path source, final Path target)
            throws IOException {
        Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                Integer.MAX_VALUE, new FileVisitor<Path>() {

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir,
                            BasicFileAttributes sourceBasic)
                                    throws IOException {
                        Path targetDir = Files.createDirectories(
                                target.resolve(source.relativize(dir)));
                        AclFileAttributeView acl = Files.getFileAttributeView(
                                dir, AclFileAttributeView.class);
                        if (acl != null)
                            Files.getFileAttributeView(targetDir,
                                    AclFileAttributeView.class)
                                    .setAcl(acl.getAcl());
                        DosFileAttributeView dosAttrs = Files
                                .getFileAttributeView(dir,
                                        DosFileAttributeView.class);
                        if (dosAttrs != null) {
                            DosFileAttributes sourceDosAttrs = dosAttrs
                                    .readAttributes();
                            DosFileAttributeView targetDosAttrs = Files
                                    .getFileAttributeView(targetDir,
                                            DosFileAttributeView.class);
                            targetDosAttrs
                                    .setArchive(sourceDosAttrs.isArchive());
                            targetDosAttrs.setHidden(sourceDosAttrs.isHidden());
                            targetDosAttrs
                                    .setReadOnly(sourceDosAttrs.isReadOnly());
                            targetDosAttrs.setSystem(sourceDosAttrs.isSystem());
                        }
                        FileOwnerAttributeView ownerAttrs = Files
                                .getFileAttributeView(dir,
                                        FileOwnerAttributeView.class);
                        if (ownerAttrs != null) {
                            FileOwnerAttributeView targetOwner = Files
                                    .getFileAttributeView(targetDir,
                                            FileOwnerAttributeView.class);
                            targetOwner.setOwner(ownerAttrs.getOwner());
                        }
                        PosixFileAttributeView posixAttrs = Files
                                .getFileAttributeView(dir,
                                        PosixFileAttributeView.class);
                        if (posixAttrs != null) {
                            PosixFileAttributes sourcePosix = posixAttrs
                                    .readAttributes();
                            PosixFileAttributeView targetPosix = Files
                                    .getFileAttributeView(targetDir,
                                            PosixFileAttributeView.class);
                            targetPosix
                                    .setPermissions(sourcePosix.permissions());
                            targetPosix.setGroup(sourcePosix.group());
                        }
                        UserDefinedFileAttributeView userAttrs = Files
                                .getFileAttributeView(dir,
                                        UserDefinedFileAttributeView.class);
                        if (userAttrs != null) {
                            UserDefinedFileAttributeView targetUser = Files
                                    .getFileAttributeView(targetDir,
                                            UserDefinedFileAttributeView.class);
                            for (String key : userAttrs.list()) {
                                ByteBuffer buffer = ByteBuffer
                                        .allocate(userAttrs.size(key));
                                userAttrs.read(key, buffer);
                                buffer.flip();
                                targetUser.write(key, buffer);
                            }
                        }
                        // Must be done last, otherwise last-modified time may
                        // be
                        // wrong
                        BasicFileAttributeView targetBasic = Files
                                .getFileAttributeView(targetDir,
                                        BasicFileAttributeView.class);
                        targetBasic.setTimes(sourceBasic.lastModifiedTime(),
                                sourceBasic.lastAccessTime(),
                                sourceBasic.creationTime());
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file,
                            BasicFileAttributes attrs) throws IOException {
                        Files.copy(file,
                                target.resolve(source.relativize(file)),
                                StandardCopyOption.COPY_ATTRIBUTES);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file,
                            IOException e) throws IOException {
                        throw e;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir,
                            IOException e) throws IOException {
                        if (e != null)
                            throw e;
                        return FileVisitResult.CONTINUE;
                    }
                });
    }
}
