/*
 * Copyright (c) 2016-2019 Chronicle Software Ltd
 */

package net.openhft.chronicle.wire;

class CountingDocumentContext extends WrappedDocumentContext {
    public int count;
    public boolean local;

    public CountingDocumentContext() {
        super(null);
    }

    @Override
    public void close() {
        if (count == 0)
            super.rollbackOnClose();
        super.close();
        dc( null);
        count = 0;
    }
}
