package com.cundong.apkpatch;

import com.cundong.cli.ApkPatchCli;

/** @deprecated Use {@link ApkPatchCli} with {@code patch <old> <new> <patch>}. */
@Deprecated
public final class ApkPatchDemo {

    private ApkPatchDemo() {
    }

    public static void main(String[] args) {
        String[] cliArgs = new String[args.length + 1];
        cliArgs[0] = "patch";
        System.arraycopy(args, 0, cliArgs, 1, args.length);
        ApkPatchCli.main(cliArgs);
    }
}
