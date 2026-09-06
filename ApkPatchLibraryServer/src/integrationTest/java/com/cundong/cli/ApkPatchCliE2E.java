package com.cundong.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Deterministic end-to-end check executed by the Gradle {@code e2eTest} task. */
public final class ApkPatchCliE2E {

    private ApkPatchCliE2E() {
    }

    public static void main(String[] args) throws Exception {
        Path workDir = Files.createTempDirectory("apk-patcher-e2e-");
        Path oldFile = workDir.resolve("old.bin");
        Path expectedFile = workDir.resolve("expected.bin");
        Path patchFile = workDir.resolve("update.patch");
        Path actualFile = workDir.resolve("actual.bin");
        Path corruptPatch = workDir.resolve("corrupt.patch");
        Path failedOutput = workDir.resolve("failed.bin");

        try {
            byte[] oldBytes = fixture(32 * 1024, 0x13579BDF);
            byte[] expectedBytes = Arrays.copyOf(oldBytes, oldBytes.length + 4096);
            byte[] replacement = fixture(6144, 0x2468ACE0);
            System.arraycopy(replacement, 0, expectedBytes, 8192, replacement.length);
            System.arraycopy(replacement, 2048, expectedBytes, oldBytes.length, 4096);

            Files.write(oldFile, oldBytes);
            Files.write(expectedFile, expectedBytes);

            ProcessResult diff = runCli("diff", oldFile, expectedFile, patchFile);
            requireExit("diff", diff, ApkPatchCli.EXIT_OK);
            byte[] patchBytes = Files.readAllBytes(patchFile);
            byte[] magic = "BSDIFF40".getBytes(StandardCharsets.US_ASCII);
            if (patchBytes.length < 32
                    || !Arrays.equals(magic, Arrays.copyOf(patchBytes, magic.length))) {
                throw new AssertionError("diff did not produce a valid BSDIFF40 header");
            }

            ProcessResult patch = runCli("patch", oldFile, actualFile, patchFile);
            requireExit("patch", patch, ApkPatchCli.EXIT_OK);

            byte[] actualBytes = Files.readAllBytes(actualFile);
            if (!Arrays.equals(expectedBytes, actualBytes)) {
                throw new AssertionError("reconstructed bytes differ from expected bytes");
            }

            ProcessResult overwrite = runCli("patch", oldFile, actualFile, patchFile);
            requireExit("overwrite protection", overwrite, ApkPatchCli.EXIT_OUTPUT);

            ProcessResult missingParent = runCli(
                    "patch", oldFile, workDir.resolve("missing/actual.bin"), patchFile);
            requireExit("missing output parent", missingParent, ApkPatchCli.EXIT_OUTPUT);

            Files.write(corruptPatch, "not-a-bsdiff-patch".getBytes(StandardCharsets.US_ASCII));
            ProcessResult corrupt = runCli("patch", oldFile, failedOutput, corruptPatch);
            requireExit("corrupt patch", corrupt, ApkPatchCli.EXIT_NATIVE);
            if (Files.exists(failedOutput)) {
                throw new AssertionError("native failure exposed a final output file");
            }
            try (java.util.stream.Stream<Path> paths = Files.list(workDir)) {
                if (paths.anyMatch(path -> path.getFileName().toString().startsWith(".apk-patcher-"))) {
                    throw new AssertionError("native failure left a temporary output file");
                }
            }

            System.out.println("e2e PASS: bytes=" + actualBytes.length + ", sha256=" + sha256(actualBytes));
        } finally {
            Files.deleteIfExists(failedOutput);
            Files.deleteIfExists(corruptPatch);
            Files.deleteIfExists(actualFile);
            Files.deleteIfExists(patchFile);
            Files.deleteIfExists(expectedFile);
            Files.deleteIfExists(oldFile);
            Files.deleteIfExists(workDir);
        }
    }

    private static ProcessResult runCli(String command, Path oldFile, Path newFile, Path patchFile)
            throws IOException, InterruptedException {
        String launcher = System.getProperty("apkPatcher.launcher");
        String javaExecutable = System.getProperty("apkPatcher.javaExecutable");
        String classPath = System.getProperty("apkPatcher.classpath");
        String libraryPath = System.getProperty("apkPatcher.libraryPath");
        if (launcher == null && (javaExecutable == null || classPath == null || libraryPath == null)) {
            throw new IllegalStateException("e2eTest system properties are not configured");
        }

        List<String> arguments = new ArrayList<>();
        if (launcher != null) {
            arguments.add(launcher);
        } else {
            arguments.add(javaExecutable);
            arguments.add("-Djava.library.path=" + libraryPath);
            arguments.add("-cp");
            arguments.add(classPath);
            arguments.add(ApkPatchCli.class.getName());
        }
        arguments.add(command);
        arguments.add(oldFile.toString());
        arguments.add(newFile.toString());
        arguments.add(patchFile.toString());

        Process process = new ProcessBuilder(arguments)
                .redirectErrorStream(true)
                .start();
        String output;
        try (InputStream input = process.getInputStream()) {
            output = readUtf8(input);
        }
        return new ProcessResult(process.waitFor(), output);
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void requireExit(String operation, ProcessResult result, int expected) {
        if (result.exitCode != expected) {
            throw new AssertionError(operation + " exit=" + result.exitCode
                    + ", expected=" + expected + "\n" + result.output);
        }
    }

    private static byte[] fixture(int size, int seed) {
        byte[] bytes = new byte[size];
        int state = seed;
        for (int i = 0; i < bytes.length; i++) {
            state = state * 1664525 + 1013904223;
            bytes[i] = (byte) (state >>> 24);
        }
        return bytes;
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format("%02x", value & 0xff));
        }
        return hex.toString();
    }

    private static final class ProcessResult {
        private final int exitCode;
        private final String output;

        private ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
