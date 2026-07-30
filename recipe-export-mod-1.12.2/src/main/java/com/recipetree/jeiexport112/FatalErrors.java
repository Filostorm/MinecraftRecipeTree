package com.recipetree.jeiexport112;

final class FatalErrors {
    private FatalErrors() {
    }

    static void rethrowIfFatal(Throwable throwable) {
        if (throwable instanceof ThreadDeath) {
            throw (ThreadDeath) throwable;
        }
        if (throwable instanceof VirtualMachineError) {
            throw (VirtualMachineError) throwable;
        }
        if (throwable instanceof LinkageError) {
            throw (LinkageError) throwable;
        }
    }

    static boolean isFatal(Throwable throwable) {
        return throwable instanceof ThreadDeath || throwable instanceof VirtualMachineError ||
                throwable instanceof LinkageError;
    }
}
