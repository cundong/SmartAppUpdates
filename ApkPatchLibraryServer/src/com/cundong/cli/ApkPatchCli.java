package com.cundong.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Command-line entry point for generating and applying BSDIFF40 patches. */
public final class ApkPatchCli {

    public static final int EXIT_OK = 0;
    public static final int EXIT_USAGE = 64;
    public static final int EXIT_INPUT = 66;
    public static final int EXIT_NATIVE = 70;
    public static final int EXIT_OUTPUT = 73;

    private ApkPatchCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 4) {
            printUsage(err);
            return EXIT_USAGE;
        }

        Command command = Command.parse(args[0]);
        if (command == null) {
            err.println("Unsupported command: " + args[0]);
            printUsage(err);
            return EXIT_USAGE;
        }

        Path temporaryOutput = null;
        try {
            Path oldFile = resolveInput("old", args[1]);
            Path secondInput = resolveInput(
                    command == Command.DIFF ? "new" : "patch",
                    args[command == Command.DIFF ? 2 : 3]);
            Path output = resolveOutput(args[command == Command.DIFF ? 3 : 2]);

            if (Files.isSameFile(oldFile, secondInput)) {
                throw new CliException(EXIT_INPUT, "Input paths must refer to different files");
            }
            if (output.equals(oldFile) || output.equals(secondInput)) {
                throw new CliException(EXIT_OUTPUT, "Output path must not overwrite an input file: " + output);
            }

            temporaryOutput = createTemporaryOutput(output);
            Path newFile = command == Command.DIFF ? secondInput : temporaryOutput;
            Path patchFile = command == Command.DIFF ? temporaryOutput : secondInput;

            long startedAt = System.nanoTime();
            int workerExit;
            try {
                workerExit = runNativeWorker(
                        command, oldFile, newFile, patchFile, temporaryOutput, err);
            } catch (IOException e) {
                err.println("Unable to start native worker: " + e.getMessage());
                return EXIT_NATIVE;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                err.println("Native worker was interrupted");
                return EXIT_NATIVE;
            }

            if (workerExit != 0) {
                err.println("Native " + command.value + " failed (worker exit " + workerExit + ")");
                return EXIT_NATIVE;
            }

            long outputBytes;
            try {
                if (!Files.isRegularFile(temporaryOutput)) {
                    err.println("Native " + command.value + " produced no regular output file");
                    return EXIT_NATIVE;
                }
                outputBytes = Files.size(temporaryOutput);
            } catch (IOException | SecurityException e) {
                err.println("Unable to validate native output: " + e.getMessage());
                return EXIT_NATIVE;
            }

            publishOutput(temporaryOutput, output);

            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
            out.println(command.value + " succeeded: " + output);
            out.println("elapsedMs=" + elapsedMillis + ", outputBytes=" + outputBytes);
            return EXIT_OK;
        } catch (CliException e) {
            err.println(e.getMessage());
            return e.exitCode;
        } catch (InvalidPathException e) {
            err.println("Invalid path: " + e.getInput());
            return EXIT_INPUT;
        } catch (IOException | SecurityException e) {
            err.println("I/O validation failed: " + e.getMessage());
            return EXIT_INPUT;
        } finally {
            deleteTemporaryOutput(temporaryOutput, err);
        }
    }

    private static Path resolveInput(String label, String rawPath) throws IOException, CliException {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            throw new CliException(EXIT_INPUT, label + " input path is empty");
        }

        Path input = Paths.get(rawPath).toRealPath();
        if (!Files.isRegularFile(input)) {
            throw new CliException(EXIT_INPUT, label + " input is not a regular file: " + input);
        }
        if (!Files.isReadable(input)) {
            throw new CliException(EXIT_INPUT, label + " input is not readable: " + input);
        }
        if (Files.size(input) == 0L) {
            throw new CliException(EXIT_INPUT, label + " input is empty: " + input);
        }
        return input;
    }

    private static Path resolveOutput(String rawPath) throws CliException {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            throw new CliException(EXIT_OUTPUT, "Output path is empty");
        }

        Path requested;
        try {
            requested = Paths.get(rawPath).toAbsolutePath().normalize();
        } catch (InvalidPathException | SecurityException e) {
            throw new CliException(EXIT_OUTPUT, "Invalid output path: " + rawPath);
        }
        Path fileName = requested.getFileName();
        Path parent = requested.getParent();
        if (fileName == null || parent == null) {
            throw new CliException(EXIT_OUTPUT, "Output must name a file: " + requested);
        }
        if (Files.exists(requested, LinkOption.NOFOLLOW_LINKS)) {
            throw new CliException(EXIT_OUTPUT, "Output already exists; refusing to overwrite: " + requested);
        }

        Path realParent;
        try {
            realParent = parent.toRealPath();
        } catch (IOException | SecurityException e) {
            throw new CliException(EXIT_OUTPUT,
                    "Output parent does not exist or is inaccessible: " + parent);
        }
        if (!Files.isDirectory(realParent)) {
            throw new CliException(EXIT_OUTPUT, "Output parent is not a directory: " + realParent);
        }
        if (!Files.isWritable(realParent)) {
            throw new CliException(EXIT_OUTPUT, "Output parent is not writable: " + realParent);
        }
        return realParent.resolve(fileName).normalize();
    }

    private static Path createTemporaryOutput(Path output) throws CliException {
        try {
            return Files.createTempFile(output.getParent(), ".apk-patcher-", ".tmp");
        } catch (IOException | SecurityException e) {
            throw new CliException(EXIT_OUTPUT,
                    "Cannot create temporary output next to " + output + " (" + e.getMessage() + ")");
        }
    }

    private static void publishOutput(Path temporaryOutput, Path output) throws CliException {
        try {
            /* 同目录 hard link 是原子发布：目标已存在时绝不覆盖，同时不会暴露半成品。 */
            Files.createLink(output, temporaryOutput);
        } catch (FileAlreadyExistsException e) {
            throw new CliException(EXIT_OUTPUT,
                    "Output appeared while native work was running; refusing to overwrite: " + output);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            throw new CliException(EXIT_OUTPUT,
                    "Cannot publish completed output: " + output + " (" + e.getMessage() + ")");
        }
    }

    private static int runNativeWorker(Command command, Path oldFile, Path newFile, Path patchFile,
            Path temporaryOutput, PrintStream err)
            throws IOException, InterruptedException {
        String javaExecutable = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String classPath = System.getProperty("java.class.path");
        String libraryPath = System.getProperty("java.library.path", "");

        List<String> processArgs = new ArrayList<>();
        processArgs.add(javaExecutable);
        processArgs.add("-Djava.library.path=" + libraryPath);
        processArgs.add("-cp");
        processArgs.add(classPath);
        processArgs.add(NativeWorker.class.getName());
        processArgs.add(command.value);
        processArgs.add(oldFile.toString());
        processArgs.add(newFile.toString());
        processArgs.add(patchFile.toString());

        Process process = new ProcessBuilder(processArgs)
                .inheritIO()
                .start();
        Thread shutdownHook = new Thread(() -> {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            deleteTemporaryOutput(temporaryOutput, err);
        }, "apk-patcher-worker-cleanup");
        boolean hookRegistered = false;
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            hookRegistered = true;
            return process.waitFor();
        } catch (InterruptedException e) {
            process.destroy();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            throw e;
        } finally {
            if (hookRegistered) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // JVM 已进入关闭流程，此时 shutdownHook 会负责回收。
                }
            }
        }
    }

    private static void deleteTemporaryOutput(Path output, PrintStream err) {
        if (output == null) {
            return;
        }
        try {
            Files.deleteIfExists(output);
        } catch (IOException | SecurityException e) {
            err.println("Warning: failed to remove temporary output " + output + ": " + e.getMessage());
        }
    }

    private static void printUsage(PrintStream stream) {
        stream.println("Usage:");
        stream.println("  apk-patcher diff <old> <new> <patch>");
        stream.println("  apk-patcher patch <old> <new> <patch>");
    }

    private enum Command {
        DIFF("diff"),
        PATCH("patch");

        private final String value;

        Command(String value) {
            this.value = value;
        }

        private static Command parse(String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.toLowerCase(Locale.ROOT);
            for (Command command : values()) {
                if (command.value.equals(normalized)) {
                    return command;
                }
            }
            return null;
        }
    }

    private static final class CliException extends Exception {
        private final int exitCode;

        private CliException(int exitCode, String message) {
            super(message);
            this.exitCode = exitCode;
        }
    }
}
