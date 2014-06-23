package net.openhft.collections;

import net.openhft.lang.io.NativeBytes;

import java.util.Iterator;

/**
 * Created by ruedi on 23.06.14.
 */
public interface ByteEntryIterator<K,V> extends Iterator<NativeBytes>, IntIntMultiMap.EntryConsumer {

    public K getCurrentKey();

    public V getCurrentValue();

}
