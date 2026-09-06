package com.cundong.cli;

import com.cundong.utils.DiffUtils;
import com.cundong.utils.PatchUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Runs one native operation in an isolated JVM for {@link ApkPatchCli}. */
public final class NativeWorker {

    private static final String NATIVE_LIBRARY = "ApkPatchLibraryServer";

    private NativeWorker() {
    }

    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Native worker requires: <diff|patch> <old> <new> <patch>");
            System.exit(1);
        }

        try {
            loadNativeLibrary();
            int result;
            if ("diff".equals(args[0])) {
                result = DiffUtils.genDiff(args[1], args[2], args[3]);
            } else if ("patch".equals(args[0])) {
                result = PatchUtils.patch(args[1], args[2], args[3]);
            } else {
                System.err.println("Unknown native operation: " + args[0]);
                System.exit(1);
                return;
            }
            System.exit(result == 0 ? 0 : 1);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            System.err.println("Native worker failed: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Gradle run 时从 java.library.path 加载；JAR/安装分发中则把根目录的 native
     * 资源提取到进程私有临时目录后按绝对路径加载，使分发包可整体搬运。
     */
    private static void loadNativeLibrary() {
        String mappedName = System.mapLibraryName(NATIVE_LIBRARY);
        try (InputStream input = NativeWorker.class.getResourceAsStream("/" + mappedName)) {
            if (input == null) {
                System.loadLibrary(NATIVE_LIBRARY);
                return;
            }

            Path directory = Files.createTempDirectory("apk-patcher-native-");
            Path library = directory.resolve(mappedName);
            Files.copy(input, library);
            directory.toFile().deleteOnExit();
            library.toFile().deleteOnExit();
            System.load(library.toAbsolutePath().toString());
        } catch (IOException e) {
            UnsatisfiedLinkError error = new UnsatisfiedLinkError(
                    "Unable to extract bundled native library: " + e.getMessage());
            error.initCause(e);
            throw error;
        }
    }
}
