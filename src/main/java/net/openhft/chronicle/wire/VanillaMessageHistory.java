/*
 * Copyright 2016 higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.wire;

import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesOut;
import net.openhft.chronicle.core.io.IORuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/*
 * Created by Peter Lawrey on 27/03/16.
 */
public class VanillaMessageHistory extends AbstractMarshallable implements MessageHistory {
    public static final int MESSAGE_HISTORY_LENGTH = 20;
    private static final ThreadLocal<MessageHistory> THREAD_LOCAL =
            ThreadLocal.withInitial((Supplier<MessageHistory>) () -> {
                @NotNull VanillaMessageHistory veh = new VanillaMessageHistory();
                veh.addSourceDetails(true);
                return veh;
            });

    private int sources;
    @NotNull
    private int[] sourceIdArray = new int[MESSAGE_HISTORY_LENGTH];
    @NotNull
    private long[] sourceIndexArray = new long[MESSAGE_HISTORY_LENGTH];
    private int timings;
    @NotNull
    private long[] timingsArray = new long[MESSAGE_HISTORY_LENGTH * 2];
    private boolean addSourceDetails = false;

    static MessageHistory getThreadLocal() {
        return THREAD_LOCAL.get();
    }

    static void setThreadLocal(MessageHistory md) {
        THREAD_LOCAL.set(md);
    }

    public void addSourceDetails(boolean addSourceDetails) {
        this.addSourceDetails = addSourceDetails;
    }

    @Override
    public void reset() {
        sources = timings = 0;
    }

    @Override
    public void reset(int sourceId, long sourceIndex) {
        sources = 1;
        sourceIdArray[0] = sourceId;
        sourceIndexArray[0] = sourceIndex;
        timings = 1;
        timingsArray[0] = System.nanoTime();
    }

    @Override
    public int lastSourceId() {
        return sources <= 0 ? -1 : sourceIdArray[sources - 1];
    }

    @Override
    public long lastSourceIndex() {
        return sources <= 0 ? -1 : sourceIndexArray[sources - 1];
    }

    @Override
    public int timings() {
        return timings;
    }

    @Override
    public long timing(int n) {
        return timingsArray[n];
    }

    @Override
    public int sources() {
        return sources;
    }

    @Override
    public int sourceId(int n) {
        return sourceIdArray[n];
    }

    @Override
    public long sourceIndex(int n) {
        return sourceIndexArray[n];
    }

    @Override
    public void readMarshallable(@NotNull WireIn wire) throws IORuntimeException {
        sources = 0;
        wire.read(() -> "sources").sequence(this, (t, in) -> {
            while (in.hasNextSequenceItem()) {
                t.addSource(in.int32(), in.int64());
            }
        });
        timings = 0;
        wire.read(() -> "timings").sequence(this, (t, in) -> {
            while (in.hasNextSequenceItem()) {
                t.addTiming(in.int64());
            }
        });
        if (addSourceDetails) {
            @Nullable Object o = wire.parent();
            if (o instanceof SourceContext) {
                @Nullable SourceContext dc = (SourceContext) o;
                addSource(dc.sourceId(), dc.index());
            }

            addTiming(System.nanoTime());
        }
    }

    @Override
    public void writeMarshallable(@NotNull WireOut wire) {
        wire.write("sources").sequence(this, (t, out) -> {
            for (int i = 0; i < t.sources; i++) {
                out.uint32(t.sourceIdArray[i]);
                out.int64_0x(t.sourceIndexArray[i]);
            }
        });
        wire.write("timings").sequence(this, (t, out) -> {
            for (int i = 0; i < t.timings; i++) {
                out.int64(t.timingsArray[i]);
            }
            out.int64(System.nanoTime());
        });
    }

    @Override
    public void readMarshallable(@NotNull BytesIn bytes) throws IORuntimeException {
        int sources = (int) bytes.readStopBit();
        this.sources = 0;
        for (int i = 0; i < sources; i++)
            addSource(bytes.readInt(), bytes.readLong());

        int timings = (int) bytes.readStopBit();
        this.timings = 0;
        for (int i = 0; i < timings; i++)
            addTiming(bytes.readLong());
    }

    @Override
    public void writeMarshallable(@NotNull BytesOut bytes) {
        bytes.writeStopBit(sources);
        for (int i = 0; i < sources; i++)
            bytes.writeInt(sourceIdArray[i]).writeLong(sourceIndexArray[i]);

        bytes.writeStopBit(timings);
        for (int i = 0; i < timings; i++)
            bytes.writeLong(timingsArray[i]);
    }

    public void addSource(int id, long index) {
        sourceIdArray[sources] = id;
        sourceIndexArray[sources++] = index;
    }

    public void addTiming(long l) {
        if (timings >= timingsArray.length) {
            throw new IllegalStateException("Have exceeded message history size: " + this.toString());
        }
        timingsArray[timings++] = l;
    }
}
