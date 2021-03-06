/*
 * Copyright 2014 Higher Frequency Trading
 * <p/>
 * http://www.higherfrequencytrading.com
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;

import static net.openhft.collections.Builder.getPersistenceFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test  VanillaSharedReplicatedHashMap where the Replicated is over a TCP Socket
 *
 * @author Rob Austin.
 */

public class TCPSocketReplicationTest3wayPutReturnsNull {


    private SharedHashMap<Integer, CharSequence> map1;
    private SharedHashMap<Integer, CharSequence> map2;
    private SharedHashMap<Integer, CharSequence> map3;

    static <T extends SharedHashMap<Integer, CharSequence>> T newTcpSocketShmIntString(
            final byte identifier,
            final int serverPort,
            final InetSocketAddress... InetSocketAddress) throws IOException {

        TcpReplicatorBuilder tcpReplicatorBuilder = new TcpReplicatorBuilder(serverPort,
                InetSocketAddress);

        tcpReplicatorBuilder.deletedModIteratorFileOnExit(true);

        return (T) new SharedHashMapBuilder()
                .entries(1000)
                .putReturnsNull(true)
                .identifier(identifier)
                .tcpReplicatorBuilder(tcpReplicatorBuilder)
                .create(getPersistenceFile(), Integer.class, CharSequence.class);
    }


    @Before
    public void setup() throws IOException {
        map1 = newTcpSocketShmIntString((byte) 1, 8076, new InetSocketAddress("localhost", 8077), new InetSocketAddress("localhost", 8078));
        map2 = newTcpSocketShmIntString((byte) 2, 8077, new InetSocketAddress("localhost", 8078));
        map3 = newTcpSocketShmIntString((byte) 3, 8078);
    }

    @After
    public void tearDown() throws InterruptedException {

        for (final Closeable closeable : new Closeable[]{map1, map2, map3}) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Test
    public void test3() throws IOException, InterruptedException {

        map3.put(5, "EXAMPLE-2");

        // allow time for the recompilation to resolve
        waitTillEqual(5000);

        assertEquals(map1, map2);
        assertEquals(map3, map2);
        assertTrue(!map1.isEmpty());

    }

    @Test
    public void test() throws IOException, InterruptedException {

        assertEquals(null, map1.put(1, "EXAMPLE-1"));
        assertEquals(null, map1.put(2, "EXAMPLE-2"));
        assertEquals(null, map1.put(2, "EXAMPLE-1"));

        assertEquals(null, map2.put(5, "EXAMPLE-2"));
        assertEquals(null, map2.put(6, "EXAMPLE-2"));

        map1.remove(2);
        map2.remove(3);
        map1.remove(3);
        map2.put(5, "EXAMPLE-2");

        // allow time for the recompilation to resolve
        waitTillEqual(5000);

        assertEquals(map1, map2);
        assertEquals(map3, map3);
        assertTrue(!map1.isEmpty());

    }


    @Test
    public void testPutIfAbsent() throws IOException, InterruptedException {

        assertEquals(null, map1.putIfAbsent(1, "EXAMPLE-1"));
        assertEquals(null, map1.putIfAbsent(1, "EXAMPLE-2"));
        assertEquals(null, map1.putIfAbsent(2, "EXAMPLE-2"));
        assertEquals(null, map1.putIfAbsent(3, "EXAMPLE-1"));

        assertEquals(null, map2.putIfAbsent(5, "EXAMPLE-2"));
        assertEquals(null, map2.putIfAbsent(6, "EXAMPLE-2"));

        map1.remove(2);
        map2.remove(3);
        map1.remove(3);
        map2.putIfAbsent(5, "EXAMPLE-2");

        // allow time for the recompilation to resolve
        waitTillEqual(5000);

        assertEquals(map1, map2);
        assertEquals(map3, map3);
        assertTrue(!map1.isEmpty());

    }

    private void waitTillEqual(final int timeOutMs) throws InterruptedException {
        int t = 0;
        for (; t < timeOutMs; t++) {
            if (map1.equals(map2) &&
                    map1.equals(map3))
                break;
            Thread.sleep(1);
        }

    }
}



