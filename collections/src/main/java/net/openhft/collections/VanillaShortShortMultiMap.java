/*
 * Copyright 2013 Peter Lawrey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.collections;

import net.openhft.lang.Maths;
import net.openhft.lang.io.Bytes;
import net.openhft.lang.io.DirectStore;

/**
 * Supports a simple interface for int -> int[] off heap.
 */
class VanillaShortShortMultiMap implements IntIntMultiMap {
    private static final int ENTRY_SIZE = 4;
    private static final int ENTRY_SIZE_SHIFT = 2;

    private static final int UNSET_KEY = 0;
    private static final int HASH_INSTEAD_OF_UNSET_KEY = 0xFFFF;
    private static final int UNSET_VALUE = Integer.MIN_VALUE;

    private static final int UNSET_ENTRY = 0xFFFF;

    private static void checkKey(int key) {
        if ((key & ~0xFFFF) != 0)
            throw new IllegalArgumentException("Key out of range, was " + key);
    }

    private static void checkValue(int value) {
        if ((value & ~0xFFFF) != 0)
            throw new IllegalArgumentException("Value out of range, was " + value);
    }

    private final int capacity;
    private final int capacityMask;
    private final int capacityMask2;
    private final Bytes bytes;

    public VanillaShortShortMultiMap(int minCapacity) {
        if (minCapacity < 0 || minCapacity > (1 << 16))
            throw new IllegalArgumentException();
        capacity = Maths.nextPower2(minCapacity, 16);
        capacityMask = capacity - 1;
        capacityMask2 = (capacity - 1) * ENTRY_SIZE;
        bytes = DirectStore.allocateLazy(capacity * ENTRY_SIZE).bytes();
        clear();
    }

    public VanillaShortShortMultiMap(Bytes bytes) {
        capacity = (int) (bytes.capacity() / ENTRY_SIZE);
        assert capacity == Maths.nextPower2(capacity, 16);
        capacityMask = capacity - 1;
        capacityMask2 = (capacity - 1) * ENTRY_SIZE;
        this.bytes = bytes;
    }

    @Override
    public void put(int key, int value) {
        if (key == UNSET_KEY)
            key = HASH_INSTEAD_OF_UNSET_KEY;
        else checkKey(key);
        checkValue(value);
        int pos = (key & capacityMask) << ENTRY_SIZE_SHIFT;
        for (int i = 0; i <= capacityMask; i++) {
            int entry = bytes.readInt(pos);
            int hash2 = entry >>> 16;
            if (hash2 == UNSET_KEY) {
                bytes.writeInt(pos, ((key << 16) | value));
                return;
            }
            if (hash2 == key) {
                int value2 = entry & 0xFFFF;
                if (value2 == value)
                    return;
            }
            pos = (pos + ENTRY_SIZE) & capacityMask2;
        }
        throw new IllegalStateException(getClass().getSimpleName() + " is full");
    }

    @Override
    public boolean remove(int key, int value) {
        if (key == UNSET_KEY)
            key = HASH_INSTEAD_OF_UNSET_KEY;
        else checkKey(key);
        checkValue(value);
        int pos = (key & capacityMask) << ENTRY_SIZE_SHIFT;
        int posToRemove = -1;
        for (int i = 0; i <= capacityMask; i++) {
            int entry = bytes.readInt(pos);
//            int hash2 = bytes.readInt(pos + KEY);
            int hash2 = entry >>> 16;
            if (hash2 == key) {
//                int value2 = bytes.readInt(pos + VALUE);
                int value2 = entry & 0xFFFF;
                if (value2 == value) {
                    posToRemove = pos;
                    break;
                }
            } else if (hash2 == UNSET_KEY) {
                break;
            }
            pos = (pos + ENTRY_SIZE) & capacityMask2;
        }
        if (posToRemove < 0)
            return false;
        removePos(posToRemove);
        return true;
    }

    @Override
    public boolean replace(int key, int oldValue, int newValue) {
        if (key == UNSET_KEY)
            key = HASH_INSTEAD_OF_UNSET_KEY;
        else checkKey(key);
        checkValue(oldValue);
        checkValue(newValue);
        int pos = (key & capacityMask) << ENTRY_SIZE_SHIFT;
        for (int i = 0; i <= capacityMask; i++) {
            int entry = bytes.readInt(pos);
            int hash2 = entry >>> 16;
            if (hash2 == key) {
                int value2 = entry & 0xFFFF;
                if (value2 == oldValue) {
                    bytes.writeInt(pos, ((key << 16) | newValue));
                    return true;
                }
            } else if (hash2 == UNSET_KEY) {
                break;
            }
            pos = (pos + ENTRY_SIZE) & capacityMask2;
        }
        return false;
    }

    private void removePos(int posToRemove) {
        int posToShift = posToRemove;
        for (int i = 0; i <= capacityMask; i++) {
            posToShift = (posToShift + ENTRY_SIZE) & capacityMask2;
            int entryToShift = bytes.readInt(posToShift);
            int hash = entryToShift >>> 16;
            if (hash == UNSET_KEY)
                break;
            int insertPos = (hash & capacityMask) << ENTRY_SIZE_SHIFT;
            // see comment in VanillaIntIntMultiMap
            boolean cond1 = insertPos <= posToRemove;
            boolean cond2 = posToRemove <= posToShift;
            if ((cond1 && cond2) ||
                    // chain wrapped around capacity
                    (posToShift < insertPos && (cond1 || cond2))) {
                bytes.writeInt(posToRemove, entryToShift);
                posToRemove = posToShift;
            }
        }
        bytes.writeInt(posToRemove, UNSET_ENTRY);
    }

    /////////////////////
    // Stateful methods

    private int searchHash = -1;
    private int searchPos = -1;

    @Override
    public int startSearch(int key) {
        if (key == UNSET_KEY)
            key = HASH_INSTEAD_OF_UNSET_KEY;

        searchPos = (key & capacityMask) << ENTRY_SIZE_SHIFT;
        return searchHash = key;
    }

    @Override
    public int nextPos() {
        for (int i = 0; i < capacity; i++) {
            int entry = bytes.readInt(searchPos);
            int hash2 = entry >>> 16;
            if (hash2 == UNSET_KEY) {
                return UNSET_VALUE;
            }
            searchPos = (searchPos + ENTRY_SIZE) & capacityMask2;
            if (hash2 == searchHash) {
                return entry & 0xFFFF;
            }
        }
        // if return UNSET_VALUE, we have 2 cases in putAfterFailedSearch()
        throw new IllegalStateException(getClass().getSimpleName() + " is full");
    }

    @Override
    public void removePrevPos() {
        removePos((searchPos - ENTRY_SIZE) & capacityMask2);
    }

    @Override
    public void replacePrevPos(int newValue) {
        checkValue(newValue);
        int prevPos = (((int) searchPos - ENTRY_SIZE) & capacityMask2);
        // Don't need to overwrite searchHash, but we don't know our bytes
        // byte order, and can't determine offset of the value within entry.
        bytes.writeInt(prevPos, ((searchHash << 16) | newValue));
    }

    @Override
    public void putAfterFailedSearch(int value) {
        checkValue(value);
        bytes.writeInt(searchPos, ((searchHash << 16) | value));
    }

    public int getSearchHash() {
        return searchHash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{ ");
        for (int i = 0, pos = 0; i < capacity; i++, pos += ENTRY_SIZE) {
            int entry = bytes.readInt(pos);
            int key = entry >>> 16;
            int value = entry & 0xFFFF;
            if (key != UNSET_KEY)
                sb.append(key).append('=').append(value).append(", ");
        }
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
            return sb.append(" }").toString();
        }
        return "{ }";
    }

    @Override
    public void forEach(EntryConsumer action) {
        for (int i = 0, pos = 0; i < capacity; i++, pos += ENTRY_SIZE) {
            int entry = bytes.readInt(pos);
            int key = entry >>> 16;
            int value = entry & 0xFFFF;
            if (key != UNSET_KEY)
                action.accept(key, value);
        }
    }

    @Override
    public void clear() {
        for (int pos = 0; pos < bytes.capacity(); pos += ENTRY_SIZE) {
            bytes.writeInt(pos, UNSET_ENTRY);
        }
    }
}
