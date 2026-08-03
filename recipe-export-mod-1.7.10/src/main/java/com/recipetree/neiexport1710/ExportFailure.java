package com.recipetree.neiexport1710;

import java.io.IOException;

final class ExportFailure extends IOException {
    final String code;

    ExportFailure(String code, String message) {
        super(code + ": " + message);
        this.code = code;
    }

    ExportFailure(String code, String message, Throwable cause) {
        super(code + ": " + message, cause);
        this.code = code;
    }
}
