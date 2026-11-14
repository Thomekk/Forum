// Copyright 2014 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package go;

/** Minimal Go-mobile Universe binding required by YggChat's generated JNI library. */
public final class Universe {
    static {
        System.loadLibrary("gojni");
        _init();
    }

    private Universe() { }
    private static native void _init();
    public static void touch() { }

    public static final class proxyerror extends Exception implements error, Seq.GoObject {
        private final int refnum;

        public proxyerror(int refnum) {
            this.refnum = refnum;
            Seq.trackGoRef(refnum, this);
        }

        @Override public native String error();
        @Override public String getMessage() { return error(); }
        @Override public final int incRefnum() {
            Seq.incGoRef(refnum, this);
            return refnum;
        }
    }
}

interface error {
    String error();
}
