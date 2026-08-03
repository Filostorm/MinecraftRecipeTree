package com.recipetree.neiexport1710;

final class FatalErrors {
    private FatalErrors() {
    }

    static boolean isFatal(Throwable error) {
        return error instanceof VirtualMachineError
                || error instanceof ThreadDeath
                || error instanceof LinkageError;
    }

    static void rethrowIfFatal(Throwable error) {
        if (error instanceof VirtualMachineError) {
            throw (VirtualMachineError) error;
        }
        if (error instanceof ThreadDeath) {
            throw (ThreadDeath) error;
        }
        if (error instanceof LinkageError) {
            throw (LinkageError) error;
        }
    }
}
